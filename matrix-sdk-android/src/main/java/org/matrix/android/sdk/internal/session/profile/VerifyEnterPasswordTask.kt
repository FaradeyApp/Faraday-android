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

import org.matrix.android.sdk.api.auth.data.Credentials
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.task.Task
import timber.log.Timber
import javax.inject.Inject

internal interface VerifyEnterPasswordTask : Task<VerifyEnterPasswordTask.Params, Credentials?> {
    data class Params(
            val username: String,
            val password: String
    )
}

internal class DefaultVerifyEnterPasswordTask @Inject constructor(
        private val profileAPI: ProfileAPI,
        private val globalErrorReceiver: GlobalErrorReceiver
) : VerifyEnterPasswordTask {
    override suspend fun execute(params: VerifyEnterPasswordTask.Params): Credentials? {
        return try {
            val result = executeRequest(globalErrorReceiver) {
                profileAPI.getLoginByPassword(
                        GetLoginByPasswordBody(
                                type = "m.login.password",
                                identifier = LoginIdentifier(
                                        type = "m.id.user",
                                        user = params.username
                                ),
                                password = params.password
                        )
                )
            }
            Credentials(
                    userId = result.userId,
                    deviceId = result.deviceId,
                    accessToken = result.accessToken,
                    refreshToken = null,
                    homeServer = result.homeServer
            )
        } catch (throwable: Throwable) {
            Timber.i("Get Login By Password error $throwable")
            if (throwable is Failure.ServerError) throw throwable
            null
        }
    }
}
