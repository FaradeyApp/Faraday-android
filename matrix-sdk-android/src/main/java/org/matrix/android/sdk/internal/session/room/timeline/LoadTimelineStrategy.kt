/*
 * Copyright (c) 2021 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.android.sdk.internal.session.room.timeline

import io.realm.OrderedCollectionChangeSet
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmResults
import io.realm.kotlin.createObject
import io.realm.kotlin.executeTransactionAwait
import kotlinx.coroutines.CompletableDeferred
import org.matrix.android.sdk.api.MatrixCoroutineDispatchers
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.failure.MatrixError
import org.matrix.android.sdk.api.session.room.send.SendState
import org.matrix.android.sdk.api.session.room.sender.SenderInfo
import org.matrix.android.sdk.api.session.room.timeline.Timeline
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.TimelineSettings
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.internal.database.helper.addIfNecessary
import org.matrix.android.sdk.internal.database.mapper.TimelineEventMapper
import org.matrix.android.sdk.internal.database.model.ChunkEntity
import org.matrix.android.sdk.internal.database.model.ChunkEntityFields
import org.matrix.android.sdk.internal.database.model.RoomEntity
import org.matrix.android.sdk.internal.database.model.TimelineEventEntity
import org.matrix.android.sdk.internal.database.model.TimelineEventEntityFields
import org.matrix.android.sdk.internal.database.model.deleteAndClearThreadEvents
import org.matrix.android.sdk.internal.database.query.findAllIncludingEvents
import org.matrix.android.sdk.internal.database.query.findLastForwardChunkOfThread
import org.matrix.android.sdk.internal.database.query.where
import org.matrix.android.sdk.internal.session.room.relation.threads.FetchThreadTimelineTask
import org.matrix.android.sdk.internal.session.room.send.LocalEchoEventFactory
import org.matrix.android.sdk.internal.session.room.state.StateEventDataSource
import org.matrix.android.sdk.internal.session.room.timeline.decorator.TimelineEventDecorator
import org.matrix.android.sdk.internal.session.room.timeline.decorator.TimelineEventDecoratorChain
import org.matrix.android.sdk.internal.session.room.timeline.decorator.UiEchoDecorator
import org.matrix.android.sdk.internal.session.sync.handler.room.ThreadsAwarenessHandler
import org.matrix.android.sdk.internal.util.time.Clock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * This class is responsible for keeping an instance of chunkEntity and timelineChunk according to the strategy.
 * There is 2 different mode: Live and Permalink.
 * In Live, we will query for the live chunk (isLastForward = true).
 * In Permalink, we will query for the chunk including the eventId we are looking for.
 * Once we got a ChunkEntity we wrap it with TimelineChunk class so we dispatch any methods for loading data.
 */

// [No longer necessary] Whether to search for timeline loops, and fix them.
const val ENABLE_TIMELINE_LOOP_SPLITTING = false
// [No longer necessary] Whether to search for stuck timelines due to empty self-linking chunks.
const val ENABLE_TIMELINE_EMPTY_CHUNK_CLEANUP = false
// Performance consideration for huge timelines, when having ENABLE_TIMELINE_LOOP_SPLITTING or ENABLE_TIMELINE_EMPTY_CHUNK_CLEANUP set to true
const val MAX_CHUNK_REPAIR_CHECK_COUNT = 100

internal class LoadTimelineStrategy constructor(
        private val roomId: String,
        private val timelineId: String,
        private val mode: Mode,
        private val dependencies: Dependencies,
        clock: Clock,
) {

    sealed interface Mode {
        object Live : Mode
        data class Permalink(val originEventId: String) : Mode
        data class Thread(val rootThreadEventId: String) : Mode

        fun originEventId(): String? {
            return if (this is Permalink) {
                originEventId
            } else {
                null
            }
        }

//        fun getRootThreadEventId(): String? {
//            return if (this is Thread) {
//                rootThreadEventId
//            } else {
//                null
//            }
//        }
    }

    data class Dependencies(
            val timelineSettings: TimelineSettings,
            val realm: AtomicReference<Realm>,
            val eventDecryptor: TimelineEventDecryptor,
            val paginationTask: PaginationTask,
            val fetchThreadTimelineTask: FetchThreadTimelineTask,
            val fetchTokenAndPaginateTask: FetchTokenAndPaginateTask,
            val getContextOfEventTask: GetContextOfEventTask,
            val timelineInput: TimelineInput,
            val timelineEventMapper: TimelineEventMapper,
            val threadsAwarenessHandler: ThreadsAwarenessHandler,
            val lightweightSettingsStorage: LightweightSettingsStorage,
            val onEventsUpdated: (Boolean) -> Unit,
            val onEventsDeleted: () -> Unit,
            val onLimitedTimeline: () -> Unit,
            val onLastForwardDeleted: () -> Unit,
            val onNewTimelineEvents: (List<String>) -> Unit,
            val stateEventDataSource: StateEventDataSource,
            val matrixCoroutineDispatchers: MatrixCoroutineDispatchers,
            val localEchoEventFactory: LocalEchoEventFactory
    )

    private var getContextLatch: CompletableDeferred<Unit>? = null
    private var chunkEntity: RealmResults<ChunkEntity>? = null
    private var timelineChunk: TimelineChunk? = null
    private var sendingEventCount: Int = 0
    private var lastForwardChunkEntity: RealmResults<ChunkEntity>? = null

    private val chunkEntityListener = OrderedRealmCollectionChangeListener { _: RealmResults<ChunkEntity>, changeSet: OrderedCollectionChangeSet ->
        // Can be call either when you open a permalink on an unknown event
        // or when there is a gap in the timeline.
        val shouldRebuildChunk = changeSet.insertions.isNotEmpty()
        if (shouldRebuildChunk) {
            timelineChunk?.close(closeNext = true, closePrev = true)
            timelineChunk = chunkEntity?.createTimelineChunk()
            // If we are waiting for a result of get context, post completion
            getContextLatch?.complete(Unit)
            // If we have a gap, just tell the timeline about it.
            if (timelineChunk?.hasReachedLastForward().orFalse()) {
                dependencies.onLimitedTimeline()
            }
        }
    }

    private val lastForwardChunkEntityListener = OrderedRealmCollectionChangeListener { _: RealmResults<ChunkEntity>, changeSet: OrderedCollectionChangeSet ->
        // When the last forward chunk changes and we had previously reached that, load forward again to avoid a stuck timeline
        val shouldRebuildChunk = changeSet.deletions.isNotEmpty()
        if (shouldRebuildChunk) {
            dependencies.onLastForwardDeleted()
        }
    }

    private val uiEchoManagerListener = object : UIEchoManager.Listener {
        override fun rebuildEvent(eventId: String, builder: (TimelineEvent) -> TimelineEvent?): Boolean {
            return timelineChunk?.rebuildEvent(eventId, builder, searchInNext = true, searchInPrev = true).orFalse()
        }
    }

    private val timelineInputListener = object : TimelineInput.Listener {

        override fun onLocalEchoCreated(roomId: String, timelineEvent: TimelineEvent) {
            if (roomId != this@LoadTimelineStrategy.roomId) {
                return
            }
            if (uiEchoManager.onLocalEchoCreated(timelineEvent)) {
                dependencies.onNewTimelineEvents(listOf(timelineEvent.eventId))
                dependencies.onEventsUpdated(false)
            }
        }

        override fun onLocalEchoUpdated(roomId: String, eventId: String, sendState: SendState) {
            if (roomId != this@LoadTimelineStrategy.roomId) {
                return
            }
            if (uiEchoManager.onSendStateUpdated(eventId, sendState)) {
                dependencies.onEventsUpdated(false)
            }
        }

        override fun onNewTimelineEvents(roomId: String, eventIds: List<String>) {
            if (roomId == this@LoadTimelineStrategy.roomId && hasReachedLastForward()) {
                dependencies.onNewTimelineEvents(eventIds)
            }
        }
    }

    private val uiEchoManager = UIEchoManager(uiEchoManagerListener, clock)
    private val sendingEventsDataSource: SendingEventsDataSource = RealmSendingEventsDataSource(
            roomId = roomId,
            realm = dependencies.realm,
            uiEchoManager = uiEchoManager,
            timelineEventMapper = dependencies.timelineEventMapper,
            onEventsUpdated = dependencies.onEventsUpdated
    )

    private val liveRoomStateListener = LiveRoomStateListener(
            roomId,
            dependencies.stateEventDataSource,
            dependencies.matrixCoroutineDispatchers.main
    )

    suspend fun onStart() {
        dependencies.eventDecryptor.start()
        dependencies.timelineInput.listeners.add(timelineInputListener)
        val realm = dependencies.realm.get()
        sendingEventsDataSource.start()
        chunkEntity = getChunkEntity(realm).also {
            it.addChangeListener(chunkEntityListener)
            timelineChunk = it.createTimelineChunk()
        }
        lastForwardChunkEntity = getLastForwardChunkEntity(realm).also {
            it.addChangeListener(lastForwardChunkEntityListener)
        }

        if (dependencies.timelineSettings.useLiveSenderInfo) {
            liveRoomStateListener.start()
        }
    }

    suspend fun onStop() {
        dependencies.eventDecryptor.destroy()
        dependencies.timelineInput.listeners.remove(timelineInputListener)
        chunkEntity?.removeChangeListener(chunkEntityListener)
        lastForwardChunkEntity?.removeChangeListener(lastForwardChunkEntityListener)
        sendingEventsDataSource.stop()
        timelineChunk?.close(closeNext = true, closePrev = true)
        getContextLatch?.cancel()
        chunkEntity = null
        timelineChunk = null
        if (mode is Mode.Thread) {
            clearThreadChunkEntity(dependencies.realm.get(), mode.rootThreadEventId)
        }
        lastForwardChunkEntity = null
        if (dependencies.timelineSettings.useLiveSenderInfo) {
            liveRoomStateListener.stop()
        }
    }

    suspend fun loadMore(count: Int, direction: Timeline.Direction, fetchOnServerIfNeeded: Boolean = true): LoadMoreResult {
        if (mode is Mode.Permalink && timelineChunk == null) {
            val params = GetContextOfEventTask.Params(roomId, mode.originEventId)
            try {
                getContextLatch = CompletableDeferred()
                dependencies.getContextOfEventTask.execute(params)
                // waits for the query to be fulfilled
                getContextLatch?.await()
                getContextLatch = null
            } catch (failure: Throwable) {
                if (failure is Failure.ServerError && failure.error.code in listOf(MatrixError.M_NOT_FOUND, MatrixError.M_FORBIDDEN, MatrixError.M_UNKNOWN)) {
                    // This failure is likely permanent, so handle in DefaultTimeline to restart without eventId
                    throw failure
                }
                return LoadMoreResult.FAILURE
            }
        }
        if (mode is Mode.Thread) {
            return timelineChunk?.loadMoreThread(count) ?: LoadMoreResult.FAILURE
        }
        return timelineChunk?.loadMore(count, direction, fetchOnServerIfNeeded) ?: LoadMoreResult.FAILURE
    }

    fun getBuiltEventIndex(eventId: String): Int? {
        return timelineChunk?.getBuiltEventIndex(eventId, searchInNext = true, searchInPrev = true)?.let { it + sendingEventCount }
    }

    fun getBuiltEvent(eventId: String): TimelineEvent? {
        return timelineChunk?.getBuiltEvent(eventId, searchInNext = true, searchInPrev = true)
    }

    fun buildSnapshot(): List<TimelineEvent> {
        val events = buildSendingEvents() + timelineChunk?.builtItems(includesNext = true, includesPrev = true).orEmpty()
        return if (dependencies.timelineSettings.useLiveSenderInfo) {
            events.map(this::applyLiveRoomState)
        } else {
            events
        }
    }

    fun senderWithLiveRoomState(senderInfo: SenderInfo): SenderInfo {
        return if (dependencies.timelineSettings.useLiveSenderInfo) {
            val updatedState = liveRoomStateListener.getLiveState(senderInfo.userId) ?: return senderInfo
            senderInfo.copy(avatarUrl = updatedState.avatarUrl, displayName = updatedState.displayName)
        } else {
            senderInfo
        }
    }

    private fun applyLiveRoomState(event: TimelineEvent): TimelineEvent {
        val updatedState = liveRoomStateListener.getLiveState(event.senderInfo.userId)
        return if (updatedState != null) {
            val updatedSenderInfo = event.senderInfo.copy(avatarUrl = updatedState.avatarUrl, displayName = updatedState.displayName)
            event.copy(senderInfo = updatedSenderInfo)
        } else {
            event
        }
    }

    private fun buildSendingEvents(): List<TimelineEvent> {
        return if (hasReachedLastForward() || mode is Mode.Thread) {
            sendingEventsDataSource.buildSendingEvents()
        } else {
            emptyList()
        }.also {
            sendingEventCount = it.size
        }
    }

    private suspend fun getChunkEntity(realm: Realm): RealmResults<ChunkEntity> {
        return when (mode) {
            is Mode.Live -> {
                ChunkEntity.where(realm, roomId)
                        .equalTo(ChunkEntityFields.IS_LAST_FORWARD, true)
                        .findAll()
            }
            is Mode.Permalink -> {
                ChunkEntity.findAllIncludingEvents(realm, roomId, listOf(mode.originEventId))
            }
            is Mode.Thread -> {
                recreateThreadChunkEntity(realm, mode.rootThreadEventId)
                ChunkEntity.where(realm, roomId)
                        .equalTo(ChunkEntityFields.ROOT_THREAD_EVENT_ID, mode.rootThreadEventId)
                        .equalTo(ChunkEntityFields.IS_LAST_FORWARD_THREAD, true)
                        .findAll()
            }
        }
    }

    /**
     * Clear any existing thread chunk entity and create a new one, with the
     * rootThreadEventId included.
     */
    private suspend fun recreateThreadChunkEntity(realm: Realm, rootThreadEventId: String) {
        realm.executeTransactionAwait {
            // Lets delete the chunk and start a new one
            ChunkEntity.findLastForwardChunkOfThread(it, roomId, rootThreadEventId)?.deleteAndClearThreadEvents()?.let {
                Timber.i("###THREADS LoadTimelineStrategy [onStart] thread chunk cleared..")
            }
            val threadChunk = it.createObject<ChunkEntity>().apply {
                Timber.i("###THREADS LoadTimelineStrategy [onStart] Created new thread chunk with rootThreadEventId: $rootThreadEventId")
                this.rootThreadEventId = rootThreadEventId
                this.isLastForwardThread = true
            }
            if (threadChunk.isValid) {
                RoomEntity.where(it, roomId).findFirst()?.addIfNecessary(threadChunk)
            }
        }
    }

    /**
     * Clear any existing thread chunk.
     */
    private suspend fun clearThreadChunkEntity(realm: Realm, rootThreadEventId: String) {
        realm.executeTransactionAwait {
            ChunkEntity.findLastForwardChunkOfThread(it, roomId, rootThreadEventId)?.deleteAndClearThreadEvents()?.let {
                Timber.i("###THREADS LoadTimelineStrategy [onStop] thread chunk cleared..")
            }
        }
    }

    private fun getLastForwardChunkEntity(realm: Realm): RealmResults<ChunkEntity> {
        return ChunkEntity.where(realm, roomId)
                .equalTo(ChunkEntityFields.IS_LAST_FORWARD, true)
                .findAll()
    }

    private fun hasReachedLastForward(): Boolean {
        return timelineChunk?.hasReachedLastForward().orFalse()
    }

    fun hasFullyLoadedForward(): Boolean {
        return timelineChunk?.hasLoadedAllEventsForwards().orFalse()
    }

    private fun RealmResults<ChunkEntity>.createTimelineChunk(): TimelineChunk? {
        fun createTimelineEventDecorator(): TimelineEventDecorator {
            val decorators = listOf(
                    UiEchoDecorator(uiEchoManager),
                    /* // SC: this is upstream reply fallback stuff
                    UpdatedReplyDecorator(
                            realm = dependencies.realm,
                            roomId = roomId,
                            localEchoEventFactory = dependencies.localEchoEventFactory,
                            timelineEventMapper = dependencies.timelineEventMapper
                    )
                    */
            )
            return TimelineEventDecoratorChain(decorators)
        }

        return firstOrNull()?.let {
            if (ENABLE_TIMELINE_EMPTY_CHUNK_CLEANUP) {
                // Before creating timeline chunks, make sure there are no empty chunks linking themselves, causing a stuck timeline
                it.cleanupSelfLinkingChunks()
            }
            if (ENABLE_TIMELINE_LOOP_SPLITTING) {
                // Before creating timeline chunks, make sure that the ChunkEntities do not form a loop
                it.fixChunkLoops()
            }
            return TimelineChunk(
                    chunkEntity = it,
                    timelineSettings = dependencies.timelineSettings,
                    roomId = roomId,
                    timelineId = timelineId,
                    fetchThreadTimelineTask = dependencies.fetchThreadTimelineTask,
                    eventDecryptor = dependencies.eventDecryptor,
                    paginationTask = dependencies.paginationTask,
                    fetchTokenAndPaginateTask = dependencies.fetchTokenAndPaginateTask,
                    timelineEventMapper = dependencies.timelineEventMapper,
                    uiEchoManager = uiEchoManager,
                    threadsAwarenessHandler = dependencies.threadsAwarenessHandler,
                    lightweightSettingsStorage = dependencies.lightweightSettingsStorage,
                    initialEventId = mode.originEventId(),
                    onBuiltEvents = dependencies.onEventsUpdated,
                    onEventsDeleted = dependencies.onEventsDeleted,
                    localEchoEventFactory = dependencies.localEchoEventFactory,
                    decorator = createTimelineEventDecorator()
            )
        }
    }

    private fun ChunkEntity.cleanupSelfLinkingChunks() {
        cleanupSelfLinkingChunksInDirection(
                "backward",
                { it.prevChunk },
                {
                    if (it.prevChunk?.nextChunk == it) {
                        it.prevChunk?.nextChunk = null
                    }
                    it.prevChunk = null
                }
        )
        cleanupSelfLinkingChunksInDirection(
                "forward",
                { it.nextChunk },
                {
                    if (it.nextChunk?.prevChunk == it) {
                        it.nextChunk?.prevChunk = null
                    }
                    it.nextChunk = null
                }
        )
    }

    private fun ChunkEntity.fixChunkLoops() {
        fixChunkLoopsInDirection("backward",
                { it.prevChunk },
                { it.sort(TimelineEventEntityFields.DISPLAY_INDEX).firstOrNull() },
                { it.sort(TimelineEventEntityFields.DISPLAY_INDEX).lastOrNull() },
                { a, b -> b - a },
                {
                    if (it.prevChunk?.nextChunk == it) {
                        it.prevChunk?.nextChunk = null
                    }
                    it.prevChunk = null
                }
        )
        fixChunkLoopsInDirection(
                "forward",
                { it.nextChunk },
                { it.sort(TimelineEventEntityFields.DISPLAY_INDEX).lastOrNull() },
                { it.sort(TimelineEventEntityFields.DISPLAY_INDEX).firstOrNull() },
                { a, b -> a - b },
                {
                    if (it.nextChunk?.prevChunk == it) {
                        it.nextChunk?.prevChunk = null
                    }
                    it.nextChunk = null
                }
        )
    }


    private fun ChunkEntity.cleanupSelfLinkingChunksInDirection(directionName: String,
                                                                directionFun: (ChunkEntity) -> ChunkEntity?,
                                                                unlinkFun: (ChunkEntity) -> Unit) {
        val visited = hashSetOf<String>()
        var chunk: ChunkEntity? = this
        while (chunk != null) {
            if (chunk.identifier() in visited) {
                return
            }
            visited.add(chunk.identifier())
            val next = directionFun(chunk)
            if (next != null && next.timelineEvents.isEmpty() && next.nextToken == next.prevToken) {
                Timber.i("Stuck self-loop cleanup $directionName: remove empty ${next.identifier()}")
                realm.executeTransaction {
                    unlinkFun(chunk!!)
                }
            }
            chunk = next
            if (visited.size > MAX_CHUNK_REPAIR_CHECK_COUNT) {
                Timber.i("Abort searching $directionName for empty chunks after ${visited.size} chunks")
                return
            }
        }
    }

    private fun ChunkEntity.fixChunkLoopsInDirection(directionName: String,
                                                     directionFun: (ChunkEntity) -> ChunkEntity?,
                                                     lastEventFun: (RealmList<TimelineEventEntity>) -> TimelineEventEntity?,
                                                     firstEventFun: (RealmList<TimelineEventEntity>) -> TimelineEventEntity?,
                                                     rateGapFun: (Long, Long) -> Long,
                                                     unlinkFun: (ChunkEntity) -> Unit) {
        var firstRepeatedChunk: String? = null
        val visited = hashSetOf<String>()
        var chunk: ChunkEntity? = this
        while (chunk != null) {
            if (chunk.identifier() in visited) {
                firstRepeatedChunk = chunk.identifier()
                break
            }
            visited.add(chunk.identifier())
            if (visited.size > MAX_CHUNK_REPAIR_CHECK_COUNT) {
                Timber.i("Abort searching $directionName for chunk loops after ${visited.size} chunks")
                return
            }
            chunk = directionFun(chunk)
        }

        if (firstRepeatedChunk != null) {
            Timber.e("Timeline loop detected ($directionName), searching for a good place to break it up")
            // Iterate all chunks again. This time, we know which chunks included in the loop,
            // so we want to compare the events between these chunks to find the one which seems
            chunk = this
            var foundRepetition = false
            var lastEventTs: Long? = null
            var lastEventChunk: ChunkEntity? = null
            var worstFoundTsJump: Long = 0
            var worstChunk: ChunkEntity? = null
            var done = false
            var loopSize = -1
            while (chunk != null && !done) {
                if (chunk.identifier() == firstRepeatedChunk) {
                    if (foundRepetition) {
                        // Do not break yet, or we might skip the best chunk for unlinking
                        done = true
                    } else {
                        foundRepetition = true
                    }
                }
                if (foundRepetition) {
                    loopSize++
                    val nextEventTs = firstEventFun(chunk.timelineEvents)?.root?.originServerTs
                    if (lastEventTs != null && nextEventTs != null) {
                        val tsJump = rateGapFun(lastEventTs, nextEventTs)
                        if (tsJump > worstFoundTsJump) {
                            worstChunk = lastEventChunk
                            worstFoundTsJump = tsJump
                        }
                    }
                    Timber.v("Loop breakup: compare ${lastEventChunk?.identifier()}/$lastEventTs to ${chunk.identifier()}$nextEventTs")
                    val newLastEventTs = lastEventFun(chunk.timelineEvents)?.root?.originServerTs
                    if (newLastEventTs != null) {
                        lastEventTs = newLastEventTs
                        lastEventChunk = chunk
                    }
                }
                chunk = directionFun(chunk)
            }
            if (worstChunk != null) {
                Timber.w("Splitting $directionName timeline chain between ${worstChunk.identifier()} and ${directionFun(worstChunk)?.identifier()} | room $roomId loopSize $loopSize loadedChunk ${identifier()}")
                realm.executeTransaction {
                    unlinkFun(worstChunk)
                }
            } else {
                Timber.e("Splitting $directionName timeline failed, no worst chunk found | room $roomId loopSize $loopSize loadedChunk ${identifier()}")
            }
        }
    }
}
