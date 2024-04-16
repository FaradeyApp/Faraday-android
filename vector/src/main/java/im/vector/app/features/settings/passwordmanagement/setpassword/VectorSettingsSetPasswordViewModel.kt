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

package im.vector.app.features.settings.passwordmanagement.setpassword

import com.airbnb.mvrx.MavericksViewModelFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.core.di.MavericksAssistedViewModelFactory
import im.vector.app.core.di.hiltMavericksViewModelFactory
import im.vector.app.core.platform.VectorViewModel
import im.vector.app.features.settings.passwordmanagement.changepassword.PasswordType
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.failure.isInvalidApplicationPassword
import org.matrix.android.sdk.api.failure.isNukePasswordEntered
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage

class VectorSettingsSetPasswordViewModel @AssistedInject constructor(
        @Assisted initialState: VectorSettingsSetPasswordViewState,
        private val lightweightSettingsStorage: LightweightSettingsStorage,
        private val session: Session
) : VectorViewModel<VectorSettingsSetPasswordViewState, VectorSettingsSetPasswordAction, VectorSettingsSetPasswordViewEvents>(initialState) {

    @AssistedFactory
    interface Factory : MavericksAssistedViewModelFactory<VectorSettingsSetPasswordViewModel, VectorSettingsSetPasswordViewState> {
        override fun create(initialState: VectorSettingsSetPasswordViewState): VectorSettingsSetPasswordViewModel
    }

    companion object : MavericksViewModelFactory<VectorSettingsSetPasswordViewModel, VectorSettingsSetPasswordViewState> by hiltMavericksViewModelFactory()
    override fun handle(action: VectorSettingsSetPasswordAction) {
        when (action) {
            is VectorSettingsSetPasswordAction.OnSavePassword -> savePassword()
            is VectorSettingsSetPasswordAction.OnSetPassword -> handleSetPassword(
                    password = action.password,
                    type = action.type
            )
            is VectorSettingsSetPasswordAction.OnRestoreState -> restoreState()
        }
    }

    private fun handleSetPassword(password: String, type: PasswordType) {
        when (type) {
            PasswordType.NEW -> setState { copy(password = password) }
            PasswordType.REPEAT -> setState { copy(repeatPassword = password) }
            PasswordType.OLD -> {}
        }
    }

    private fun restoreState() = withState {
        _viewEvents.post(
                VectorSettingsSetPasswordViewEvents.RestorePasswords(
                        password = it.password,
                        repeatPassword = it.repeatPassword
                )
        )
    }

    private fun savePassword() {
        withState {
            viewModelScope.launch {
                try {
                    val isPasswordSaved = session.applicationPasswordService().setApplicationPassword(it.password)
                    if(isPasswordSaved) {
                        lightweightSettingsStorage.setApplicationPasswordEnabled(true)
                        _viewEvents.post(VectorSettingsSetPasswordViewEvents.OnPasswordSaved)
                    }
                } catch (failure: Throwable) {
                    when{
                        failure.isInvalidApplicationPassword() -> {
                            _viewEvents.post(VectorSettingsSetPasswordViewEvents.ShowError(
                                    message = (failure as Failure.ServerError).error.code, location = PasswordErrorLocation.PASSWORD)
                            )
                        }
                        failure.isNukePasswordEntered() -> {
                            _viewEvents.post(
                                    VectorSettingsSetPasswordViewEvents.ShowError(
                                            message = failure.message.orEmpty(),
                                            location = PasswordErrorLocation.GENERAL
                                    )
                            )
                        }
                        else -> _viewEvents.post(
                                VectorSettingsSetPasswordViewEvents.ShowError(
                                        message = failure.message.orEmpty(),
                                        location = PasswordErrorLocation.GENERAL
                                )
                        )
                    }
                }
            }
        }
    }
}
