/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.android.sdk.internal.session.sync.handler.room

import de.spiritcroc.matrixsdk.util.DbgUtil
import de.spiritcroc.matrixsdk.util.Dimber
import io.realm.Realm
import org.matrix.android.sdk.api.extensions.orTrue
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.room.read.ReadService
import org.matrix.android.sdk.api.session.room.read.ReadService.Companion.THREAD_ID_MAIN
import org.matrix.android.sdk.api.session.room.read.ReadService.Companion.THREAD_ID_MAIN_OR_NULL
import org.matrix.android.sdk.internal.database.model.EventEntity
import org.matrix.android.sdk.internal.database.model.ReadReceiptEntity
import org.matrix.android.sdk.internal.database.model.ReadReceiptsSummaryEntity
import org.matrix.android.sdk.internal.database.query.createUnmanaged
import org.matrix.android.sdk.internal.database.query.getOrCreate
import org.matrix.android.sdk.internal.database.query.where
import org.matrix.android.sdk.internal.session.sync.RoomSyncEphemeralTemporaryStore
import org.matrix.android.sdk.internal.session.sync.SyncResponsePostTreatmentAggregator
import timber.log.Timber
import javax.inject.Inject

// the receipts dictionaries
// key   : $EventId
// value : dict key $UserId
//              value dict key ts
//                    dict value ts value
internal typealias ReadReceiptContent = Map<String, Map<String, Map<String, Map<String, Any>>>>

private const val READ_KEY = "m.read"
private const val TIMESTAMP_KEY = "ts"
private const val THREAD_ID_KEY = "thread_id"

internal class ReadReceiptHandler @Inject constructor(
        private val roomSyncEphemeralTemporaryStore: RoomSyncEphemeralTemporaryStore
) {

    private val rrDimber = Dimber("ReadReceipts", DbgUtil.DBG_READ_RECEIPTS)

    companion object {

        fun createContent(
                userId: String,
                eventId: String,
                threadId: String?,
                currentTimeMillis: Long
        ): ReadReceiptContent {
            val userReadReceipt = mutableMapOf<String, Any>(
                    TIMESTAMP_KEY to currentTimeMillis.toDouble(),
            )
            threadId?.let {
                userReadReceipt.put(THREAD_ID_KEY, threadId)
            }
            return mapOf(
                    eventId to mapOf(
                            READ_KEY to mapOf(
                                    userId to userReadReceipt
                            )
                    )
            )
        }
    }

    fun handle(
            realm: Realm,
            roomId: String,
            content: ReadReceiptContent?,
            isInitialSync: Boolean,
            aggregator: SyncResponsePostTreatmentAggregator?
    ) {
        content ?: return

        try {
            handleReadReceiptContent(realm, roomId, content, isInitialSync, aggregator)
        } catch (exception: Exception) {
            Timber.e("Fail to handle read receipt for room $roomId")
        }
    }

    private fun handleReadReceiptContent(
            realm: Realm,
            roomId: String,
            content: ReadReceiptContent,
            isInitialSync: Boolean,
            aggregator: SyncResponsePostTreatmentAggregator?
    ) {
        if (isInitialSync) {
            initialSyncStrategy(realm, roomId, content)
        } else {
            incrementalSyncStrategy(realm, roomId, content, aggregator)
        }
    }

    private fun initialSyncStrategy(realm: Realm, roomId: String, content: ReadReceiptContent) {
        val readReceiptSummaries = ArrayList<ReadReceiptsSummaryEntity>()
        // SC: fight duplicate read markers
        val summariesByEventId = HashMap<String, ReadReceiptsSummaryEntity>()
        val mainReceiptByUserId = HashMap<String, ReadReceiptEntity>()
        for ((eventId, receiptDict) in content) {
            val userIdsDict = receiptDict[READ_KEY] ?: continue
            val readReceiptsSummary = ReadReceiptsSummaryEntity(eventId = eventId, roomId = roomId)

            for ((userId, paramsDict) in userIdsDict) {
                val ts = paramsDict[TIMESTAMP_KEY] as? Double ?: 0.0
                val threadId = paramsDict[THREAD_ID_KEY] as String?
                val receiptEntity = ReadReceiptEntity.createUnmanaged(roomId, eventId, userId, threadId, ts)
                rrDimber.i{"Handle initial sync RR $roomId / $userId thread $threadId: event $eventId"}
                readReceiptsSummary.readReceipts.add(receiptEntity)
                // SC: fight duplicate read markers, by unifying main|null into the same marker
                if (threadId in listOf(null, THREAD_ID_MAIN) && mainReceiptByUserId[userId]?.originServerTs?.let { it < ts }.orTrue()) {
                    mainReceiptByUserId[userId] = ReadReceiptEntity.createUnmanaged(roomId, eventId, userId, THREAD_ID_MAIN_OR_NULL, ts)
                }
            }
            readReceiptSummaries.add(readReceiptsSummary)
            summariesByEventId[eventId] = readReceiptsSummary
        }
        mainReceiptByUserId.forEach {
            rrDimber.i{"Handle initial sync RR $roomId / ${it.value.userId} thread ${it.value.threadId}: event ${it.value.eventId}"}
            summariesByEventId[it.value.eventId]?.readReceipts?.add(it.value)
        }
        realm.insertOrUpdate(readReceiptSummaries)
    }

    private fun incrementalSyncStrategy(
            realm: Realm,
            roomId: String,
            content: ReadReceiptContent,
            aggregator: SyncResponsePostTreatmentAggregator?
    ) {
        // First check if we have data from init sync to handle
        getContentFromInitSync(roomId)?.let {
            Timber.d("INIT_SYNC Insert during incremental sync RR for room $roomId")
            doIncrementalSyncStrategy(realm, roomId, it)
            aggregator?.ephemeralFilesToDelete?.add(roomId)
        }

        doIncrementalSyncStrategy(realm, roomId, content)
    }

    private fun doIncrementalSyncStrategy(realm: Realm, roomId: String, content: ReadReceiptContent) {
        for ((eventId, receiptDict) in content) {
            val userIdsDict = receiptDict[READ_KEY] ?: continue
            val readReceiptsSummary = ReadReceiptsSummaryEntity.where(realm, eventId).findFirst()
                    ?: realm.createObject(ReadReceiptsSummaryEntity::class.java, eventId).apply {
                        this.roomId = roomId
                    }

            for ((userId, paramsDict) in userIdsDict) {
                val ts = paramsDict[TIMESTAMP_KEY] as? Double ?: 0.0
                val syncedThreadId = paramsDict[THREAD_ID_KEY] as String?

                // SC: fight duplicate read receipts in main timeline
                val receiptDestinations = if (syncedThreadId in listOf(null, THREAD_ID_MAIN)) {
                    setOf(syncedThreadId, THREAD_ID_MAIN_OR_NULL)
                } else {
                    setOf(syncedThreadId)
                }
                receiptDestinations.forEach { threadId ->
                    val receiptEntity = ReadReceiptEntity.getOrCreate(realm, roomId, userId, threadId)
                    val shouldForceMon: Boolean
                    val shouldSkipMon: Boolean
                    if (threadId == THREAD_ID_MAIN_OR_NULL) {
                        val oldEventTs = EventEntity.where(realm, roomId, receiptEntity.eventId).findFirst()?.originServerTs
                        val newEvent = EventEntity.where(realm, roomId, eventId).findFirst()
                        val newEventTs = newEvent?.originServerTs
                        // Avoid setting MAIN_OR_NULL to an event which only exists in the combined null-timeline, but not in the actual main-timeline
                        if (syncedThreadId != THREAD_ID_MAIN && newEvent?.rootThreadEventId != null) {
                            shouldForceMon = false
                            shouldSkipMon = true
                        } else {
                            shouldForceMon = oldEventTs != null && newEventTs != null && oldEventTs < newEventTs
                            shouldSkipMon = oldEventTs != null && newEventTs != null && oldEventTs > newEventTs
                        }
                    } else {
                        shouldForceMon = false
                        shouldSkipMon = false
                    }
                    // ensure new ts is superior to the previous one
                    if (shouldForceMon || (ts > receiptEntity.originServerTs && !shouldSkipMon)) {
                        rrDimber.i { "Handle outdated sync RR $roomId / $userId thread $threadId($syncedThreadId): event ${receiptEntity.eventId} -> $eventId || force $shouldForceMon legacy ${ts > receiptEntity.originServerTs}" }
                        ReadReceiptsSummaryEntity.where(realm, receiptEntity.eventId).findFirst()?.also {
                            it.readReceipts.remove(receiptEntity)
                        }
                        receiptEntity.eventId = eventId
                        receiptEntity.originServerTs = ts
                        readReceiptsSummary.readReceipts.add(receiptEntity)
                    } else {
                        rrDimber.i { "Handle keep sync RR $roomId / $userId thread $threadId($syncedThreadId): event ${receiptEntity.eventId} (not $eventId) || $shouldSkipMon" }
                    }
                }
            }
        }
    }

    fun getContentFromInitSync(roomId: String): ReadReceiptContent? {
        val dataFromFile = roomSyncEphemeralTemporaryStore.read(roomId)

        dataFromFile ?: return null

        @Suppress("UNCHECKED_CAST")
        val content = dataFromFile
                .events
                ?.firstOrNull { it.type == EventType.RECEIPT }
                ?.content as? ReadReceiptContent

        if (content == null) {
            // We can delete the file now
            roomSyncEphemeralTemporaryStore.delete(roomId)
        }

        return content
    }

    fun onContentFromInitSyncHandled(roomId: String) {
        roomSyncEphemeralTemporaryStore.delete(roomId)
    }
}
