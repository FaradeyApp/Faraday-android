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
import android.widget.RadioGroup
import androidx.preference.PreferenceViewHolder
import im.vector.app.R
import org.matrix.android.sdk.api.util.ConnectionType

class RadioGroupReference : VectorPreference {

    var type: ConnectionType? = null
        private set

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        layoutResource = R.layout.vector_preference_radio_group
    }

    fun setType(connectionType: ConnectionType?) {
        type = connectionType
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)

        val radioGroup = holder.findViewById(R.id.radioGroup) as? RadioGroup
        radioGroup?.setOnCheckedChangeListener(null)

        when (type) {
            ConnectionType.MATRIX -> {
                radioGroup?.check(R.id.matrix_radio_button)
            }
            ConnectionType.ONION -> {
                radioGroup?.check(R.id.onion_radio_button)
            }
            ConnectionType.I2P -> {
                radioGroup?.check(R.id.i2p_radio_button)
            }
            null -> Unit
        }

        radioGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.matrix_radio_button -> {
                    onPreferenceChangeListener?.onPreferenceChange(this, ConnectionType.MATRIX)
                    type = ConnectionType.MATRIX
                }
                R.id.onion_radio_button -> {
                    onPreferenceChangeListener?.onPreferenceChange(this, ConnectionType.ONION)
                    type = ConnectionType.ONION
                }
                R.id.i2p_radio_button -> {
                    onPreferenceChangeListener?.onPreferenceChange(this, ConnectionType.I2P)
                    type = ConnectionType.I2P
                }
            }
        }
    }
}
