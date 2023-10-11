/*
 * Copyright (c) 2020 New Vector Ltd
 * Copyright (c) 2022 Beeper Inc.
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

package im.vector.app.features.home.room.detail.timeline.reply

import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent

/**
 * The state representing a reply preview UI state for an Event.
 */
sealed class PreviewReplyUiState {

    abstract val repliedToEventId: String?

    // This is not a reply
    object NoReply : PreviewReplyUiState() {
        override val repliedToEventId: String? = null
    }

    // Error
    data class Error(val throwable: Throwable, override val repliedToEventId: String?) : PreviewReplyUiState()

    data class ReplyLoading(override val repliedToEventId: String?) : PreviewReplyUiState()

    // Is a reply
    data class InReplyTo(
            override val repliedToEventId: String,
            val event: TimelineEvent,
            val senderName: String,
    ) : PreviewReplyUiState()
}
