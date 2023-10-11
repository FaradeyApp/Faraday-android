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

import de.spiritcroc.matrixsdk.StaticScSdkHelper
import de.spiritcroc.matrixsdk.util.DbgUtil
import de.spiritcroc.matrixsdk.util.Dimber
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly
import org.matrix.android.sdk.api.MatrixCoroutineDispatchers
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.sender.SenderInfo
import org.matrix.android.sdk.api.session.room.timeline.Timeline
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.TimelineSettings
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.internal.database.mapper.TimelineEventMapper
import org.matrix.android.sdk.internal.session.room.membership.LoadRoomMembersTask
import org.matrix.android.sdk.internal.session.room.relation.threads.FetchThreadTimelineTask
import org.matrix.android.sdk.internal.session.room.send.LocalEchoEventFactory
import org.matrix.android.sdk.internal.session.room.state.StateEventDataSource
import org.matrix.android.sdk.internal.session.sync.handler.room.ReadReceiptHandler
import org.matrix.android.sdk.internal.session.sync.handler.room.ThreadsAwarenessHandler
import org.matrix.android.sdk.internal.task.SemaphoreCoroutineSequencer
import org.matrix.android.sdk.internal.util.createBackgroundHandler
import org.matrix.android.sdk.internal.util.time.Clock
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class DefaultTimeline(
        private val roomId: String,
        private val initialEventId: String?,
        private var targetEventOffset: Int = 0,
        private val realmConfiguration: RealmConfiguration,
        private val loadRoomMembersTask: LoadRoomMembersTask,
        private val readReceiptHandler: ReadReceiptHandler,
        private val settings: TimelineSettings,
        private val coroutineDispatchers: MatrixCoroutineDispatchers,
        private val clock: Clock,
        localEchoEventFactory: LocalEchoEventFactory,
        stateEventDataSource: StateEventDataSource,
        paginationTask: PaginationTask,
        getEventTask: GetContextOfEventTask,
        fetchTokenAndPaginateTask: FetchTokenAndPaginateTask,
        fetchThreadTimelineTask: FetchThreadTimelineTask,
        timelineEventMapper: TimelineEventMapper,
        timelineInput: TimelineInput,
        threadsAwarenessHandler: ThreadsAwarenessHandler,
        lightweightSettingsStorage: LightweightSettingsStorage,
        eventDecryptor: TimelineEventDecryptor,
) : Timeline {

    companion object {
        val BACKGROUND_HANDLER = createBackgroundHandler("Matrix-DefaultTimeline_Thread")
    }

    override val timelineID = UUID.randomUUID().toString()

    private val listeners = CopyOnWriteArrayList<Timeline.Listener>()
    private val isStarted = AtomicBoolean(false)
    private val forwardState = AtomicReference(Timeline.PaginationState())
    private val backwardState = AtomicReference(Timeline.PaginationState())

    private val backgroundRealm = AtomicReference<Realm>()
    private val timelineDispatcher = BACKGROUND_HANDLER.asCoroutineDispatcher()
    private val timelineScope = CoroutineScope(SupervisorJob() + timelineDispatcher)
    private val sequencer = SemaphoreCoroutineSequencer()
    private val postSnapshotSignalFlow = MutableSharedFlow<Unit>(0)

    private var isFromThreadTimeline = false
    private var rootThreadEventId: String? = null

    private var targetEventId = initialEventId
    private val dimber = Dimber("TimelineChunks", DbgUtil.DBG_TIMELINE_CHUNKS)

    private val strategyDependencies = LoadTimelineStrategy.Dependencies(
            timelineSettings = settings,
            realm = backgroundRealm,
            eventDecryptor = eventDecryptor,
            paginationTask = paginationTask,
            fetchTokenAndPaginateTask = fetchTokenAndPaginateTask,
            fetchThreadTimelineTask = fetchThreadTimelineTask,
            getContextOfEventTask = getEventTask,
            timelineInput = timelineInput,
            timelineEventMapper = timelineEventMapper,
            threadsAwarenessHandler = threadsAwarenessHandler,
            lightweightSettingsStorage = lightweightSettingsStorage,
            onEventsUpdated = this::sendSignalToPostSnapshot,
            onEventsDeleted = this::onEventsDeleted,
            onLimitedTimeline = this::onLimitedTimeline,
            onLastForwardDeleted = this::onLastForwardDeleted,
            onNewTimelineEvents = this::onNewTimelineEvents,
            stateEventDataSource = stateEventDataSource,
            matrixCoroutineDispatchers = coroutineDispatchers,
            localEchoEventFactory = localEchoEventFactory
    )

    private var strategy: LoadTimelineStrategy = buildStrategy(LoadTimelineStrategy.Mode.Live)
    private var startTimelineJob: Job? = null

    override val isLive: Boolean
        get() = !getPaginationState(Timeline.Direction.FORWARDS).hasMoreToLoad

    override fun addListener(listener: Timeline.Listener): Boolean {
        listeners.add(listener)
        timelineScope.launch {
            val snapshot = strategy.buildSnapshot()
            withContext(coroutineDispatchers.main) {
                tryOrNull { listener.onTimelineUpdated(snapshot) }
            }
        }
        return true
    }

    override fun removeListener(listener: Timeline.Listener): Boolean {
        return listeners.remove(listener)
    }

    override fun removeAllListeners() {
        listeners.clear()
    }

    override fun start(rootThreadEventId: String?) {
        timelineScope.launch {
            loadRoomMembersIfNeeded()
        }
        startTimelineJob = timelineScope.launch {
            sequencer.post {
                if (isStarted.compareAndSet(false, true)) {
                    isFromThreadTimeline = rootThreadEventId != null
                    this@DefaultTimeline.rootThreadEventId = rootThreadEventId
                    // /
                    val realm = Realm.getInstance(realmConfiguration)
                    ensureReadReceiptAreLoaded(realm)
                    backgroundRealm.set(realm)
                    listenToPostSnapshotSignals()
                    openAround(initialEventId, rootThreadEventId)
                    postSnapshot()
                }
            }
        }
    }

    override fun dispose() {
        timelineScope.coroutineContext.cancelChildren()
        timelineScope.launch {
            sequencer.post {
                if (isStarted.compareAndSet(true, false)) {
                    strategy.onStop()
                    backgroundRealm.get().closeQuietly()
                }
            }
        }
    }

    override fun restartWithEventId(eventId: String?) {
        timelineScope.launch {
            sequencer.post {
                openAround(eventId, rootThreadEventId)
                postSnapshot()
            }
        }
    }

    override fun hasMoreToLoad(direction: Timeline.Direction): Boolean {
        return getPaginationState(direction).hasMoreToLoad
    }

    override fun paginate(direction: Timeline.Direction, count: Int) {
        timelineScope.launch {
            startTimelineJob?.join()
            val postSnapshot = loadMore(count, direction, fetchOnServerIfNeeded = true)
            if (postSnapshot) {
                postSnapshot()
            }
        }
    }

    override suspend fun awaitPaginate(direction: Timeline.Direction, count: Int): List<TimelineEvent> {
        startTimelineJob?.join()
        withContext(timelineDispatcher) {
            loadMore(count, direction, fetchOnServerIfNeeded = true)
        }
        return getSnapshot()
    }

    override fun getSnapshot(): List<TimelineEvent> {
        return strategy.buildSnapshot()
    }

    override fun senderWithLiveRoomState(senderInfo: SenderInfo): SenderInfo = strategy.senderWithLiveRoomState(senderInfo)

    override fun getIndexOfEvent(eventId: String?): Int? {
        if (eventId == null) return null
        return strategy.getBuiltEventIndex(eventId)
    }

    override fun getPaginationState(direction: Timeline.Direction): Timeline.PaginationState {
        return if (direction == Timeline.Direction.BACKWARDS) {
            backwardState
        } else {
            forwardState
        }.get()
    }

    private suspend fun loadMore(count: Int, direction: Timeline.Direction, fetchOnServerIfNeeded: Boolean): Boolean {
        val baseLogMessage = "loadMore(count: $count, direction: $direction, roomId: $roomId, fetchOnServer: $fetchOnServerIfNeeded)"
        Timber.v("$baseLogMessage started")
        if (!isStarted.get()) {
            throw IllegalStateException("You should call start before using timeline")
        }
        val currentState = getPaginationState(direction)
        if (!currentState.hasMoreToLoad) {
            Timber.v("$baseLogMessage : nothing more to load")
            return false
        }
        if (currentState.loading) {
            Timber.v("$baseLogMessage : already loading")
            return false
        }
        updateState(direction) {
            it.copy(loading = true)
        }
        val loadMoreResult = try {
            strategy.loadMore(count, direction, fetchOnServerIfNeeded)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                LoadMoreResult.FAILURE
            } else {
                // Timeline could not be loaded with a (likely) permanent issue, such as the
                // server now knowing the initialEventId, so we want to show an error message
                // and possibly restart without initialEventId.
                onTimelineFailure(throwable)
                return false
            }
        }
        Timber.v("$baseLogMessage: result $loadMoreResult")
        val hasMoreToLoad = loadMoreResult != LoadMoreResult.REACHED_END
        updateState(direction) {
            it.copy(loading = false, hasMoreToLoad = hasMoreToLoad, hasLoadedAtLeastOnce = true)
        }
        // Stop forward-loading animation also when backwards loading, if we know we have all already
        if (direction == Timeline.Direction.BACKWARDS) {
            updateState(Timeline.Direction.FORWARDS) {
                if (!it.hasLoadedAtLeastOnce && strategy.hasFullyLoadedForward()) {
                    it.copy(hasMoreToLoad = false)
                } else {
                    it
                }
            }
        }
        return true
    }

    private suspend fun openAround(eventId: String?, rootThreadEventId: String?) = withContext(timelineDispatcher) {
        val baseLogMessage = "openAround(eventId: $eventId)"
        Timber.v("$baseLogMessage started")
        if (!isStarted.get()) {
            throw IllegalStateException("You should call start before using timeline")
        }
        strategy.onStop()

        setTargetEventId(eventId)

        strategy = when {
            rootThreadEventId != null -> buildStrategy(LoadTimelineStrategy.Mode.Thread(rootThreadEventId))
            eventId == null -> buildStrategy(LoadTimelineStrategy.Mode.Live)
            else -> buildStrategy(LoadTimelineStrategy.Mode.Permalink(eventId))
        }

        rootThreadEventId?.let {
            initPaginationStates(null)
        } ?: initPaginationStates(eventId)

        strategy.onStart()
        loadMore(
                count = strategyDependencies.timelineSettings.initialSize,
                direction = Timeline.Direction.BACKWARDS,
                fetchOnServerIfNeeded = false
        )

        Timber.v("$baseLogMessage finished")
    }

    private fun initPaginationStates(eventId: String?) {
        updateState(Timeline.Direction.FORWARDS) {
            it.copy(loading = false, hasMoreToLoad = eventId != null, hasLoadedAtLeastOnce = false)
        }
        updateState(Timeline.Direction.BACKWARDS) {
            it.copy(loading = false, hasMoreToLoad = true, hasLoadedAtLeastOnce = false)
        }
    }

    private fun sendSignalToPostSnapshot(withThrottling: Boolean) {
        timelineScope.launch {
            if (withThrottling) {
                postSnapshotSignalFlow.emit(Unit)
            } else {
                postSnapshot()
            }
        }
    }

    private fun listenToPostSnapshotSignals() {
        postSnapshotSignalFlow
                .sample(150)
                .onEach {
                    postSnapshot()
                }
                .launchIn(timelineScope)
    }

    private fun onLimitedTimeline() {
        timelineScope.launch {
            sequencer.post {
                Timber.i("onLimitedTimeline: load more backwards")
                initPaginationStates(null)
                loadMore(settings.initialSize, Timeline.Direction.BACKWARDS, false)
                postSnapshot()
            }
        }
    }

    private fun onLastForwardDeleted() {
        timelineScope.launch {
            // If we noticed before we don't have more to load, we want to re-try now.
            // Since the last forward chunk got deleted, more may be available now using pagination.
            if (hasMoreToLoad(Timeline.Direction.FORWARDS)) {
                Timber.i("onLastForwardDeleted: no action necessary")
                return@launch
            }
            Timber.i("onLastForwardDeleted: load more forwards")
            //initPaginationStates(null)
            updateState(Timeline.Direction.FORWARDS) {
                it.copy(hasMoreToLoad = true)
            }
            loadMore(settings.initialSize, Timeline.Direction.FORWARDS, true)
            postSnapshot()
        }
    }

    private fun onEventsDeleted() {
        // Some event have been deleted, for instance when a user has been ignored.
        // Restart the timeline (live)
        restartWithEventId(null)
    }

    private suspend fun postSnapshot() {
        val snapshot = strategy.buildSnapshot()
        Timber.v("Post snapshot of ${snapshot.size} events")
        // Async debugging to not slow down things too much
        dimber.exec {
            timelineScope.launch(coroutineDispatchers.computation) {
                checkTimelineConsistency("DefaultTimeline.postSnapshot", snapshot, verbose = false) { msg ->
                    if (DbgUtil.isDbgEnabled(DbgUtil.DBG_SHOW_DISPLAY_INDEX)) {
                        timelineScope.launch(coroutineDispatchers.main) {
                            StaticScSdkHelper.scSdkPreferenceProvider?.annoyDevelopersWithToast(msg)
                        }
                    }
                }
            }
        }
        withContext(coroutineDispatchers.main) {
            listeners.forEach {
                if (initialEventId != null && isFromThreadTimeline && snapshot.firstOrNull { it.eventId == initialEventId } == null) {
                    // We are in a thread timeline with a permalink, post update timeline only when the appropriate message have been found
                    tryOrNull { it.onTimelineUpdated(arrayListOf()) }
                } else {
                    // In all the other cases update timeline as expected
                    tryOrNull { it.onTimelineUpdated(snapshot) }
                }
            }
        }
    }

    private fun onNewTimelineEvents(eventIds: List<String>) {
        timelineScope.launch(coroutineDispatchers.main) {
            listeners.forEach {
                tryOrNull { it.onNewTimelineEvents(eventIds) }
            }
        }
    }

    private fun updateState(direction: Timeline.Direction, update: (Timeline.PaginationState) -> Timeline.PaginationState) {
        val stateReference = when (direction) {
            Timeline.Direction.FORWARDS -> forwardState
            Timeline.Direction.BACKWARDS -> backwardState
        }
        val currentValue = stateReference.get()
        val newValue = update(currentValue)
        stateReference.set(newValue)
        if (newValue != currentValue) {
            postPaginationState(direction, newValue)
        }
    }

    private fun postPaginationState(direction: Timeline.Direction, state: Timeline.PaginationState) {
        timelineScope.launch(coroutineDispatchers.main) {
            Timber.v("Post $direction pagination state: $state ")
            listeners.forEach {
                tryOrNull { it.onStateUpdated(direction, state) }
            }
        }
    }

    private fun onTimelineFailure(throwable: Throwable) {
        timelineScope.launch(coroutineDispatchers.main) {
            listeners.forEach {
                tryOrNull { it.onTimelineFailure(throwable) }
            }
        }
    }

    private fun buildStrategy(mode: LoadTimelineStrategy.Mode): LoadTimelineStrategy {
        return LoadTimelineStrategy(
                roomId = roomId,
                timelineId = timelineID,
                mode = mode,
                dependencies = strategyDependencies,
                clock = clock,
        )
    }

    private suspend fun loadRoomMembersIfNeeded() {
        val loadRoomMembersParam = LoadRoomMembersTask.Params(roomId, excludeMembership = Membership.LEAVE)
        try {
            loadRoomMembersTask.execute(loadRoomMembersParam)
        } catch (failure: Throwable) {
            Timber.v("Failed to load room members. Retry in 10s.")
            delay(10_000L)
            loadRoomMembersIfNeeded()
        }
    }

    private fun ensureReadReceiptAreLoaded(realm: Realm) {
        readReceiptHandler.getContentFromInitSync(roomId)
                ?.also {
                    Timber.d("INIT_SYNC Insert when opening timeline RR for room $roomId")
                }
                ?.let { readReceiptContent ->
                    realm.executeTransactionAsync {
                        readReceiptHandler.handle(it, roomId, readReceiptContent, false, null)
                        readReceiptHandler.onContentFromInitSyncHandled(roomId)
                    }
                }
    }

    override fun getTargetEventId(): String? {
        return targetEventId
    }

    override fun setTargetEventId(eventId: String?) {
        targetEventId = eventId
    }

    override fun getTargetEventOffset(): Int {
        return targetEventOffset
    }

    override fun setTargetEventOffset(offset: Int) {
        targetEventOffset = offset
    }
}

fun checkTimelineConsistency(location: String, events: List<TimelineEvent>, verbose: Boolean = true, sendToastFunction: (String) -> Unit = {}) {
    // Set verbose = false if this is a much repeated check and not close to the likely issue source -> in this case, we only want to complain about actually found issues
    try {
        var potentialIssues = 0
        // Note that the "previous" event is actually newer than the currently looked at event,
        // since the list is ordered from new to old
        var prev: TimelineEvent? = null
        var toastMsg = ""
        for (i in events.indices) {
            val event = events[i]
            if (prev != null) {
                if (prev.eventId == event.eventId) {
                    // This should never happen in a bug-free world, as far as I'm aware
                    Timber.e("Timeline inconsistency found at $location, $i/${events.size}: double event ${event.eventId}")
                    potentialIssues++
                } else if (prev.displayIndex != event.displayIndex + 1 &&
                        // Jumps from -1 to 1 seem to be normal, have only seen index 0 for local echos yet
                        (prev.displayIndex != 1 || event.displayIndex != -1)) {
                    // Note that jumps in either direction may be normal for some scenarios:
                    // - Events between two chunks lead to a new indexing, so one may start at 1, or even something negative.
                    // - The list may omit unsupported events (I guess?), thus causing gaps in the indices.
                    Timber.w("Possible timeline inconsistency found at $location, $i/${events.size}: ${event.displayIndex}->${prev.displayIndex}, ${event.eventId} -> ${prev.eventId}")
                    // Toast only those which are particularly suspicious
                    if (prev.displayIndex != 1 && prev.displayIndex != 0 && prev.displayIndex >= event.displayIndex && !(prev.displayIndex == -1 && event.displayIndex == -1)) {
                        toastMsg += "${event.displayIndex}->${prev.displayIndex},"
                    }
                    potentialIssues++
                }
            }
            prev = event
        }
        if (toastMsg.isNotEmpty()) {
            sendToastFunction(toastMsg.substring(0, toastMsg.length-1))
        }
        if (verbose || potentialIssues > 0) {
            Timber.i("Check timeline consistency from $location for ${events.size} events from ${events.firstOrNull()?.eventId} to ${events.lastOrNull()?.eventId}, found $potentialIssues possible issues")
        }
    } catch (t: Throwable) {
        Timber.e("Failed check timeline consistency from $location", t)
    }
}
