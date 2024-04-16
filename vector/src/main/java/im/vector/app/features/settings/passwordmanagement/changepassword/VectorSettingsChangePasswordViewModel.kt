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

import com.airbnb.mvrx.MavericksViewModelFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.core.di.MavericksAssistedViewModelFactory
import im.vector.app.core.di.hiltMavericksViewModelFactory
import im.vector.app.core.platform.VectorViewModel
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.failure.isInvalidApplicationPassword
import org.matrix.android.sdk.api.failure.isInvalidPassword
import org.matrix.android.sdk.api.session.Session

class VectorSettingsChangePasswordViewModel @AssistedInject constructor(
        @Assisted initialState: VectorSettingsChangePasswordViewState,
        private val session: Session
) : VectorViewModel<VectorSettingsChangePasswordViewState, VectorSettingsChangePasswordAction, VectorSettingsChangePasswordViewEvents>(initialState) {

    @AssistedFactory
    interface Factory : MavericksAssistedViewModelFactory<VectorSettingsChangePasswordViewModel, VectorSettingsChangePasswordViewState> {
        override fun create(initialState: VectorSettingsChangePasswordViewState): VectorSettingsChangePasswordViewModel
    }

    companion object : MavericksViewModelFactory<VectorSettingsChangePasswordViewModel, VectorSettingsChangePasswordViewState> by hiltMavericksViewModelFactory()

    override fun handle(action: VectorSettingsChangePasswordAction) {
        when (action) {
            is VectorSettingsChangePasswordAction.OnSaveNewPassword -> changePassword()
            is VectorSettingsChangePasswordAction.OnSetPassword -> handleSetPassword(
                    password = action.password,
                    type = action.type
            )

            is VectorSettingsChangePasswordAction.OnRestoreState -> restoreState()
        }
    }

    private fun changePassword() {
        withState {
            viewModelScope.launch {
                try {
                    val passwordChanged = session.applicationPasswordService().updateApplicationPassword(
                            oldPassword = it.oldPassword, newPassword = it.newPassword
                    )
                    if (passwordChanged) {
                        _viewEvents.post(VectorSettingsChangePasswordViewEvents.OnPasswordReset)
                    }
                } catch (failure: Throwable) {
                    if (failure is Failure.ServerError) {
                        if (failure.isInvalidPassword()) {
                            _viewEvents.post(VectorSettingsChangePasswordViewEvents.ShowError(message = failure.error.message, location = ErrorLocation.OLD_PASSWORD))
                        } else if (failure.isInvalidApplicationPassword()) {
                            _viewEvents.post(
                                    VectorSettingsChangePasswordViewEvents.ShowError(
                                            message = failure.error.code,
                                            location = ErrorLocation.GENERAL
                                    )
                            )
                        } else {
                            _viewEvents.post(
                                    VectorSettingsChangePasswordViewEvents.ShowError(
                                            message = failure.error.message,
                                            location = ErrorLocation.GENERAL
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleSetPassword(password: String, type: PasswordType) {
        when (type) {
            PasswordType.OLD -> setState { copy(oldPassword = password) }
            PasswordType.NEW -> setState { copy(newPassword = password) }
            PasswordType.REPEAT -> setState { copy(repeatPassword = password) }
        }
    }

    private fun restoreState() = withState {
        _viewEvents.post(
                VectorSettingsChangePasswordViewEvents.RestorePasswords(
                        oldPassword = it.oldPassword,
                        newPassword = it.newPassword,
                        repeatPassword = it.repeatPassword
                )
        )
    }
}

enum class PasswordType {
    OLD,
    NEW,
    REPEAT
}
