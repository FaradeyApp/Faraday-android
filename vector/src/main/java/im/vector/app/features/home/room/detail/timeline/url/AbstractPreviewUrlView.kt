package im.vector.app.features.home.room.detail.timeline.url

import android.view.View
import androidx.core.view.isVisible
import im.vector.app.features.home.room.detail.timeline.TimelineEventController
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.media.ImageContentRenderer

interface AbstractPreviewUrlView {
    var isVisible: Boolean
        get() = (this as View).isVisible
        set(value) { (this as View).isVisible = value }

    var delegate: TimelineEventController.PreviewUrlCallback?
    fun render(newState: PreviewUrlUiState,
               imageContentRenderer: ImageContentRenderer,
               force: Boolean = false)

    // Like upstream TimelineMessageLayoutRenderer, not like downstream one, so don't inherit
    fun renderMessageLayout(messageLayout: TimelineMessageLayout)
}
