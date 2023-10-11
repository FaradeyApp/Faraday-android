package im.vector.app.core.utils

import android.text.style.ClickableSpan
import android.widget.TextView
import me.saket.bettermovementmethod.BetterLinkMovementMethod
import timber.log.Timber

object SafeBetterLinkMovementMethod: BetterLinkMovementMethod() {

    override fun dispatchUrlClick(textView: TextView?, clickableSpan: ClickableSpan?) {
        try {
            super.dispatchUrlClick(textView, clickableSpan)
        } catch (e: StringIndexOutOfBoundsException) {
            Timber.w("BetterLinkMovement dispatchUrlClick StringIndexOutOfBoundsException $e")
            // Let Android handle this click.
            textView?.let {
                clickableSpan?.onClick(it)
            }
        }
    }

    override fun dispatchUrlLongClick(textView: TextView?, clickableSpan: ClickableSpan?) {
        try {
            super.dispatchUrlLongClick(textView, clickableSpan)
        } catch (e: StringIndexOutOfBoundsException) {
            Timber.w("BetterLinkMovement dispatchUrlLongClick StringIndexOutOfBoundsException $e")
            // Let Android handle this long click as a short-click.
            textView?.let {
                clickableSpan?.onClick(it)
            }
        }
    }
}
