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

import dagger.Binds
import dagger.Module
import dagger.Provides
import org.matrix.android.sdk.api.session.applicationpassword.ApplicationPasswordService
import org.matrix.android.sdk.internal.session.SessionScope
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.CheckApplicationPasswordIsSetTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DefaultCheckApplicationPasswordIsSetTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DefaultDeleteApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DefaultGetNukePasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DefaultGetPasswordNotificationsTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DefaultLoginByApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DefaultSetApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DefaultSetNukePasswordNotificationViewedTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DefaultUpdateApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.DeleteApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.GetNukePasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.GetPasswordNotificationsTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.LoginByApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.SetApplicationPasswordTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.SetNukePasswordNotificationViewedTask
import org.matrix.android.sdk.internal.session.applicationpassword.tasks.UpdateApplicationPasswordTask
import retrofit2.Retrofit

@Module
internal abstract class ApplicationPasswordModule {

    @Module
    companion object {
        @Provides
        @JvmStatic
        @SessionScope
        fun providesApplicationPasswordAPI(retrofit: Retrofit): ApplicationPasswordAPI {
            return retrofit.create(ApplicationPasswordAPI::class.java)
        }
    }


    @Binds
    abstract fun bindCheckApplicationPasswordIsSetTask(task: DefaultCheckApplicationPasswordIsSetTask): CheckApplicationPasswordIsSetTask

    @Binds
    abstract fun bindDeleteApplicationPasswordTask(task: DefaultDeleteApplicationPasswordTask): DeleteApplicationPasswordTask

    @Binds
    abstract fun bindGetNukePasswordTask(task: DefaultGetNukePasswordTask): GetNukePasswordTask

    @Binds
    abstract fun bindLoginByApplicationPasswordTask(task: DefaultLoginByApplicationPasswordTask): LoginByApplicationPasswordTask

    @Binds
    abstract fun bindSetApplicationPasswordTask(task: DefaultSetApplicationPasswordTask): SetApplicationPasswordTask

    @Binds
    abstract fun bindUpdateApplicationPasswordTask(task: DefaultUpdateApplicationPasswordTask): UpdateApplicationPasswordTask

    @Binds
    abstract fun bindGetPasswordNotificationsTask(task: DefaultGetPasswordNotificationsTask): GetPasswordNotificationsTask

    @Binds
    abstract fun bindSetNukePasswordNotificationViewedTask(task: DefaultSetNukePasswordNotificationViewedTask): SetNukePasswordNotificationViewedTask

    @Binds
    abstract fun bindApplicationPasswordService(service: DefaultApplicationPasswordService): ApplicationPasswordService


}
