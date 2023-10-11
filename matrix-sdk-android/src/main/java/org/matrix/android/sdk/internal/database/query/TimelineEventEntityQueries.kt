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

import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import io.realm.kotlin.where
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.room.send.SendState
import org.matrix.android.sdk.api.session.room.timeline.TimelineEventFilters
import org.matrix.android.sdk.internal.database.model.ChunkEntity
import org.matrix.android.sdk.internal.database.model.RoomEntity
import org.matrix.android.sdk.internal.database.model.TimelineEventEntity
import org.matrix.android.sdk.internal.database.model.TimelineEventEntityFields

internal fun TimelineEventEntity.Companion.where(realm: Realm): RealmQuery<TimelineEventEntity> {
    return realm.where()
}

internal fun TimelineEventEntity.Companion.where(
        realm: Realm,
        roomId: String,
        eventId: String
): RealmQuery<TimelineEventEntity> {
    return where(realm)
            .equalTo(TimelineEventEntityFields.ROOM_ID, roomId)
            .equalTo(TimelineEventEntityFields.EVENT_ID, eventId)
}

internal fun TimelineEventEntity.Companion.where(
        realm: Realm,
        roomId: String,
        eventIds: List<String>
): RealmQuery<TimelineEventEntity> {
    return where(realm)
            .equalTo(TimelineEventEntityFields.ROOM_ID, roomId)
            .`in`(TimelineEventEntityFields.EVENT_ID, eventIds.toTypedArray())
}

internal fun TimelineEventEntity.Companion.whereRoomId(
        realm: Realm,
        roomId: String
): RealmQuery<TimelineEventEntity> {
    return where(realm)
            .equalTo(TimelineEventEntityFields.ROOM_ID, roomId)
}

internal fun TimelineEventEntity.Companion.findWithSenderMembershipEvent(
        realm: Realm,
        senderMembershipEventId: String
): List<TimelineEventEntity> {
    return where(realm)
            .equalTo(TimelineEventEntityFields.SENDER_MEMBERSHIP_EVENT_ID, senderMembershipEventId)
            .findAll()
}

internal fun TimelineEventEntity.Companion.latestEvent(
        realm: Realm,
        roomId: String,
        includesSending: Boolean,
        filters: TimelineEventFilters = TimelineEventFilters()
): TimelineEventEntity? {
    val roomEntity = RoomEntity.where(realm, roomId).findFirst() ?: return null
    val sendingTimelineEvents = roomEntity.sendingTimelineEvents.where().filterEvents(filters)

    val liveEvents = ChunkEntity.findLastForwardChunkOfRoom(realm, roomId)?.timelineEvents?.where()?.filterEvents(filters)
    val query = if (includesSending && sendingTimelineEvents.findAll().isNotEmpty()) {
        sendingTimelineEvents
    } else {
        liveEvents
    }
    return query
            ?.sort(TimelineEventEntityFields.DISPLAY_INDEX, Sort.DESCENDING)
            ?.findFirst()
}

/**
 * Return <Event, Boolean>, with:
 * - Event: best event we could find to generate room timestamps
 * - Boolean: true if Event is previewable, else false
 */
internal fun TimelineEventEntity.Companion.bestTimestampPreviewEvent(realm: Realm,
                                                                    roomId: String,
                                                                    filters: TimelineEventFilters = TimelineEventFilters(),
                                                                    chunk: ChunkEntity? = null,
                                                                    maxChunksToVisit: Int = 10): Pair<TimelineEventEntity, Boolean>? {
    val roomEntity = RoomEntity.where(realm, roomId).findFirst() ?: return null

    // First try currently sending events, later recurse and try chunks
    val query = if (chunk == null) {
        roomEntity.sendingTimelineEvents.where().filterEvents(filters)
    } else {
        chunk.timelineEvents.where()?.filterEvents(filters)
    }
    val filteredResult = query
            ?.sort(TimelineEventEntityFields.DISPLAY_INDEX, Sort.DESCENDING)
            ?.findFirst()
    if (filteredResult != null) {
        return Pair(filteredResult, true)
    }
    var recursiveResult: Pair<TimelineEventEntity, Boolean>? = null
    // One recursion step more than maxChunksToVisit, since in first step, we don't visit any chunk, but only sendingTimelineEvents
    if (maxChunksToVisit > 0 || chunk == null) {
        if (chunk == null) {
            // Initial chunk recursion
            val latestChunk = ChunkEntity.findLastForwardChunkOfRoom(realm, roomId)
            if (latestChunk != null) {
                recursiveResult = bestTimestampPreviewEvent(realm, roomId, filters, latestChunk, maxChunksToVisit - 1)
            }
        } else {
            val prevChunk = chunk.prevChunk
            if (prevChunk != null) {
                recursiveResult = bestTimestampPreviewEvent(realm, roomId, filters, prevChunk, maxChunksToVisit - 1)
            }
        }
    }
    if (recursiveResult != null) {
        return recursiveResult
    }
    // If we haven't found any previewable event by now, fall back to the oldest non-previewable event we found
    return chunk?.timelineEvents?.where()?.sort(TimelineEventEntityFields.DISPLAY_INDEX, Sort.ASCENDING)?.findFirst()?.let {
        Pair(it, false)
    }
}

internal fun Pair<TimelineEventEntity, Boolean>?.previewable(): TimelineEventEntity? {
    return if (this == null || !second) {
        null
    } else {
        first
    }
}

internal fun RealmQuery<TimelineEventEntity>.filterEvents(filters: TimelineEventFilters): RealmQuery<TimelineEventEntity> {
    if (filters.filterTypes && filters.allowedTypes.isNotEmpty()) {
        beginGroup()
        filters.allowedTypes.forEachIndexed { index, filter ->
            if (filter.eventType == EventType.ENCRYPTED) {
                val otherTypes = filters.allowedTypes.minus(filter).map { it.eventType }
                if (filter.stateKey == null) {
                    filterEncryptedTypes(otherTypes)
                } else {
                    beginGroup()
                    filterEncryptedTypes(otherTypes)
                    and()
                    equalTo(TimelineEventEntityFields.ROOT.STATE_KEY, filter.stateKey)
                    endGroup()
                }
            } else {
                if (filter.stateKey == null) {
                    equalTo(TimelineEventEntityFields.ROOT.TYPE, filter.eventType)
                } else {
                    beginGroup()
                    equalTo(TimelineEventEntityFields.ROOT.TYPE, filter.eventType)
                    and()
                    equalTo(TimelineEventEntityFields.ROOT.STATE_KEY, filter.stateKey)
                    endGroup()
                }
            }
            if (index != filters.allowedTypes.size - 1) {
                or()
            }
        }
        endGroup()
    }
    if (filters.filterUseless) {
        not().equalTo(TimelineEventEntityFields.ROOT.IS_USELESS, true)
    }
    if (filters.filterEdits) {
        not().like(TimelineEventEntityFields.ROOT.CONTENT, TimelineEventFilter.Content.EDIT)
        not().like(TimelineEventEntityFields.ROOT.CONTENT, TimelineEventFilter.Content.RESPONSE)
    }
    if (filters.filterRedacted) {
        not().like(TimelineEventEntityFields.ROOT.UNSIGNED_DATA, TimelineEventFilter.Unsigned.REDACTED)
    }

    return this
}

internal fun RealmQuery<TimelineEventEntity>.filterEncryptedTypes(allowedTypes: List<String>): RealmQuery<TimelineEventEntity> {
    beginGroup()
    equalTo(TimelineEventEntityFields.ROOT.TYPE, EventType.ENCRYPTED)
    and()
    beginGroup()
    isNull(TimelineEventEntityFields.ROOT.DECRYPTION_RESULT_JSON)
    allowedTypes.forEach { eventType ->
        or()
        like(TimelineEventEntityFields.ROOT.DECRYPTION_RESULT_JSON, TimelineEventFilter.DecryptedContent.type(eventType))
    }
    endGroup()
    endGroup()
    return this
}

internal fun RealmQuery<TimelineEventEntity>.filterTypes(filterTypes: List<String>): RealmQuery<TimelineEventEntity> {
    return if (filterTypes.isEmpty()) {
        this
    } else {
        `in`(TimelineEventEntityFields.ROOT.TYPE, filterTypes.toTypedArray())
    }
}

internal fun RealmList<TimelineEventEntity>.find(eventId: String): TimelineEventEntity? {
    return where()
            .equalTo(TimelineEventEntityFields.EVENT_ID, eventId)
            .findFirst()
}

internal fun TimelineEventEntity.Companion.findAllInRoomWithSendStates(
        realm: Realm,
        roomId: String,
        sendStates: List<SendState>
): RealmResults<TimelineEventEntity> {
    return whereRoomId(realm, roomId)
            .filterSendStates(sendStates)
            .findAll()
}

internal fun RealmQuery<TimelineEventEntity>.filterSendStates(sendStates: List<SendState>): RealmQuery<TimelineEventEntity> {
    val sendStatesStr = sendStates.map { it.name }.toTypedArray()
    return `in`(TimelineEventEntityFields.ROOT.SEND_STATE_STR, sendStatesStr)
}

/**
 * Find all TimelineEventEntity items where sender is in senderIds collection, excluding state events.
 */
internal fun TimelineEventEntity.Companion.findAllFrom(
        realm: Realm,
        senderIds: Collection<String>
): RealmResults<TimelineEventEntity> {
    return where(realm)
            .`in`(TimelineEventEntityFields.ROOT.SENDER, senderIds.toTypedArray())
            .isNull(TimelineEventEntityFields.ROOT.STATE_KEY)
            .findAll()
}
