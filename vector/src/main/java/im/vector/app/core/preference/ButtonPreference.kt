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
import android.widget.Button
import androidx.preference.PreferenceViewHolder
import im.vector.app.R

class ButtonPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int = 0
) : VectorPreference(context, attrs, defStyleAttr) {

    init {
        layoutResource = R.layout.vector_preference_button
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)

        val button = holder.findViewById(R.id.button) as? Button

        button?.setOnClickListener {
            onPreferenceClickListener?.onPreferenceClick(this)
        }
    }
}
