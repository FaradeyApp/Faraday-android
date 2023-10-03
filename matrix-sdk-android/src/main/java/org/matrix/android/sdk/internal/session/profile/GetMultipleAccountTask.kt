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




import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.task.Task
import timber.log.Timber
import javax.inject.Inject

internal interface GetMultipleAccountTask : Task<GetMultipleAccountTask.Params, List<AccountItem>> {
    data class Params(
            val homeServerConnectionConfig: HomeServerConnectionConfig
    )
}

internal class DefaultGetMultipleAccountTask @Inject constructor(
        private val profileAPI: ProfileAPI,
        private val globalErrorReceiver: GlobalErrorReceiver
) : GetMultipleAccountTask {
    override suspend fun execute(params: GetMultipleAccountTask.Params): List<AccountItem> {
        var result = emptyList<AccountItem>()
        try {
            result = executeRequest(globalErrorReceiver) {
                profileAPI.getMultiAccount()
            }.map { it.toAccountItem() }
        }
        catch (throwable: Throwable) {
            Timber.i("GetMultipleAccountTask Throwable $throwable")
        }
        return result
    }
}


