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

import androidx.lifecycle.asFlow
import com.airbnb.mvrx.MavericksViewModelFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.di.MavericksAssistedViewModelFactory
import im.vector.app.core.di.hiltMavericksViewModelFactory
import im.vector.app.core.platform.VectorViewModel
import im.vector.app.core.session.ConfigureAndStartSessionUseCase
import im.vector.app.features.login.ReAuthHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.profile.ProfileService
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.internal.session.profile.LocalAccount
import timber.log.Timber

class AccountsViewModel @AssistedInject constructor(
        @Assisted initialState: AccountsViewState,
        private val reAuthHelper: ReAuthHelper,
        private val session: Session,
        private val lightweightSettingsStorage: LightweightSettingsStorage,
        private val authenticationService: AuthenticationService,
        private val activeSessionHolder: ActiveSessionHolder,
        private val configureAndStartSessionUseCase: ConfigureAndStartSessionUseCase,
) : VectorViewModel<AccountsViewState, AccountsAction, AccountsViewEvents>(initialState) {

    @AssistedFactory
    interface Factory : MavericksAssistedViewModelFactory<AccountsViewModel, AccountsViewState> {
        override fun create(initialState: AccountsViewState): AccountsViewModel
    }

    companion object : MavericksViewModelFactory<AccountsViewModel, AccountsViewState> by hiltMavericksViewModelFactory()

    private val profileService = session.profileService()

    private var accountLoadingJob: Job? = null

    init {
        accountLoadingJob = viewModelScope.launch {
            val accounts = profileService.getMultipleAccount(session.myUserId)
            setState {
                copy(
                    accountItems = accounts
                )
            }
        }
        profileService.getAccounts().asFlow().onEach {
            observeAccounts(it)
        }
    }

    override fun handle(action: AccountsAction) {
        when (action) {
            is AccountsAction.SelectAccount -> handleSelectAccountAction(account = action.account)
            is AccountsAction.SetRestartAppValue -> handleSetRestartAppValue(value = action.value)
            is AccountsAction.SetErrorMessage -> handleSetErrorMessage(message = action.value)
            is AccountsAction.DeleteAccount -> handleDeleteAccount(account = action.account)
            is AccountsAction.SetErrorWhileAccountChange -> handleSetErrorWhileAccountChange(account = action.account)
        }
    }

    private fun handleDeleteAccount(account: AccountItem) = viewModelScope.launch {
        authenticationService.getLocalAccountStore().deleteAccount(account.userId)
    }

    private fun observeAccounts(localAccounts: List<LocalAccount>) {
        if (accountLoadingJob?.isActive == true) {
            accountLoadingJob?.cancel()
        }
        accountLoadingJob = viewModelScope.launch {
            val items = localAccounts.map { account ->
                try {
                    val data = profileService.getProfile(account.userId, account.homeServerUrl)
                    AccountItem(
                            userId = account.userId,
                            displayName = data.get(ProfileService.DISPLAY_NAME_KEY) as? String ?: "",
                            avatarUrl = data.get(ProfileService.AVATAR_URL_KEY) as? String,
                            unreadCount = account.unreadCount
                    )
                } catch (throwable: Throwable) {
                    Timber.i("Error get multiple account data: $throwable")
                    AccountItem(
                            userId = account.userId,
                            displayName = account.userId.removePrefix("@").split(':')[0],
                            avatarUrl = null,
                            unreadCount = account.unreadCount
                    )
                }
            }

            setState {
                copy(
                        accountItems = items
                )
            }
        }
    }

    private fun handleSetRestartAppValue(value: Boolean) {
        if (value) {
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

    private fun handleSetErrorWhileAccountChange(account: AccountItem?) {
        setState {
            copy(
                    invalidAccount = account
            )
        }
    }

    private fun handleSelectAccountAction(account: AccountItem) = viewModelScope.launch {
        try {
            session.close()
            reAuthHelper.data = null
            val result = session.profileService()
                    .reLoginMultiAccount(userId = account.userId, authenticationService.getSessionCreator()) {
                        reAuthHelper.data = it.password
                    }
            Timber.d("Session created")
            activeSessionHolder.setActiveSession(result)
            lightweightSettingsStorage.setLastSessionHash(result.sessionId)
            authenticationService.reset()
            Timber.d("Configure and start session")
            configureAndStartSessionUseCase.execute(result)
            Timber.i("handleSelectAccountAction ${result.sessionParams.credentials}")

            handleSetRestartAppValue(value = true)
        } catch (throwable: Throwable) {
            Timber.i("Error re-login into app $throwable")
            if (throwable is Failure.ServerError) {
                handleSetErrorMessage(throwable.error.message)
            }
            handleSetErrorWhileAccountChange(account)
            return@launch
        }
    }
}
