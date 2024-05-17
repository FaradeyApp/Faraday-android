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

package im.vector.app.features.home.accounts

import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.Success
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.di.MavericksAssistedViewModelFactory
import im.vector.app.core.di.hiltMavericksViewModelFactory
import im.vector.app.core.platform.VectorViewModel
import im.vector.app.core.session.ConfigureAndStartSessionUseCase
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import timber.log.Timber

class AccountsViewModel @AssistedInject constructor(
        @Assisted initialState: AccountsViewState,
        private val session: Session,
        private val lightweightSettingsStorage: LightweightSettingsStorage,
        private val authenticationService: AuthenticationService,
        private val activeSessionHolder: ActiveSessionHolder,
        private val configureAndStartSessionUseCase: ConfigureAndStartSessionUseCase
) : VectorViewModel<AccountsViewState, AccountsAction, AccountsViewEvents>(initialState) {

    @AssistedFactory
    interface Factory : MavericksAssistedViewModelFactory<AccountsViewModel, AccountsViewState> {
        override fun create(initialState: AccountsViewState): AccountsViewModel
    }

    companion object : MavericksViewModelFactory<AccountsViewModel, AccountsViewState> by hiltMavericksViewModelFactory()

    init {
        observeAccounts()
    }

    override fun handle(action: AccountsAction) {
        when (action) {
            is AccountsAction.SelectAccount -> handleSelectAccountAction(account = action.account)
            is AccountsAction.SetRestartAppValue -> handleSetRestartAppValue(value = action.value)
            is AccountsAction.SetErrorMessage -> handleSetErrorMessage(message = action.value)
        }
    }

    fun observeAccounts() = viewModelScope.launch {
        flow {
            if(!lightweightSettingsStorage.areCustomSettingsEnabled()) return@flow
            val result = session.profileService().getMultipleAccount(
                    session.myUserId
            )
            emit(result)
        }.setOnEach {
            copy(
                    asyncAccounts = Success(it)
            )
        }
    }

    private fun handleSetRestartAppValue(value: Boolean) {
        if(value) {
            lightweightSettingsStorage.setApplicationPasswordEnabled(false)
        }
        setState {
            copy(
                    restartApp = value
            )
        }
    }

    private fun handleSetErrorMessage(message: String?) {
        setState {
            copy(
                    errorMessage = message
            )
        }
    }

    private fun handleSelectAccountAction(account: AccountItem) = viewModelScope.launch {
        try {
            val result = session.profileService().reLoginMultiAccount(
                    userId = account.userId,
                    homeServerConnectionConfig = session.sessionParams.homeServerConnectionConfig,
                    currentCredentials = session.sessionParams.credentials,
                    sessionCreator = authenticationService.getSessionCreator()
            )
            activeSessionHolder.setActiveSession(result)
            authenticationService.reset()
            configureAndStartSessionUseCase.execute(result)
            Timber.i("handleSelectAccountAction ${result.sessionParams.credentials}")
        } catch (throwable: Throwable) {
            Timber.i("Error re-login into app $throwable")
            if (throwable is Failure.ServerError) {
                handleSetErrorMessage(throwable.error.message)
                return@launch
            }
        }
        handleSetRestartAppValue(value = true)
    }
}
