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

package org.matrix.android.sdk.internal.database.model

import de.spiritcroc.matrixsdk.StaticScSdkHelper
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.crypto.model.RoomEncryptionTrustLevel
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.model.RoomJoinRules
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.room.model.RoomType
import org.matrix.android.sdk.api.session.room.model.VersioningState
import org.matrix.android.sdk.api.session.room.model.tag.RoomTag
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.internal.database.model.presence.UserPresenceEntity
import org.matrix.android.sdk.internal.session.room.membership.RoomName
import timber.log.Timber

internal open class RoomSummaryEntity(
        @PrimaryKey var roomId: String = "",
        var roomType: String? = null,
        var parents: RealmList<SpaceParentSummaryEntity> = RealmList(),
        var children: RealmList<SpaceChildSummaryEntity> = RealmList(),
        var directParentNames: RealmList<String> = RealmList(),
) : RealmObject() {

    private var displayName: String? = ""

    fun displayName() = displayName

    fun setDisplayName(roomName: RoomName) {
        if (roomName.name != displayName) {
            displayName = roomName.name
            normalizedDisplayName = roomName.normalizedName
        }
    }

    /**
     * Workaround for Realm only supporting Latin-1 character sets when sorting
     * or filtering by case
     * See https://github.com/realm/realm-core/issues/777
     */
    private var normalizedDisplayName: String? = ""

    var avatarUrl: String? = ""
        set(value) {
            if (value != field) field = value
        }
    var name: String? = ""
        set(value) {
            if (value != field) field = value
        }
    var topic: String? = ""
        set(value) {
            if (value != field) field = value
        }

    var latestPreviewableEvent: TimelineEventEntity? = null
        set(value) {
            if (value != field) field = value
        }

    var latestPreviewableContentEvent: TimelineEventEntity? = null
        set(value) {
            if (value != field) field = value
        }

    var latestPreviewableOriginalContentEvent: TimelineEventEntity? = null
        set(value) {
            if (value != field) field = value
        }

    @Index
    var lastActivityTime: Long? = null
        set(value) {
            if (value != field) field = value
        }

    var heroes: RealmList<String> = RealmList()

    var joinedMembersCount: Int? = 0
        set(value) {
            if (value != field) field = value
        }

    var invitedMembersCount: Int? = 0
        set(value) {
            if (value != field) field = value
        }

    @Index
    var isDirect: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    var directUserId: String? = null
        set(value) {
            if (value != field) field = value
        }

    var otherMemberIds: RealmList<String> = RealmList()

    var notificationCount: Int = 0
        set(value) {
            if (value != field) field = value
            updateTreatAsUnread()
        }

    var highlightCount: Int = 0
        set(value) {
            if (value != field) field = value
            updateTreatAsUnread()
        }

    var unreadCount: Int? = null
        set(value) {
            if (value != field) field = value
            updateTreatAsUnread()
        }
        /* -> safeUnreadCount
        get() {
            if (field == 0 && hasUnreadOriginalContentMessages) {
                return 1
            }
            return field
        }
         */

    // Keep in sync with RoomSummary.kt!
    fun safeUnreadCount(): Int {
        val safeUnreadCount = unreadCount
        return when {
            safeUnreadCount != null && safeUnreadCount > 0                 -> safeUnreadCount
            hasUnreadOriginalContentMessages && roomType != RoomType.SPACE -> 1
            else                                                           -> 0
        }
    }

    // Keep sync with RoomSummaryEntity.notificationCountOrMarkedUnread!
    fun notificationCountOrMarkedUnread(): Int {
        return when {
            notificationCount > 0 -> notificationCount
            markedUnread          -> 1
            else                  -> 0
        }
    }

    var aggregatedUnreadCount: Int = 0
        set(value) {
            if (value != field) field = value
        }

    var aggregatedNotificationCount: Int = 0
        set(value) {
            if (value != field) field = value
        }

    var threadNotificationCount: Int = 0
        set(value) {
            if (value != field) field = value
        }

    var threadHighlightCount: Int = 0
        set(value) {
            if (value != field) field = value
        }

    var readMarkerId: String? = null
        set(value) {
            if (value != field) field = value
        }

    var hasUnreadMessages: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    var hasUnreadContentMessages: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    var hasUnreadOriginalContentMessages: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    var markedUnread: Boolean = false
        set(value) {
            if (value != field) {
                field = value
                updateTreatAsUnread()
            }
        }

    /**
     * SchildiChat: Helper var so we can sort depending on how "unread" a chat is: mentions > {notifications, marked unread} > unreads > all read
     * Make sure to call `updateTreatAsUnread()` when necessary.
     */
    @Index
    private var treatAsUnreadLevel: Int = calculateUnreadLevel()
    private fun updateTreatAsUnread() {
        treatAsUnreadLevel = calculateUnreadLevel()
    }
    private fun calculateUnreadLevel(): Int {
        return calculateUnreadLevel(highlightCount, notificationCount, markedUnread, unreadCount)
    }

    private var tags: RealmList<RoomTagEntity> = RealmList()

    fun tags(): List<RoomTagEntity> = tags

    fun updateTags(newTags: List<Pair<String, Double?>>) {
        val toDelete = mutableListOf<RoomTagEntity>()
        tags.forEach { existingTag ->
            val updatedTag = newTags.firstOrNull { it.first == existingTag.tagName }
            if (updatedTag == null) {
                toDelete.add(existingTag)
            } else {
                existingTag.tagOrder = updatedTag.second
            }
        }
        toDelete.forEach { it.deleteFromRealm() }
        newTags.forEach { newTag ->
            if (tags.all { it.tagName != newTag.first }) {
                // we must add it
                tags.add(
                        RoomTagEntity(newTag.first, newTag.second)
                )
            }
        }

        isFavourite = newTags.any { it.first == RoomTag.ROOM_TAG_FAVOURITE }
        isLowPriority = newTags.any { it.first == RoomTag.ROOM_TAG_LOW_PRIORITY }
        isServerNotice = newTags.any { it.first == RoomTag.ROOM_TAG_SERVER_NOTICE }
    }

    @Index
    var isFavourite: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    @Index
    var isLowPriority: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    @Index
    var isServerNotice: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    var userDrafts: UserDraftsEntity? = null
        set(value) {
            if (value != field) field = value
        }

    var breadcrumbsIndex: Int = RoomSummary.NOT_IN_BREADCRUMBS
        set(value) {
            if (value != field) field = value
        }

    var canonicalAlias: String? = null
        set(value) {
            if (value != field) field = value
        }

    var aliases: RealmList<String> = RealmList()

    fun updateAliases(newAliases: List<String>) {
        // only update underlying field if there is a diff
        if (newAliases.distinct().sorted() != aliases.distinct().sorted()) {
            aliases.clear()
            aliases.addAll(newAliases)
            flatAliases = newAliases.joinToString(separator = "|", prefix = "|")
        }
    }

    // this is required for querying
    var flatAliases: String = ""

    var isEncrypted: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    var e2eAlgorithm: String? = null
        set(value) {
            if (value != field) field = value
        }

    var encryptionEventTs: Long? = 0
        set(value) {
            if (value != field) field = value
        }

    var roomEncryptionTrustLevelStr: String? = null
        set(value) {
            if (value != field) field = value
        }

    var inviterId: String? = null
        set(value) {
            if (value != field) field = value
        }

    var directUserPresence: UserPresenceEntity? = null
        set(value) {
            if (value != field) field = value
        }

    var hasFailedSending: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    var flattenParentIds: String? = null
        set(value) {
            if (value != field) field = value
        }

    /**
     * Whether the flattenParentIds thing is only non-empty because of DMs auto-added to spaces
     */
    var isOrphanDm: Boolean = false

    @Index
    private var membershipStr: String = Membership.NONE.name

    var membership: Membership
        get() {
            return Membership.valueOf(membershipStr)
        }
        set(value) {
            if (value.name != membershipStr) {
                membershipStr = value.name
            }
        }

    @Index
    var isHiddenFromUser: Boolean = false
        set(value) {
            if (value != field) field = value
        }

    @Index
    private var versioningStateStr: String = VersioningState.NONE.name
    var versioningState: VersioningState
        get() {
            return VersioningState.valueOf(versioningStateStr)
        }
        set(value) {
            if (value.name != versioningStateStr) {
                versioningStateStr = value.name
            }
        }

    private var joinRulesStr: String? = null
    var joinRules: RoomJoinRules?
        get() {
            return joinRulesStr?.let {
                tryOrNull { RoomJoinRules.valueOf(it) }
            }
        }
        set(value) {
            if (value?.name != joinRulesStr) {
                joinRulesStr = value?.name
            }
        }

    var roomEncryptionTrustLevel: RoomEncryptionTrustLevel?
        get() {
            return roomEncryptionTrustLevelStr?.let {
                try {
                    RoomEncryptionTrustLevel.valueOf(it)
                } catch (failure: Throwable) {
                    null
                }
            }
        }
        set(value) {
            if (value?.name != roomEncryptionTrustLevelStr) {
                roomEncryptionTrustLevelStr = value?.name
            }
        }

    companion object {
        private const val UNREAD_LEVEL_HIGHLIGHT = 4
        private const val UNREAD_LEVEL_NOTIFIED = 3
        private const val UNREAD_LEVEL_MARKED_UNREAD = UNREAD_LEVEL_NOTIFIED
        private const val UNREAD_LEVEL_SILENT_UNREAD = 1
        private const val UNREAD_LEVEL_NONE = 0

        fun calculateUnreadLevel(highlightCount: Int, notificationCount: Int, markedUnread: Boolean, unreadCount: Int?): Int {
            return when {
                highlightCount > 0 -> UNREAD_LEVEL_HIGHLIGHT
                notificationCount > 0 -> UNREAD_LEVEL_NOTIFIED
                markedUnread -> UNREAD_LEVEL_MARKED_UNREAD
                unreadCount?.let { it > 0 }.orFalse() -> UNREAD_LEVEL_SILENT_UNREAD
                else -> UNREAD_LEVEL_NONE
            }
        }
    }


    // Keep sync with RoomSummary.scHasUnreadMessages!
    fun scHasUnreadMessages(): Boolean {
        val preferenceProvider = StaticScSdkHelper.scSdkPreferenceProvider
        if (preferenceProvider == null) {
            // Fallback to default
            return hasUnreadOriginalContentMessages
        }
        return when(preferenceProvider.roomUnreadKind(isDirect)) {
            RoomSummary.UNREAD_KIND_ORIGINAL_CONTENT -> hasUnreadOriginalContentMessages
            RoomSummary.UNREAD_KIND_CONTENT          -> hasUnreadContentMessages
            RoomSummary.UNREAD_KIND_FULL             -> hasUnreadMessages
            else                                     -> hasUnreadOriginalContentMessages
        }
    }

    // Keep sync with RoomSummary.scLatestPreviewableEvent!
    fun scLatestPreviewableEvent(): TimelineEventEntity? {
        val preferenceProvider = StaticScSdkHelper.scSdkPreferenceProvider
        if (preferenceProvider == null) {
            // Fallback to default
            Timber.w("No preference provider set!")
            return latestPreviewableOriginalContentEvent
        }
        return when(preferenceProvider.roomUnreadKind(isDirect)) {
            RoomSummary.UNREAD_KIND_ORIGINAL_CONTENT -> latestPreviewableOriginalContentEvent
            RoomSummary.UNREAD_KIND_CONTENT          -> latestPreviewableContentEvent
            RoomSummary.UNREAD_KIND_FULL             -> latestPreviewableEvent
            else                                     -> latestPreviewableOriginalContentEvent
        }
    }
}
