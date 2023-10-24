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

package org.matrix.android.sdk.internal.session.applicationpassword

import org.matrix.android.sdk.api.session.applicationpassword.ApplicationPasswordService
import org.matrix.android.sdk.internal.auth.SessionParamsStore
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.CheckApplicationPasswordIsSetTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DeleteApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.GetNukePasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.GetPasswordNotificationsTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.LoginByApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.SetApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.SetNukePasswordNotificationViewedTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.UpdateApplicationPasswordTask
import javax.inject.Inject

internal class DefaultApplicationPasswordService @Inject constructor(
        private val sessionParamsStore: SessionParamsStore,
        private val checkApplicationPasswordIsSetTask: CheckApplicationPasswordIsSetTask,
        private val deleteApplicationPasswordTask: DeleteApplicationPasswordTask,
        private val getNukePasswordTask: GetNukePasswordTask,
        private val loginByApplicationPasswordTask: LoginByApplicationPasswordTask,
        private val setApplicationPasswordTask: SetApplicationPasswordTask,
        private val updateApplicationPasswordTask: UpdateApplicationPasswordTask,
        private val getPasswordNotificationsTask: GetPasswordNotificationsTask,
        private val setNukePasswordNotificationViewedTask: SetNukePasswordNotificationViewedTask
): ApplicationPasswordService {
    override suspend fun setApplicationPassword(password: String): Boolean {
        return setApplicationPasswordTask.execute(SetApplicationPasswordTask.Params(password = password))
    }

    override suspend fun checkApplicationPasswordIsSet(): Boolean {
        return checkApplicationPasswordIsSetTask.execute(Unit)
    }

    override suspend fun loginByApplicationPassword(password: String): Boolean {
       return loginByApplicationPasswordTask.execute(LoginByApplicationPasswordTask.Params(password = password))
    }

    override suspend fun updateApplicationPassword(oldPassword: String, newPassword: String): Boolean {
        return  updateApplicationPasswordTask.execute(UpdateApplicationPasswordTask.Params(oldPassword = oldPassword, newPassword = newPassword))
    }

    override suspend fun deleteApplicationPassword(): Boolean {
        return deleteApplicationPasswordTask.execute(Unit)
    }

    override suspend fun getNukePassword(): String {
        return getNukePasswordTask.execute(Unit)
    }

    override suspend fun getNukePasswordNotifications(): List<NukePasswordNotification> {
        return getPasswordNotificationsTask.execute(Unit)
    }

    override suspend fun setNukePasswordNotificationViewed(id: String): Boolean {
        return setNukePasswordNotificationViewedTask.execute(SetNukePasswordNotificationViewedTask.Params(id = id))
    }

    override suspend fun clearSessionParamsStore() {
        sessionParamsStore.deleteAll()
    }
}
