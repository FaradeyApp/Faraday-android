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

import com.airbnb.epoxy.EpoxyController
import im.vector.app.features.home.AvatarRenderer
import im.vector.app.features.home.room.list.UnreadCounterBadgeView
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import org.matrix.android.sdk.api.session.profile.model.toMatrixItem
import javax.inject.Inject

class AccountsController @Inject constructor(
        private val avatarRenderer: AvatarRenderer
) : EpoxyController() {

    var callback: Callback? = null
    private var viewState: AccountsViewState? = null

    override fun buildModels() {
        val nonNullViewState = viewState ?: return
        buildAccountsModels(accounts = nonNullViewState.asyncAccounts())
    }

    fun update(state: AccountsViewState) {
        viewState = state
        requestModelBuild()
    }

    private fun buildAccountsModels(
            accounts: List<AccountItem>?
    ) {
        val host = this
        accounts?.forEach { account ->
            accountItem {
                id(account.userId)
                avatarRenderer(host.avatarRenderer)
                countState(UnreadCounterBadgeView.State.Text(account.unreadCount.toString(), false))
                listener { host.callback?.onAccountSelected(account) }
                matrixItem(account.toMatrixItem())
            }
        }
    }

    interface Callback {
        fun onAccountSelected(account: AccountItem)
    }
}
