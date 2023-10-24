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

package org.matrix.android.sdk.internal.session.applicationpassword.tasks

import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.session.applicationpassword.ApplicationPasswordAPI
import org.matrix.android.sdk.internal.session.applicationpassword.UpdateApplicationPasswordBody
import org.matrix.android.sdk.internal.task.Task
import javax.inject.Inject

internal interface UpdateApplicationPasswordTask : Task<UpdateApplicationPasswordTask.Params, Boolean> {
    data class Params(
            val oldPassword: String,
            val newPassword: String
    )
}

internal class DefaultUpdateApplicationPasswordTask @Inject constructor(
        private val applicationPasswordAPI: ApplicationPasswordAPI,
        private val loginByApplicationPasswordTask: LoginByApplicationPasswordTask,
        private val globalErrorReceiver: GlobalErrorReceiver
) : UpdateApplicationPasswordTask {
    override suspend fun execute(params: UpdateApplicationPasswordTask.Params): Boolean {
        val loginResult = try {
            loginByApplicationPasswordTask.execute(
                    LoginByApplicationPasswordTask.Params(
                            password = params.oldPassword
                    )
            )
        } catch (throwable: Throwable) {
            if (throwable is Failure.ServerError) throw throwable
            false
        }
        return when (loginResult) {
            true -> try {
                val result = executeRequest(globalErrorReceiver) {
                    applicationPasswordAPI.updateApplicationPassword(
                            UpdateApplicationPasswordBody(
                                    password = params.newPassword
                            )
                    )
                }.status
                result == "OK"
            } catch (throwable: Throwable) {
                if (throwable is Failure.ServerError) throw throwable
                false
            }
            false -> false
        }
    }
}
