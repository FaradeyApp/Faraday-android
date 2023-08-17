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
import com.airbnb.mvrx.Success
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.core.di.MavericksAssistedViewModelFactory
import im.vector.app.core.di.hiltMavericksViewModelFactory
import im.vector.app.core.platform.VectorViewModel
import org.matrix.android.sdk.api.session.Session

class AccountsViewModel @AssistedInject constructor(
        @Assisted initialState: AccountsViewState,
        private val session: Session
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
            is AccountsAction.SelectAccount -> handleSelectAccountAction()
        }
    }

    private fun observeAccounts() {
        session.userService().getUserLive(session.myUserId)
                .asFlow()
                .setOnEach {
                    copy(
                            asyncAccounts = Success(
                                    listOf(
                                            Account(
                                                    userId = it.getOrNull()?.userId.orEmpty(),
                                                    avatar = it.getOrNull()?.avatarUrl,
                                                    username = it.getOrNull()?.displayName,
                                                    unreadMessages = 9

                                            )
                                    )
                            )
                    )
                }
    }

    private fun handleSelectAccountAction() {}
}
