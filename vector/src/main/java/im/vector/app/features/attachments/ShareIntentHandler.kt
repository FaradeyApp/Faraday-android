/*
 * Copyright (c) 2022 New Vector Ltd
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

package im.vector.app.features.attachments

import android.content.Context
import android.content.Intent
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import javax.inject.Inject

class ShareIntentHandler @Inject constructor(
        private val multiPickerIncomingFiles: MultiPickerIncomingFiles,
) {

    /**
     * This methods aims to handle incoming share intents.
     *
     * @return true if it can handle the intent data, false otherwise
     */
    fun handleIncomingShareIntent(context: Context, intent: Intent, onFile: (List<ContentAttachmentData>) -> Unit, onPlainText: (String) -> Unit): Boolean {
        val type = intent.resolveType(context) ?: return false
        return when {
            type == "text/plain" -> handlePlainText(intent, onPlainText)
            type.startsWith("image") -> onFile(multiPickerIncomingFiles.image(intent)).let { true }
            type.startsWith("video") -> onFile(multiPickerIncomingFiles.video(intent)).let { true }
            type.startsWith("audio") -> onFile(multiPickerIncomingFiles.audio(intent)).let { true }
            type.startsWith("application") || type.startsWith("file") || type.startsWith("text") || type.startsWith("*") -> {
                onFile(multiPickerIncomingFiles.file(intent)).let { true }
            }
            else -> false
        }
    }

    private fun handlePlainText(intent: Intent, onPlainText: (String) -> Unit): Boolean {
        val content = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        return if (content?.isNotEmpty() == true) {
            onPlainText(content)
            true
        } else {
            false
        }
    }
}
