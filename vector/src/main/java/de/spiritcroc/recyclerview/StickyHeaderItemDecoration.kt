package de.spiritcroc.recyclerview

/**
 * Source: https://gist.github.com/jonasbark/f1e1373705cfe8f6a7036763f7326f7c
 * Modified to
 * - make isHeader() abstract, so we don't need a predefined list of header ids
 * - not require EpoxyRecyclerView
 * - work with reverse layouts
 * - hide the currently overlaid header for a smoother animation without duplicate headers
 * - ...
 */

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyViewHolder
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.extensions.orTrue
import org.matrix.android.sdk.api.extensions.tryOrNull
import kotlin.math.abs

const val FADE_DURATION = 200

abstract class StickyHeaderItemDecoration(
    private val epoxyController: EpoxyController,
    private val reverse: Boolean = false
) : RecyclerView.ItemDecoration() {
    private var mStickyHeaderHeight: Int = 0

    private var lastHeaderPos: Int? = null
    private var lastFadeState: FadeState? = null
    private var floatingHeaderEnabled: Boolean = true

    data class FadeState(
            val headerPos: Int,
            val headerView: View,
            val animatedView: View,
            var shouldBeVisible: Boolean = true,
            var animation: ViewPropertyAnimator? = null
    )
    // An extra header view we still draw while it's fading out
    private var oldFadingView: View? = null

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        if (parent.childCount == 0) {
            return
        }
        if (!parent.canScrollVertically(1) && !parent.canScrollVertically(-1)) {
            // No floating header needs if we cannot scroll, i.e. all headers are already visible
            return
        }
        val topChild = if (reverse) {
            parent.getChildAt(parent.childCount - 1) ?: return
        } else {
            parent.getChildAt(0) ?: return
        }

        val topChildPosition = parent.getChildAdapterPosition(topChild)
        if (topChildPosition == RecyclerView.NO_POSITION) {
            return
        }

        val headerPos = getHeaderPositionForItem(topChildPosition)
        if (headerPos != RecyclerView.NO_POSITION) {
            val oldFadeState = lastFadeState
            val newFadeState: FadeState = if (headerPos == oldFadeState?.headerPos) {
                oldFadeState.copy()
            } else {
                val currentHeaderHolder = getHeaderViewHolderForItem(headerPos, parent)
                oldFadeState?.let { onDeleteFadeState(it) }
                FadeState(headerPos, currentHeaderHolder.itemView, getViewForFadeAnimation(currentHeaderHolder))
            }
            val currentHeader = newFadeState.headerView
            fixLayoutSize(parent, currentHeader)
            val contactPoint = currentHeader.bottom

            val childInContact = getChildInContact(parent, contactPoint, headerPos)
            val childBelow = getChildInContact(parent, currentHeader.top, headerPos)
            val childInContactModel = childInContact?.let { tryOrNull { epoxyController.adapter.getModelAtPosition(parent.getChildAdapterPosition(childInContact)) } }
            val childBelowModel = childBelow?.let { tryOrNull { epoxyController.adapter.getModelAtPosition(parent.getChildAdapterPosition(childBelow)) } }

            val shouldBeVisible = if (childInContact != null) {
                if (isHeader(childInContactModel)) {
                    updateOverlaidHeaders(parent, headerPos)
                    newFadeState.shouldBeVisible = floatingHeaderEnabled
                    updateFadeAnimation(newFadeState)
                    moveHeader(c, currentHeader, childInContact)
                    maybeInvalidateDraw(parent)
                    return
                }
                !(preventOverlay(childInContactModel) || preventOverlay(childBelowModel))
            } else {
                // Header unhide
                true
            } && floatingHeaderEnabled

            newFadeState.shouldBeVisible = shouldBeVisible

            val overlaidHeaderPos: Int? = if (!shouldBeVisible ||
                    // Un-hide views early, so we don't get flashing headers while scrolling
                    (childBelow != childInContact &&
                    childBelow != null &&
                    isHeader(childBelowModel) &&
                    contactPoint - childBelow.bottom < (childBelow.bottom - childBelow.top)/8)
            ) {
                null
            } else {
                headerPos
            }

            updateOverlaidHeaders(parent, overlaidHeaderPos)
            updateFadeAnimation(newFadeState)
            drawHeader(c, currentHeader)
        } else {
            // Show hidden header again
            updateOverlaidHeaders(parent, null)
        }

        // Fade out an old header view
        oldFadingView?.let {
            if (it.alpha == 0.0f) {
                oldFadingView = null
            } else {
                drawHeader(c, it)
            }
        }

        maybeInvalidateDraw(parent)
    }

    private fun maybeInvalidateDraw(parent: RecyclerView) {
        // Keep invalidating while we are animating, so we can draw animation updates
        if (oldFadingView != null || !lastFadeState?.hasTargetAlpha().orTrue()) {
            parent.invalidate()
        }
    }

    private fun onDeleteFadeState(oldState: FadeState) {
        oldState.animation?.cancel()
        oldState.animatedView.alpha = 1.0f
    }

    private fun updateFadeAnimation(newState: FadeState) {
        val oldState = lastFadeState
        if (oldState == null) {
            newState.applyAlphaImmediate()
        } else if (oldState.headerPos == newState.headerPos) {
            if (oldState.shouldBeVisible != newState.shouldBeVisible) {
                newState.startAlphaAnimation()
            }
        } else {
            if (!oldState.shouldBeVisible && oldState.animatedView.alpha != 1.0f) {
                // Keep drawing for soft fade out
                oldFadingView = oldState.animatedView
            }
            newState.applyAlphaImmediate()
        }
        lastFadeState = newState
    }

    private fun FadeState.targetAlpha(): Float {
        return if (shouldBeVisible) 1.0f else 0.0f
    }

    private fun FadeState.hasTargetAlpha(): Boolean {
        return animatedView.alpha == targetAlpha()
    }

    private fun FadeState.applyAlphaImmediate() {
        animation?.cancel()
        animation = null
        animatedView.alpha = targetAlpha()
    }

    private fun FadeState.startAlphaAnimation() {
        animation?.cancel()
        val targetAlpha = targetAlpha()
        val currentAlpha = animatedView.alpha
        val remainingAlpha = abs(targetAlpha - currentAlpha)
        // Shorter duration if we just aborted a different fade animation, thus leaving us with less necessary alpha changes
        val duration = (remainingAlpha * FADE_DURATION).toLong()
        animation = animatedView.animate().alpha(targetAlpha()).setDuration(duration).apply {
            start()
        }
    }

    private fun updateOverlaidHeaders(parent: RecyclerView, headerPos: Int?) {
        if (lastHeaderPos != headerPos) {
            // Show hidden header again
            lastHeaderPos?.let {
                updateOverlaidHeader(parent, it, false)
            }
            // Remember new hidden header
            lastHeaderPos = if (headerPos?.let { updateOverlaidHeader(parent, it, true) }.orFalse()) {
                headerPos
            } else {
                null
            }
        }
    }

    /**
     * Return true if successfully updated the view.
     * Note: this has some issues when invisible views get recycled, better override this in subclasses.
     */
    open fun updateOverlaidHeader(parent: RecyclerView, position: Int, isCurrentlyOverlaid: Boolean): Boolean {
        val view = parent.findViewHolderForAdapterPosition(position)?.itemView
        if (view != null) {
            view.isVisible = !isCurrentlyOverlaid
            return true
        }
        return false
    }

    open fun getHeaderViewHolderForItem(headerPosition: Int, parent: RecyclerView): EpoxyViewHolder {
        val viewHolder = epoxyController.adapter.onCreateViewHolder(
            parent,
            epoxyController.adapter.getItemViewType(headerPosition)
        )
        epoxyController.adapter.onBindViewHolder(viewHolder, headerPosition)
        return viewHolder
    }

    open fun getViewForFadeAnimation(holder: EpoxyViewHolder): View {
        return holder.itemView
    }

    private fun drawHeader(c: Canvas, header: View) {
        c.save()
        c.translate(0f, 0f)
        header.draw(c)
        c.restore()
    }

    private fun moveHeader(c: Canvas, currentHeader: View, nextHeader: View) {
        c.save()
        c.translate(0f, (nextHeader.top - currentHeader.height).toFloat())
        currentHeader.draw(c)
        c.restore()
    }

    abstract fun isHeader(model: EpoxyModel<*>?): Boolean

    open fun preventOverlay(model: EpoxyModel<*>?): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    private fun getChildInContact(parent: RecyclerView, contactPoint: Int, currentHeaderPos: Int): View? {
        var childInContact: View? = null
        for (i in 0 until parent.childCount) {
            //var heightTolerance = 0
            val child = parent.getChildAt(i)

            //measure height tolerance with child if child is another header
            // Note: seems to be always 0 for us
            /*
            if (currentHeaderPos != i) {
                val isChildHeader = isHeader(tryOrNull { epoxyController.adapter.getModelAtPosition(parent.getChildAdapterPosition(child)) } )
                if (isChildHeader) {
                    heightTolerance = mStickyHeaderHeight - child.height
                }
            }

            //add heightTolerance if child top be in display area
            val childBottomPosition = if (child.top > 0) {
                child.bottom + heightTolerance
            } else {
                child.bottom
            }
             */
            val childBottomPosition = child.bottom

            if (childBottomPosition > contactPoint) {
                if (child.top <= contactPoint) {
                    // This child overlaps the contactPoint
                    childInContact = child
                    break
                }
            }
        }
        return childInContact
    }


    /**
     * This method gets called by [StickyHeaderItemDecoration] to fetch the position of the header item in the adapter
     * that is used for (represents) item at specified position.
     * @param itemPosition int. Adapter's position of the item for which to do the search of the position of the header item.
     * @return int. Position of the header item in the adapter.
     */
    private fun getHeaderPositionForItem(itemPosition: Int): Int {
        var tempPosition = itemPosition
        var headerPosition = RecyclerView.NO_POSITION
        val directionAdd = if (reverse) 1 else -1
        do {
            if (isHeader(epoxyController.adapter.getModelAtPosition(tempPosition))) {
                headerPosition = tempPosition
                break
            }
            tempPosition += directionAdd
        } while (tempPosition >= -1 && tempPosition < epoxyController.adapter.itemCount)
        return headerPosition
    }

    /**
     * Properly measures and layouts the top sticky header.
     * @param parent ViewGroup: RecyclerView in this case.
     */
    private fun fixLayoutSize(parent: ViewGroup, view: View) {

        // Specs for parent (RecyclerView)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        // Specs for children (headers)
        val childWidthSpec =
            ViewGroup.getChildMeasureSpec(widthSpec, parent.paddingLeft + parent.paddingRight, view.layoutParams.width)
        val childHeightSpec = ViewGroup.getChildMeasureSpec(
            heightSpec,
            parent.paddingTop + parent.paddingBottom,
            view.layoutParams.height
        )

        view.measure(childWidthSpec, childHeightSpec)

        mStickyHeaderHeight = view.measuredHeight

        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    fun setFloatingDateEnabled(recyclerView: RecyclerView, enabled: Boolean) {
        if (floatingHeaderEnabled != enabled) {
            floatingHeaderEnabled = enabled
            recyclerView.invalidate()
        }
    }

}
