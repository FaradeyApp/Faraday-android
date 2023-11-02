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

package im.vector.app.features.settings.passwordmanagement.enterpassword

import com.airbnb.mvrx.MavericksViewModelFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.core.di.MavericksAssistedViewModelFactory
import im.vector.app.core.di.hiltMavericksViewModelFactory
import im.vector.app.core.platform.VectorViewModel
import im.vector.app.features.settings.passwordmanagement.setpassword.PasswordErrorLocation
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.failure.isInvalidApplicationPassword
import org.matrix.android.sdk.api.failure.isNukePasswordEntered
import org.matrix.android.sdk.api.session.Session

class EnterPasswordViewModel @AssistedInject constructor(
        @Assisted private val initialState: EnterPasswordViewState,
        private val session: Session
) : VectorViewModel<EnterPasswordViewState, EnterPasswordAction, EnterPasswordViewEvents>(initialState) {

    @AssistedFactory
    interface Factory : MavericksAssistedViewModelFactory<EnterPasswordViewModel, EnterPasswordViewState> {
        override fun create(initialState: EnterPasswordViewState): EnterPasswordViewModel
    }

    companion object : MavericksViewModelFactory<EnterPasswordViewModel, EnterPasswordViewState> by hiltMavericksViewModelFactory()

    override fun handle(action: EnterPasswordAction) {
        when (action) {
            is EnterPasswordAction.OnChangePassword -> {
                setState {
                    copy(
                            password = action.password
                    )
                }
            }

            is EnterPasswordAction.OnClickNext ->
                withState {
                    verifyPassword(password = it.password)
                }

            is EnterPasswordAction.RestoreState ->
                withState {
                    _viewEvents.post(
                            EnterPasswordViewEvents.OnRestoreState(
                                    password = it.password,
                                    error = it.error
                            )
                    )
                }
        }
    }

    private fun verifyPassword(password: String) = viewModelScope.launch {
        try {
            val correctPasswordEntered = session.applicationPasswordService().loginByApplicationPassword(
                    password = password
            )
            if(correctPasswordEntered) {
                _viewEvents.post(EnterPasswordViewEvents.OnNavigateToPasswordManagement)
            }
        } catch (throwable: Throwable) {
            if (throwable is Failure.ServerError) {
                when {
                    throwable.isInvalidApplicationPassword() -> _viewEvents.post(
                            EnterPasswordViewEvents.ShowError(
                                    message = throwable.error.message,
                                    location = PasswordErrorLocation.PASSWORD
                            )
                    )
                    throwable.isNukePasswordEntered() -> {
                        _viewEvents.post(EnterPasswordViewEvents.OnNukePasswordEntered)
                    }
                    else -> _viewEvents.post(
                            EnterPasswordViewEvents.ShowError(
                                    message = throwable.message.orEmpty(),
                                    location = PasswordErrorLocation.GENERAL
                            )
                    )
                }
                return@launch
            }
        }
    }
}
