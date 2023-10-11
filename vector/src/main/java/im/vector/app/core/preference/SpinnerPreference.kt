/*
 * Copyright (c) 2023 New Vector Ltd
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

package im.vector.app.core.preference

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RelativeLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import im.vector.app.R
import org.matrix.android.sdk.api.util.ProxyType

class SpinnerPreference : Preference {
    private var mItems: List<String?> = ArrayList()
    private var mValue: String? = null
    private val adapter: ArrayAdapter<String> by lazy {
        object : ArrayAdapter<String>(context, R.layout.item_spinner_text, mItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var view = convertView
                if (view == null) {
                    val inflater = LayoutInflater.from(context)
                    view = inflater.inflate(R.layout.item_spinner_text, parent, false)
                }
                val tv = view!!.findViewById<TextView>(android.R.id.text1)
                tv.text = getItem(position)
                if (mTextColor != 0) tv.setTextColor(mTextColor)
                return view
            }
        }
    }
    private var mListener: AdapterView.OnItemSelectedListener? = null
    private var mAllCaps = false
    private var mVisibility = 0
    private var mBackgroundColor = 0
    private var mTextColor = 0
    private var mSpinnerMode = 0

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
            context, attrs, defStyleAttr
    ) {
        init(context, attrs)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val itemView = holder.itemView
        val container: RelativeLayout? = itemView as RelativeLayout?
        container?.visibility = visibility
        if (mBackgroundColor != 0) container?.setBackgroundColor(mBackgroundColor)
        val textView = itemView.findViewById<TextView>(R.id.spinner_title)
        textView.text = title
        textView.isAllCaps = mAllCaps
        if (mTextColor != 0) textView.setTextColor(mTextColor)
        val spinner = createSpinner(container)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            spinner.layoutMode = mSpinnerMode
        }
        spinner.adapter = adapter
        val selection = mItems.indexOf(mValue)
        if (selection >= 0) spinner.setSelection(selection)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                setValue(mItems[position])
                mListener?.onItemSelected(parent, view, position, id)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                mListener?.onNothingSelected(parent)
            }
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index)!!
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        defaultValue?.let {
            setValue(defaultValue as String)
        }
    }

    fun setItems(items: List<String?>) {
        mItems = items
        notifyChanged()
    }

    fun setOnItemSelectedListener(listener: AdapterView.OnItemSelectedListener) {
        mListener = listener
    }

    fun setAllCaps(allCaps: Boolean) {
        mAllCaps = allCaps
    }

    fun setLayoutBackgroundColor(@ColorRes color: Int) {
        mBackgroundColor = ContextCompat.getColor(context, color)
    }

    fun setLayoutBackgroundColor(color: String?) {
        mBackgroundColor = Color.parseColor(color)
    }

    fun setTextColor(@ColorRes color: Int) {
        mTextColor = ContextCompat.getColor(context, color)
    }

    fun setTextColor(color: String?) {
        mTextColor = Color.parseColor(color)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        layoutResource = R.layout.vector_preference_spinner
        val array = context.obtainStyledAttributes(attrs, R.styleable.SpinnerPreference)
        val id = array.getResourceId(R.styleable.SpinnerPreference_spinnerValues, 0)
        val backgroundColor =
                array.getColor(R.styleable.SpinnerPreference_preferenceLayoutColor, Color.WHITE)
        val textColor = array.getColor(R.styleable.SpinnerPreference_preferenceTextColor, 0)
        mSpinnerMode = array.getInt(R.styleable.SpinnerPreference_spinnerMode, 1)
        mAllCaps = array.getBoolean(R.styleable.SpinnerPreference_preferenceAllCaps, true)
        mVisibility = array.getInt(R.styleable.SpinnerPreference_preferenceVisibility, 0)
        if (id != 0) {
            if (mItems.isEmpty()) {
                mItems = listOf(*context.resources.getStringArray(id))
            }
        }
        if (backgroundColor != Color.WHITE) {
            mBackgroundColor = backgroundColor
        }
        if (textColor != 0) {
            mTextColor = textColor
        }
        array.recycle()
    }

    private fun createSpinner(container: RelativeLayout?): Spinner {
        val spinner = Spinner(context, mSpinnerMode)
        val params = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_END)
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        params.addRule(RelativeLayout.END_OF, R.id.spinner_title)
        params.addRule(RelativeLayout.RIGHT_OF, R.id.spinner_title)
        params.addRule(RelativeLayout.CENTER_VERTICAL or RelativeLayout.CENTER_HORIZONTAL)
        params.rightMargin = -150
        spinner.layoutParams = params
        container?.addView(spinner)
        return spinner
    }

    fun setValue(value: String?) {
        val changed = value != mValue
        if (changed) {
            mValue = value
            persistString(value)
            notifyDependencyChange(shouldDisableDependents())
            notifyChanged()
            adapter.notifyDataSetChanged()
            adapter.notifyDataSetInvalidated()
        }
    }

    private var visibility: Int
        get() = when (mVisibility) {
            0 -> View.VISIBLE
            1 -> View.INVISIBLE
            2 -> View.GONE
            else -> throw IllegalArgumentException("Not supported visibility")
        }
        set(visibility) {
            mVisibility = visibility
        }
}
