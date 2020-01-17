/*
 * Copyright 2019 New Vector Ltd
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

package im.vector.matrix.android.api.session.room.model

import im.vector.matrix.android.api.session.room.model.tag.RoomTag
import im.vector.matrix.android.api.session.room.send.UserDraft
import im.vector.matrix.android.api.session.room.timeline.TimelineEvent

/**
 * This class holds some data of a room.
 * It can be retrieved by [im.vector.matrix.android.api.session.room.Room] and [im.vector.matrix.android.api.session.room.RoomService]
 */
data class RoomSummary(
        val roomId: String,
        val displayName: String = "",
        val topic: String = "",
        val avatarUrl: String = "",
        val canonicalAlias: String? = null,
        val aliases: List<String> = emptyList(),
        val isDirect: Boolean = false,
        val joinedMembersCount: Int? = 0,
        val invitedMembersCount: Int? = 0,
        val latestPreviewableEvent: TimelineEvent? = null,
        val otherMemberIds: List<String> = emptyList(),
        val notificationCount: Int = 0,
        val highlightCount: Int = 0,
        val hasUnreadMessages: Boolean = false,
        val tags: List<RoomTag> = emptyList(),
        val membership: Membership = Membership.NONE,
        val versioningState: VersioningState = VersioningState.NONE,
        val readMarkerId: String? = null,
        val userDrafts: List<UserDraft> = emptyList(),
        var isEncrypted: Boolean,
        val typingRoomMemberIds: List<String> = emptyList()
) {

    val isVersioned: Boolean
        get() = versioningState != VersioningState.NONE

    val hasNewMessages: Boolean
        get() = notificationCount != 0
}
