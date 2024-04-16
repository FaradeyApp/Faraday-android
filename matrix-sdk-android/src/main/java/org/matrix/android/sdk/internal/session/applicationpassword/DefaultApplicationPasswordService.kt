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

import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.failure.MatrixError
import org.matrix.android.sdk.api.session.applicationpassword.ApplicationPasswordService
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.internal.auth.SessionParamsStore
import org.matrix.android.sdk.internal.util.generatePassword
import javax.inject.Inject

internal class DefaultApplicationPasswordService @Inject constructor(
        private val sessionParamsStore: SessionParamsStore,
        private val lightweightSettingsStorage: LightweightSettingsStorage
): ApplicationPasswordService {
    private fun throwIfNukePassword(password: String) {
        if (password == lightweightSettingsStorage.getNukePassword())
            throw Failure.ServerError(error = MatrixError(
                    code = MatrixError.M_FORBIDDEN,
                    message = "Nuke-password has been entered!"
            ), 403)
    }

    private fun throwIfIncorrectPassword(password: String) {
        if (password != lightweightSettingsStorage.getApplicationPassword())
            throw Failure.ServerError(error = MatrixError(
                    code = MatrixError.M_FORBIDDEN,
                    message = "Invalid password"
            ), 403)
    }

    private fun throwIfInvalidPassword(password: String) {
        if (password.length !in (4..15))
            throw Failure.ServerError(error = MatrixError(
                code = MatrixError.M_PASSWORD_WRONG_LENGTH,
                message = "Incorrect password entered"
            ), 403)

        var hasDigit = false
        val digitCharset = ('0'..'9')

        var hasSymbol = false
        val symbolCharset = listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')')

        var hasUpperCase = false
        val upperCaseCharset = ('A'..'Z')

        var hasLowerCase = false
        val lowerCaseCharset = ('a'..'z')
        password.forEach {
            when(it) {
                in digitCharset -> { hasDigit = true }
                in symbolCharset -> { hasSymbol = true }
                in upperCaseCharset -> { hasUpperCase = true }
                in lowerCaseCharset -> { hasLowerCase = true }
            }

            if (hasDigit && hasSymbol && hasLowerCase && hasUpperCase) return
        }

        val errorCode = when {
            !hasDigit -> MatrixError.M_PASSWORD_NO_DIGIT
            !hasSymbol -> MatrixError.M_PASSWORD_NO_SYMBOL
            !hasLowerCase -> MatrixError.M_PASSWORD_NO_LOWERCASE
            !hasUpperCase -> MatrixError.M_PASSWORD_NO_UPPERCASE
            else -> ""
        }

        throw Failure.ServerError(error = MatrixError(
                code = errorCode,
                message = "Incorrect password entered"
        ), 403)
    }

    override suspend fun setApplicationPassword(password: String): Boolean {
        throwIfNukePassword(password)
        throwIfInvalidPassword(password)

        if (lightweightSettingsStorage.getNukePassword() == null) {
            lightweightSettingsStorage.setNukePassword(generatePassword())
        }

        lightweightSettingsStorage.setApplicationPassword(password)
        return true
    }

    override suspend fun checkApplicationPasswordIsSet(): Boolean {
        return lightweightSettingsStorage.getApplicationPassword() != null
    }

    override suspend fun loginByApplicationPassword(password: String): Boolean {
        throwIfNukePassword(password)
        throwIfIncorrectPassword(password)
        return lightweightSettingsStorage.getApplicationPassword() == password
    }

    override suspend fun updateApplicationPassword(oldPassword: String, newPassword: String): Boolean {
        throwIfNukePassword(oldPassword)
        if (oldPassword != lightweightSettingsStorage.getApplicationPassword())
            throw Failure.ServerError(error = MatrixError(
                    code = MatrixError.M_FORBIDDEN,
                    message = "Invalid password"
            ), 403)

        throwIfInvalidPassword(newPassword)

        lightweightSettingsStorage.setApplicationPassword(newPassword)
        return true
    }

    override suspend fun deleteApplicationPassword(): Boolean {
        lightweightSettingsStorage.setApplicationPassword(null)
        return true
    }

    override suspend fun getNukePassword(): String {
        return lightweightSettingsStorage.getNukePassword()!!
    }

    override suspend fun getNukePasswordNotifications(): List<NukePasswordNotification> {
        // TODO: implement notifications
        return emptyList()
    }

    override suspend fun setNukePasswordNotificationViewed(id: String): Boolean {
        // TODO: implement notifications
        return false
    }

    override suspend fun clearSessionParamsStore() {
        sessionParamsStore.deleteAll()
    }
}
