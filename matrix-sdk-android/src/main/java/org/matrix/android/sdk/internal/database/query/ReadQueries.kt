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
package org.matrix.android.sdk.internal.database.query

import de.spiritcroc.matrixsdk.util.Dimber
import io.realm.Realm
import io.realm.RealmConfiguration
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.session.events.model.LocalEcho
import org.matrix.android.sdk.api.session.room.read.ReadService
import org.matrix.android.sdk.internal.database.helper.isMoreRecentThan
import org.matrix.android.sdk.internal.database.model.ChunkEntity
import org.matrix.android.sdk.internal.database.model.ReadMarkerEntity
import org.matrix.android.sdk.internal.database.model.ReadReceiptEntity
import org.matrix.android.sdk.internal.database.model.TimelineEventEntity
import org.matrix.android.sdk.internal.database.model.RoomSummaryEntity
import org.matrix.android.sdk.internal.database.model.getThreadId

internal fun isEventRead(
        realmConfiguration: RealmConfiguration,
        userId: String?,
        roomId: String?,
        eventId: String?,
        shouldCheckIfReadInEventsThread: Boolean,
        eventTs: Long? = null,
        ignoreSenderId: Boolean = false,
        handleAsUnreadForNonZeroUnreadCount: Boolean = false
): Boolean {
    if (userId.isNullOrBlank() || roomId.isNullOrBlank() || eventId.isNullOrBlank()) {
        return false
    }
    if (LocalEcho.isLocalEchoId(eventId)) {
        return true
    }

    return Realm.getInstance(realmConfiguration).use { realm ->
        // For SetReadMarkersTask, in case the message is somehow still marked as unread even though the receipt is on bottom,
        // we want to handle it as if it where unread.
        // This scenario can happen after sending a message, but not updating the read receipt manually.
        if (handleAsUnreadForNonZeroUnreadCount) {
            val roomSummary = RoomSummaryEntity.where(realm, roomId).findFirst()
            if (roomSummary?.hasUnreadMessages.orFalse()) {
                return false
            }
        }
        val eventToCheck = TimelineEventEntity.where(realm, roomId, eventId).findFirst()
        when {
            // The event doesn't exist locally, let's assume it hasn't been read unless we know all unread events
            eventToCheck == null -> isReadMarkerMoreRecentThanMissingEvent(realm, roomId, userId, eventTs)
                    //.also {
                        //Timber.i("isEventRead: eventToCheck ($eventId) == null -> assume read: $it")
                    //}
            !ignoreSenderId && eventToCheck.root?.sender == userId -> true
            // If new event exists and the latest event is from ourselves we can infer the event is read
            !ignoreSenderId && latestEventIsFromSelf(realm, roomId, userId) -> true
            eventToCheck.isBeforeLatestReadReceipt(realm, roomId, userId, null) -> true
            (shouldCheckIfReadInEventsThread && eventToCheck.isBeforeLatestReadReceipt(realm, roomId, userId, eventToCheck.getThreadId())) -> true
            else -> false
        }
    }
}

private fun isReadMarkerMoreRecentThanMissingEvent(realm: Realm, roomId: String, userId: String, eventTs: Long?): Boolean {
    if (eventTs == null) {
        // We don't have enough information to do an educated guess without timestamp:
        // Case 1: a fastlane event came through for which we didn't sync yet
        // -> the read marker may be very well in the latest chunk, but the missing event is still unread
        // Case 2: We synced all recent events, but have some gap where the missing event would be
        // -> if the read marker is at the bottom, the missing event should be marked as read in this case
        // => fallback to showing the notification either way
        return false
    }
    // Assume a missing event is read if:
    // - The read receipt is in the last forward chunk and
    // - The timestamp of the notification is smaller than the read receipt's one
    return ReadReceiptEntity.where(realm, roomId, userId, null).findFirst()?.let { readReceipt ->
        val readReceiptEvent = TimelineEventEntity.where(realm, roomId, readReceipt.eventId).findFirst()
        //Timber.i("isReadMarkerMoreRecentThanMissing? ${readReceiptEvent?.chunk?.firstOrNull()?.isLastForward} && ${(readReceiptEvent?.root?.originServerTs ?: 0) > eventTs} <- ${(readReceiptEvent?.root?.originServerTs ?: 0)} > $eventTs")
        readReceiptEvent?.chunk?.firstOrNull()?.isLastForward.orFalse() && (readReceiptEvent?.root?.originServerTs ?: 0) > eventTs
    } ?: false
}

private fun latestEventIsFromSelf(realm: Realm, roomId: String, userId: String) = TimelineEventEntity.latestEvent(realm, roomId, true)
        ?.root?.sender == userId

private fun TimelineEventEntity.isBeforeLatestReadReceipt(realm: Realm, roomId: String, userId: String, threadId: String?): Boolean {
    val isMoreRecent = ReadReceiptEntity.where(realm, roomId, userId, threadId).findFirst()?.let { readReceipt ->
        val readReceiptEvent = TimelineEventEntity.where(realm, roomId, readReceipt.eventId).findFirst()
        readReceiptEvent?.isMoreRecentThan(this)
    } ?: false
    return isMoreRecent
}

/**
 * Missing events can be caused by the latest timeline chunk no longer contain an older event or
 * by fast lane eagerly displaying events before the database has finished updating.
 */
private fun hasReadMissingEvent(realm: Realm,
                                latestChunkEntity: ChunkEntity,
                                roomId: String,
                                userId: String,
                                eventId: String,
                                threadId: String? = ReadService.THREAD_ID_MAIN): Boolean {
    return realm.doesEventExistInChunkHistory(roomId, eventId) && realm.hasReadReceiptInLatestChunk(latestChunkEntity, roomId, userId, threadId)
}

private fun Realm.doesEventExistInChunkHistory(roomId: String, eventId: String): Boolean {
    return ChunkEntity.findIncludingEvent(this, roomId, eventId) != null
}

private fun Realm.hasReadReceiptInLatestChunk(latestChunkEntity: ChunkEntity, roomId: String, userId: String, threadId: String?): Boolean {
    return ReadReceiptEntity.where(this, roomId = roomId, userId = userId, threadId = threadId).findFirst()?.let {
        latestChunkEntity.timelineEvents.find(it.eventId)
    } != null
}

internal fun isReadMarkerMoreRecent(
        realmConfiguration: RealmConfiguration,
        roomId: String?,
        eventId: String?,
        dimber: Dimber? = null
): Boolean {
    if (roomId.isNullOrBlank() || eventId.isNullOrBlank()) {
        return false.also { dimber?.i { "isReadMarkerMoreRecent = false (roomId ${roomId.isNullOrBlank()} || eventId ${eventId.isNullOrBlank()}" } }
    }
    return Realm.getInstance(realmConfiguration).use { realm ->
        val eventToCheck = TimelineEventEntity.where(realm, roomId = roomId, eventId = eventId).findFirst()
        val eventToCheckChunk = eventToCheck?.chunk?.firstOrNull()
        val readMarker = ReadMarkerEntity.where(realm, roomId).findFirst() ?: return false
        val readMarkerEvent = TimelineEventEntity.where(realm, roomId = roomId, eventId = readMarker.eventId).findFirst()
        val readMarkerChunk = readMarkerEvent?.chunk?.firstOrNull()
        if (eventToCheckChunk == readMarkerChunk) {
            val readMarkerIndex = readMarkerEvent?.displayIndex ?: Int.MIN_VALUE
            val eventToCheckIndex = eventToCheck?.displayIndex ?: Int.MAX_VALUE
            dimber?.i { "isReadMarkerMoreRecent = ($eventToCheckIndex <= $readMarkerIndex)" }
            eventToCheckIndex <= readMarkerIndex
        } else {
            dimber?.i { "isReadMarkerMoreRecent = (non-null ${eventToCheckChunk!=null} && ${readMarkerChunk!=null} && ...)" }
            eventToCheckChunk != null && readMarkerChunk?.isMoreRecentThan(eventToCheckChunk, dimber) == true
        }
    }.also { dimber?.i { "isReadMarkerMoreRecent result $it" } }
}
internal fun isMarkedUnread(realmConfiguration: RealmConfiguration,
                            roomId: String?): Boolean {
    if (roomId.isNullOrBlank()) {
        return false
    }
    return Realm.getInstance(realmConfiguration).use { realm ->
        val roomSummary = RoomSummaryEntity.where(realm, roomId).findFirst()
        roomSummary?.markedUnread ?: false
    }
}
