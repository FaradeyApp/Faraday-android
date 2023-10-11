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

package im.vector.lib.core.utils.epoxy.charsequence

import android.text.TextUtils

/**
 * Extensions to wrap CharSequence to EpoxyCharSequence.
 */
fun CharSequence.toEpoxyCharSequence() = EpoxyCharSequence(this)

fun CharSequence.toMessageTextEpoxyCharSequence(): EpoxyCharSequence {
    var m = this
    if (m.isNotEmpty()) {
        // Remove last trailing newline: looks especially bad in message bubble
        if (m.last() == '\n') {
            m = m.subSequence(0, m.length-1)
        }
        // Add a narrow non-breakable space to work around wrap_content cutting italic text | https://stackoverflow.com/questions/4353836/italic-textview-with-wrap-contents-seems-to-clip-the-text-at-right-edge
        // (interestingly, this seems to be only relevant for the last character even for multi-line messages)
        m = TextUtils.concat(m, "\u202f")
    }
    return m.toEpoxyCharSequence()
}
