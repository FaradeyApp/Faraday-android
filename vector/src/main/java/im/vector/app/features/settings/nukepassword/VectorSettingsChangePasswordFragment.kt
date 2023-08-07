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

package im.vector.app.features.settings.nukepassword

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.preference.TextInputPreference
import im.vector.app.features.settings.VectorSettingsBaseFragment


@AndroidEntryPoint
class VectorSettingsChangePasswordFragment :
        VectorSettingsBaseFragment()  {

    override val preferenceXmlRes = R.xml.vector_settings_change_password
    override var titleRes = R.string.settings_change_password

    override fun setDivider(divider: Drawable?) {
        super.setDivider(ColorDrawable(Color.TRANSPARENT))
    }

    private val oldPasswordPreference by lazy {
        findPreference<TextInputPreference>("SETTINGS_CURRENT_PASSWORD_PREFERENCE_KEY")!!
    }

    private val newPasswordPreference by lazy {
        findPreference<TextInputPreference>("SETTINGS_NEW_PASSWORD_PREFERENCE_KEY")!!
    }

    private val repeatPasswordPreference by lazy {
        findPreference<TextInputPreference>("SETTINGS_REPEAT_PASSWORD_PREFERENCE_KEY")!!
    }

    override fun bindPref() {}
}
