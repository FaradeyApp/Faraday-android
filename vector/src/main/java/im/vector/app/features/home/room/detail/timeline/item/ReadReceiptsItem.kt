/*
 * Copyright (c) 2021 New Vector Ltd
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

package im.vector.app.features.home.room.detail.timeline.item

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.resources.DefaultLocaleProvider
import im.vector.app.core.resources.LocaleProvider
import im.vector.app.core.resources.getLayoutDirectionFromCurrentLocale
import im.vector.app.core.ui.views.BubbleDependentView
import im.vector.app.core.ui.views.ReadReceiptsView
import im.vector.app.features.home.AvatarRenderer
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.home.room.detail.timeline.view.ScMessageBubbleWrapView
import im.vector.app.features.home.room.detail.timeline.view.setFlatRtl

@EpoxyModelClass
abstract class ReadReceiptsItem : VectorEpoxyModel<ReadReceiptsItem.Holder>(R.layout.item_timeline_event_read_receipts), ItemWithEvents, BubbleDependentView<ReadReceiptsItem.Holder> {

    @EpoxyAttribute lateinit var eventId: String
    @EpoxyAttribute lateinit var readReceipts: List<ReadReceiptData>
    @EpoxyAttribute lateinit var messageLayout: TimelineMessageLayout
    @EpoxyAttribute var shouldHideReadReceipts: Boolean = false
    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash) lateinit var avatarRenderer: AvatarRenderer
    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash) lateinit var clickListener: ClickListener
    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash) lateinit var localeProvider: LocaleProvider

    override fun canAppendReadMarker(): Boolean = false

    override fun getEventIds(): List<String> = listOf(eventId)

    override fun bind(holder: Holder) {
        super.bind(holder)
        holder.readReceiptsView.onClick(clickListener)
        holder.readReceiptsView.render(readReceipts, avatarRenderer)

        (messageLayout as? TimelineMessageLayout.ScBubble)?.let { applyScBubbleStyle(it, holder) }

        holder.readReceiptsView.isVisible = !shouldHideReadReceipts
    }

    override fun unbind(holder: Holder) {
        holder.readReceiptsView.unbind(avatarRenderer)
        super.unbind(holder)
    }

    override fun applyScBubbleStyle(messageLayout: TimelineMessageLayout.ScBubble, holder: Holder) {
        val defaultDirection = localeProvider.getLayoutDirectionFromCurrentLocale()
        val defaultRtl = defaultDirection == View.LAYOUT_DIRECTION_RTL
        val reverseDirection = if (defaultRtl) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL

        /*
        val receiptParent = holder.readReceiptsView.parent
        if (receiptParent is LinearLayout) {
            (holder.readReceiptsView.layoutParams as LinearLayout.LayoutParams).gravity = if (dualBubbles) Gravity.START else Gravity.END

            (receiptParent.layoutParams as RelativeLayout.LayoutParams).removeRule(RelativeLayout.END_OF)
            (receiptParent.layoutParams as RelativeLayout.LayoutParams).removeRule(RelativeLayout.ALIGN_PARENT_START)
            if (dualBubbles) {
                (receiptParent.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
            } else {
                (receiptParent.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.END_OF, R.id.messageStartGuideline)
            }
        } else if (receiptParent is RelativeLayout) {
            if (dualBubbles) {
                (holder.readReceiptsView.layoutParams as RelativeLayout.LayoutParams).removeRule(RelativeLayout.ALIGN_PARENT_END)
            } else {
                (holder.readReceiptsView.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ALIGN_PARENT_END)
            }
        } else if (receiptParent is FrameLayout) {
         */
        if (messageLayout.singleSidedLayout) {
            (holder.readReceiptsView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.END
        } else {
            (holder.readReceiptsView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.START
        }
        /*
        } else {
            Timber.e("Unsupported layout for read receipts parent: $receiptParent")
        }
         */

        // Also set rtl to have members fill from the natural side
        setFlatRtl(holder.readReceiptsView, if (messageLayout.singleSidedLayout) defaultDirection else reverseDirection, defaultDirection)
    }

    class Holder : VectorEpoxyHolder() {
        val readReceiptsView by bind<ReadReceiptsView>(R.id.readReceiptsView)
    }

}
