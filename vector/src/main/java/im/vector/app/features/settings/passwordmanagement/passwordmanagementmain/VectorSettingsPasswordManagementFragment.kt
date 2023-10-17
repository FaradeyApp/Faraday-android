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

package im.vector.app.features.settings.passwordmanagement.passwordmanagementmain

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import com.airbnb.mvrx.fragmentViewModel
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.dialogs.NukePasswordDialog
import im.vector.app.core.preference.ButtonPreference
import im.vector.app.core.preference.VectorPreference
import im.vector.app.core.preference.VectorSwitchPreference
import im.vector.app.features.settings.VectorSettingsActivity
import im.vector.app.features.settings.VectorSettingsBaseFragment
import im.vector.app.features.settings.passwordmanagement.changepassword.VectorSettingsChangePasswordFragment
import im.vector.app.features.settings.passwordmanagement.VectorSettingsSetPasswordFragment

@AndroidEntryPoint
class VectorSettingsPasswordManagementFragment :
        VectorSettingsBaseFragment() {

    private val viewModel: VectorSettingsPasswordManagementViewModel by fragmentViewModel()

    override var titleRes = R.string.settings_password

    override val preferenceXmlRes = R.xml.vector_password_management

    private val passwordPreference by lazy {
        findPreference<VectorSwitchPreference>("SETTINGS_PASSWORD_PREFERENCE_KEY")
    }

    private val changePasswordPreference by lazy {
        findPreference<VectorPreference>("SETTINGS_PASSWORD_CHANGE_PREFERENCE_KEY")
    }

    private val nukePasswordPreference by lazy {
        findPreference<VectorPreference>("SETTINGS_NUKE_PASSWORD_PREFERENCE_KEY")
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewEvents()
    }

    override fun bindPref() {
        passwordPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            changePasswordPreference?.isVisible = passwordPreference?.isChecked ?: false
            nukePasswordPreference?.isVisible = passwordPreference?.isChecked ?: false
            true
        }
        changePasswordPreference?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                goToChangePasswordScreen()
                true
            }
            isVisible = passwordPreference?.isChecked ?: false
        }
        nukePasswordPreference?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showNukePassword()
                true
            }
            isVisible = passwordPreference?.isChecked ?: false
        }
    }

    private fun observeViewEvents() {
        viewModel.observeViewEvents { event ->
            when(event) {
                is VectorSettingsPasswordManagementViewEvents.ShowPasswordDialog -> {
                    activity?.let {
                        NukePasswordDialog().show(it, event.nukePassword)
                    }
                }
            }
        }
    }

    private fun showNukePassword() {
        viewModel.handle(VectorSettingsPasswordManagementAction.OnClickNukePassword)
    }

    private fun goToChangePasswordScreen() {
        (vectorActivity as? VectorSettingsActivity)?.navigateTo(VectorSettingsChangePasswordFragment::class.java)
    }

    private fun goToSetPasswordScreen() {
        (vectorActivity as? VectorSettingsActivity)?.navigateTo(VectorSettingsSetPasswordFragment::class.java)
    }


}
