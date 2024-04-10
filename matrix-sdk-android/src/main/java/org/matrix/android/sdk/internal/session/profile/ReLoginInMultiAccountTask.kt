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


import org.matrix.android.sdk.api.auth.LoginType
import org.matrix.android.sdk.api.auth.data.Credentials
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.internal.auth.SessionCreator
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.task.Task
import javax.inject.Inject
import org.matrix.android.sdk.api.session.Session
import timber.log.Timber

internal interface ReLoginInMultiAccountTask : Task<ReLoginInMultiAccountTask.Params, Session> {
    data class Params(
            val homeServerConnectionConfig: HomeServerConnectionConfig,
            val userId: String,
            val currentCredentials: Credentials,
            val sessionCreator: SessionCreator
    )
}

internal class DefaultReLoginInMultiAccountTask @Inject constructor(
        private val profileAPI: ProfileAPI,
        private val globalErrorReceiver: GlobalErrorReceiver,
        private val localAccountStore: LocalAccountStore
) : ReLoginInMultiAccountTask {
    override suspend fun execute(params: ReLoginInMultiAccountTask.Params): Session {
        var result = ""
        val credentials: Credentials = try {
//            result = executeRequest(globalErrorReceiver) {
//                profileAPI.reLoginMultiAccount(params.userId)
//            }.loginToken
            val account = localAccountStore.getAccount(params.userId)
            val loginResponse = executeRequest(globalErrorReceiver) {
                if (account.token != null) {
                    profileAPI.getLoginByToken(
                            GetLoginByTokenBody(
                                    type = "m.login.token",
                                    token = account.token!!,
                            )
                    )
                } else {
                    profileAPI.getLoginByPassword(
                            GetLoginByPasswordBody(
                                    type = "m.login.password",
                                    identifier = LoginIdentifier(
                                            type = "m.id.user",
                                            user = account.username!!
                                    ),
                                    password = account.password!!,
                            )
                    )
                }
            }
            Credentials(
                    userId = loginResponse.userId,
                    deviceId = loginResponse.deviceId,
                    homeServer = loginResponse.homeServer,
                    accessToken = loginResponse.accessToken,
                    refreshToken = null,
            )
        } catch (throwable: Throwable) {
            Timber.i("ReLoginInMultiAccountTask Throwable=$throwable")
            if(throwable is Failure.ServerError) throw throwable
            else  params.currentCredentials
        }
        Timber.i("ReLoginInMultiAccountTask result=$result")
        return params.sessionCreator.createSession(credentials, params.homeServerConnectionConfig, LoginType.DIRECT)
    }
}
