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

import org.matrix.android.sdk.internal.network.NetworkConstants
import org.matrix.android.sdk.internal.session.profile.BaseRequestStatusResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

internal interface ApplicationPasswordAPI {

    /**
     * Set whether or not application password will be shown to a user after every app launch.
     */
    @POST(NetworkConstants.URI_API_PREFIX_PATH_V3 + "application_password")
    suspend fun setApplicationPassword(
            @Body body: SetApplicationPasswordBody
    ): BaseRequestStatusResponse

    /**
     * Check whether application password is set for a user.
     */
    @GET(NetworkConstants.URI_API_PREFIX_PATH_V3 + "application_password/login")
    suspend fun checkApplicationPasswordIsSet(): BaseRequestStatusResponse


    /**
     * Verify application password. In case nuke-password was entered, local cache,
     * user credentials and SessionParamsStore get cleared
     * and user is logged out.
     */
    @POST(NetworkConstants.URI_API_PREFIX_PATH_V3 + "application_password/login")
    suspend fun loginByApplicationPassword(
            @Body body: LoginByApplicationPasswordBody
    ): BaseRequestStatusResponse


    /**
     * Change application password in password settings.
     */
    @PUT(NetworkConstants.URI_API_PREFIX_PATH_V3 + "application_password")
    suspend fun updateApplicationPassword(
            @Body body: UpdateApplicationPasswordBody
    ): BaseRequestStatusResponse


    /**
     * Delete application password for a user once switch in VectorSettingsPasswordManagementFragment is turned off.
     */
    @DELETE(NetworkConstants.URI_API_PREFIX_PATH_V3 + "application_password")
    suspend fun deleteApplicationPassword(): BaseRequestStatusResponse

    /**
     * Fetch nuke-password from server.
     */
    @GET(NetworkConstants.URI_API_PREFIX_PATH_V3 + "nuke_password")
    suspend fun getNukePassword(): NukePasswordResponse


    /**
     * Fetch notifications about nuke-password activation.
     */
    @GET(NetworkConstants.URI_API_PREFIX_PATH_V3 + "notice")
    suspend fun getNukePasswordNotifications(): List<NukePasswordNotification>

    /**
     * Mark nuke-password notification as viewed.
     */
    @POST(NetworkConstants.URI_API_PREFIX_PATH_V3 + "notice/{id}")
    suspend fun setNukePasswordNotificationViewed(
            @Path("id") id: String
    ): BaseRequestStatusResponse
}
