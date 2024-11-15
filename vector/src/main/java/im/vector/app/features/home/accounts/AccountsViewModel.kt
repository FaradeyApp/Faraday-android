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
import im.vector.app.features.home.ChangeAccountUseCase
import im.vector.app.features.login.ReAuthHelper
import im.vector.lib.core.utils.flow.throttleFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
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
        private val session: Session,
        private val lightweightSettingsStorage: LightweightSettingsStorage,
        private val authenticationService: AuthenticationService,
        private val changeAccountUseCase: ChangeAccountUseCase,
) : VectorViewModel<AccountsViewState, AccountsAction, AccountsViewEvents>(initialState) {

    @AssistedFactory
    interface Factory : MavericksAssistedViewModelFactory<AccountsViewModel, AccountsViewState> {
        override fun create(initialState: AccountsViewState): AccountsViewModel
    }

    companion object : MavericksViewModelFactory<AccountsViewModel, AccountsViewState> by hiltMavericksViewModelFactory()

    private val profileService = session.profileService()
//    private var rawAccounts = MutableStateFlow(emptyList<LocalAccount>())

    init {
        viewModelScope.launch {
            profileService.getAccounts().asFlow()
                    .distinctUntilChanged()
                    .collect {
                        observeAccounts(it)
                    }
        }
//        viewModelScope.launch {
//            rawAccounts.collectLatest {
//                observeAccounts(it)
//            }
//        }
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

    private fun observeAccounts(localAccounts: List<LocalAccount>) = viewModelScope.launch(Dispatchers.Main) {
        Timber.d("Accounts observing: $localAccounts")
        val items = localAccounts.filter { it.userId != session.myUserId }.map { account ->
            async(SupervisorJob() + Dispatchers.IO) {
                try {
                    val data = profileService.getProfile(
                            account.userId, account.homeServerUrl, storeInDatabase = false
                    )
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
        }.awaitAll()

        Timber.d("Running on thread: ${Thread.currentThread().name}")
        setState {
            copy(
                    accountItems = items
            )
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
            changeAccountUseCase.execute(account.userId)
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
