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

package org.matrix.android.sdk.internal.session.profile

import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.auth.data.Credentials
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.auth.login.LoginWizard
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.internal.auth.SessionCreator
import org.matrix.android.sdk.internal.auth.db.LocalAccountStore
import org.matrix.android.sdk.internal.task.Task
import javax.inject.Inject

internal interface ReLoginInMultiAccountTask : Task<ReLoginInMultiAccountTask.Params, Session> {
    data class Params(
            val homeServerConnectionConfig: HomeServerConnectionConfig,
            val userId: String,
            val currentCredentials: Credentials,
            val sessionCreator: SessionCreator,
    )
}

internal class DefaultReLoginInMultiAccountTask @Inject constructor(
        private val authenticationService: AuthenticationService,
) : ReLoginInMultiAccountTask {
    private val localAccountStore: LocalAccountStore = authenticationService.getLocalAccountStore()
    private val loginWizard: LoginWizard
        get() = authenticationService.getLoginWizard()

    override suspend fun execute(params: ReLoginInMultiAccountTask.Params): Session {
        val account = localAccountStore.getAccount(params.userId)
        require(account.homeServerUrl.isNotBlank())

        val homeServerConnectionConfig = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(account.homeServerUrl)
                .build()


        authenticationService.cancelPendingLoginOrRegistration()
        authenticationService.getLoginFlow(homeServerConnectionConfig)
        val session = loginWizard.login(
                account.username!!,
                account.password!!,
                "Faraday Android"
        )
        return session
    }
}
