package de.spiritcroc.recyclerview.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * A recyclerview to use with BetterLinearLayoutManager.
 * Exposes some things that androidx' LinearLayoutManager would use but is package-private in androidx' RecyclerView.
 */
class BLLRecyclerView : RecyclerView {
    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        (layoutManager as? BetterLinearLayoutManager)?.setPreviousMeasure(measuredWidth, measuredHeight)
    }

    override fun setLayoutManager(layout: LayoutManager?) {
        super.setLayoutManager(layout)
        (layoutManager as? BetterLinearLayoutManager)?.setIsBLLRecyclerView()
    }
}
