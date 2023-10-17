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

package im.vector.app.features.settings.passwordmanagement.changepassword

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.preference.Preference
import com.airbnb.mvrx.fragmentViewModel
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.platform.SimpleTextWatcher
import im.vector.app.core.preference.ButtonPreference
import im.vector.app.core.preference.TextInputPreference
import im.vector.app.features.settings.VectorSettingsBaseFragment

@AndroidEntryPoint
class VectorSettingsChangePasswordFragment :
        VectorSettingsBaseFragment() {

    private val viewModel: VectorSettingsChangePasswordViewModel by fragmentViewModel()

    override val preferenceXmlRes = R.xml.vector_settings_change_password
    override var titleRes = R.string.settings_reset_password

    override fun setDivider(divider: Drawable?) {
        super.setDivider(ColorDrawable(Color.TRANSPARENT))
    }

    private val oldPasswordPreference by lazy {
        findPreference<TextInputPreference>("SETTINGS_CURRENT_PASSWORD_PREFERENCE_KEY")?.editTextView
    }

    private val newPasswordPreference by lazy {
        findPreference<TextInputPreference>("SETTINGS_NEW_PASSWORD_PREFERENCE_KEY")?.editTextView
    }

    private val repeatPasswordPreference by lazy {
        findPreference<TextInputPreference>("SETTINGS_REPEAT_PASSWORD_PREFERENCE_KEY")?.editTextView
    }

    private val savePreference by lazy {
        findPreference<ButtonPreference>("SETTINGS_PROXY_SAVE_KEY")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewEvents()
    }

    override fun bindPref() {
        savePreference?.let { pref ->
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                if (checkFieldsAreValid()) {
                    viewModel.handle(
                            VectorSettingsChangePasswordAction.OnSaveNewPassword(
                                    oldPassword = oldPasswordPreference?.text.toString(),
                                    newPassword = newPasswordPreference?.text.toString()
                            )
                    )
                }
                true
            }
        }
        oldPasswordPreference?.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                super.onTextChanged(s, start, before, count)
                viewModel.handle(
                        VectorSettingsChangePasswordAction.OnSetPassword(
                                password = s.toString(), type = PasswordType.OLD
                        )
                )
            }
        })
        newPasswordPreference?.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                super.onTextChanged(s, start, before, count)
                viewModel.handle(
                        VectorSettingsChangePasswordAction.OnSetPassword(
                                password = s.toString(), type = PasswordType.NEW
                        )
                )
            }
        })
        repeatPasswordPreference?.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                super.onTextChanged(s, start, before, count)
                viewModel.handle(
                        VectorSettingsChangePasswordAction.OnSetPassword(
                                password = s.toString(), type = PasswordType.REPEAT
                        )
                )
            }
        })
    }

    private fun observeViewEvents() {
        viewModel.observeViewEvents { event ->
            when (event) {
                is VectorSettingsChangePasswordViewEvents.OnPasswordReset -> {
                    Toast.makeText(activity, R.string.login_reset_password_success_notice, Toast.LENGTH_SHORT).show()
                }

                is VectorSettingsChangePasswordViewEvents.ShowError -> {
                    when (event.location) {
                        ErrorLocation.OLD_PASSWORD -> {
                            oldPasswordPreference?.error = getString(R.string.settings_fail_to_update_password_invalid_current_password)
                        }

                        ErrorLocation.GENERAL -> {
                            Toast.makeText(activity, R.string.settings_fail_to_update_password, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                is VectorSettingsChangePasswordViewEvents.RestorePasswords -> {
                    listOf(
                            oldPasswordPreference,
                            newPasswordPreference,
                            repeatPasswordPreference
                    ).forEach {
                        it?.setText(event.oldPassword)
                    }
                }
            }
        }
    }

    private fun checkFieldsAreValid(): Boolean {
        var allFieldsAreValid = true
        if (oldPasswordPreference?.text?.isEmpty() == true) {
            oldPasswordPreference?.error = getString(R.string.error_empty_field_old_password)
            allFieldsAreValid = false
        }
        if (newPasswordPreference?.text?.isEmpty() == true) {
            newPasswordPreference?.error = getString(R.string.error_empty_field_new_password)
            allFieldsAreValid = false
        }
        if (repeatPasswordPreference?.text?.isEmpty() == true) {
            repeatPasswordPreference?.error = getString(R.string.error_empty_field_repeat_new_password)
            allFieldsAreValid = false
        }
        if (repeatPasswordPreference?.text.toString() != newPasswordPreference?.text.toString()) {
            repeatPasswordPreference?.error = getString(R.string.error_passwords_do_not_match)
            allFieldsAreValid = false
        }
        return allFieldsAreValid
    }
}
