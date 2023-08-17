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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.core.extensions.cleanup
import im.vector.app.core.extensions.configureWith
import im.vector.app.core.platform.StateView
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.databinding.FragmentAccountsListBinding
import javax.inject.Inject

@AndroidEntryPoint
class AccountsFragment :
        VectorBaseFragment<FragmentAccountsListBinding>(), AccountsController.Callback {

    @Inject lateinit var accountsController: AccountsController

    private val viewModel: AccountsViewModel by fragmentViewModel()
    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAccountsListBinding {
        return FragmentAccountsListBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        views.stateView.contentView = views.groupListView
        setupAccountsController()
        observeViewEvents()
    }

    private fun setupAccountsController() {
        accountsController.callback = this
        views.groupListView.configureWith(accountsController)
    }

    private fun observeViewEvents() = viewModel.observeViewEvents {
        when (it) {
            is AccountsViewEvents.SelectAccount -> {}
        }
    }

    override fun invalidate() = withState(viewModel) { state ->
        when (val spaces = state.asyncAccounts) {
            Uninitialized,
            is Loading -> {
                views.stateView.state = StateView.State.Loading
                return@withState
            }
            is Success -> {
                views.stateView.state = StateView.State.Content
                views.groupListView.isVisible = spaces.invoke().isNotEmpty()
            }
            else -> Unit
        }
        accountsController.update(state)
    }

    override fun onAccountSelected(account: Account) {
    }

    override fun onDestroyView() {
        accountsController.callback = null
        views.groupListView.cleanup()
        super.onDestroyView()
    }
}
