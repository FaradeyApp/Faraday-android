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

package im.vector.app.features.home.room.detail.timeline.style

import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.themes.BubbleThemeUtils
import javax.inject.Inject

class TimelineLayoutSettingsProvider @Inject constructor(/*private val vectorPreferences: VectorPreferences,*/ private val bubbleThemeUtils: BubbleThemeUtils) {

    fun getLayoutSettings(): TimelineLayoutSettings {
        return when (bubbleThemeUtils.getBubbleStyle()) {
            BubbleThemeUtils.BUBBLE_STYLE_NONE -> TimelineLayoutSettings.MODERN
            BubbleThemeUtils.BUBBLE_STYLE_ELEMENT -> TimelineLayoutSettings.BUBBLE
            else -> TimelineLayoutSettings.SC_BUBBLE
        }
        /*
        return if (vectorPreferences.useMessageBubblesLayout()) {
            TimelineLayoutSettings.BUBBLE
        } else {
            TimelineLayoutSettings.MODERN
        }
         */
    }
}
