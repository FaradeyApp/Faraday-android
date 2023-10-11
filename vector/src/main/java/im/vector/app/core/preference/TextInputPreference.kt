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
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.google.android.material.textfield.TextInputEditText
import im.vector.app.R

class TextInputPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int = 0
) : VectorPreference(context, attrs, defStyleAttr) {

    var hint = ""
        set(value) {
            field = value
            notifyChanged()
        }

    var text: String = ""
        set(value) {
            field = value
            notifyChanged()
        }

    var editTextView: TextInputEditText? = null

    init {
        layoutResource = R.layout.vector_preference_text_input
        context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.TextInputPreference,
                0, 0
        ).apply {
            hint = getString(R.styleable.TextInputPreference_hint).orEmpty()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        editTextView = holder.findViewById(R.id.text_input_edit_text) as? TextInputEditText
        editTextView?.hint = hint
        editTextView?.isSingleLine = true
        text.takeIf { it.isNotEmpty() }?.let {
            editTextView?.setText(text)
        }
    }
}
