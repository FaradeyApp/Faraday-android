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

package im.vector.app.features.home.room.detail.timeline.view

import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.ui.views.BubbleDependentView
import im.vector.app.features.home.room.detail.timeline.item.BaseEventItem
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout

interface TimelineMessageLayoutRenderer {
    fun <H: BaseEventItem.BaseHolder>renderMessageLayout(messageLayout: TimelineMessageLayout,
                                                         bubbleDependentView: BubbleDependentView<H>,
                                                         holder: H)

    // Variant to use from classes that do not use BaseEventItem.BaseHolder, and don't need the heavy bubble stuff
    fun <H: VectorEpoxyHolder>renderBaseMessageLayout(messageLayout: TimelineMessageLayout,
                                                      bubbleDependentView: BubbleDependentView<H>,
                                                      holder: H) {}
}

// Only render message layout for SC layouts - even if parent is not an ScBubble
fun <H: BaseEventItem.BaseHolder>TimelineMessageLayoutRenderer?.scOnlyRenderMessageLayout(messageLayout: TimelineMessageLayout,
                                                           bubbleDependentView: BubbleDependentView<H>,
                                                           holder: H) {
    if (messageLayout is TimelineMessageLayout.ScBubble) {
        scRenderMessageLayout(messageLayout, bubbleDependentView, holder)
    }
}

// Also render stub in case parent is no ScBubble
fun <H: BaseEventItem.BaseHolder>TimelineMessageLayoutRenderer?.scRenderMessageLayout(messageLayout: TimelineMessageLayout,
                                                                                    bubbleDependentView: BubbleDependentView<H>,
                                                                                    holder: H) {
    if (this == null) {
        renderStubMessageLayout(messageLayout, holder.viewStubContainer)
    } else {
        renderMessageLayout(messageLayout, bubbleDependentView, holder)
    }
}
