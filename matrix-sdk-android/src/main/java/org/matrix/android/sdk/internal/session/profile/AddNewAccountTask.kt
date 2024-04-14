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
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.internal.auth.db.LocalAccountStore
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.task.Task
import timber.log.Timber
import javax.inject.Inject

internal interface AddNewAccountTask: Task<AddNewAccountTask.Params, Boolean> {
    data class Params(
            val username: String,
            val password: String
    )
}

internal class DefaultAddNewAccountTask @Inject constructor(
        private val profileAPI: ProfileAPI,
        private val globalErrorReceiver: GlobalErrorReceiver,
        authenticationService: AuthenticationService
) : AddNewAccountTask {
    private val localAccountStore: LocalAccountStore = authenticationService.getLocalAccountStore()

    override suspend fun execute(params: AddNewAccountTask.Params): Boolean {
        val credentials = try {
            executeRequest(globalErrorReceiver) {
                profileAPI.getLoginByPassword(
                        GetLoginByPasswordBody(
                                type = "m.login.password",
                                identifier = LoginIdentifier(
                                        type = "m.id.user",
                                        user = params.username
                                ),
                                password = params.password,
                        )
                )
            }
        } catch (throwable: Throwable) {
            Timber.i("Get Login By Password error $throwable")
            if(throwable is Failure.ServerError) throw throwable
            null
        } ?: return false
        val result = try {
//            executeRequest(globalErrorReceiver) {
//                profileAPI.addNewAccount(AddNewAccountBody(token = credentials.accessToken))
//            }.status
            localAccountStore.addAccount(credentials.userId, params.username, params.password)
            "OK"
        } catch (throwable: Throwable) {
            if(throwable is Failure.ServerError) throw throwable
            Timber.i("Add New Account error $throwable")
            null
        }
        Timber.i("DefaultAddNewAccountTask result=$result")
        return result == "OK"
    }
}
