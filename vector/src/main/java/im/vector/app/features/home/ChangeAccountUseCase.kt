/*
 * Copyright (c) 2024 New Vector Ltd
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

package im.vector.app.features.home

import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.session.ConfigureAndStartSessionUseCase
import im.vector.app.features.login.ReAuthHelper
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import timber.log.Timber
import javax.inject.Inject

class ChangeAccountUseCase @Inject constructor(
        private val session: Session,
        private val reAuthHelper: ReAuthHelper,
        private val activeSessionHolder: ActiveSessionHolder,
        private val lightweightSettingsStorage: LightweightSettingsStorage,
        private val authenticationService: AuthenticationService,
        private val configureAndStartSessionUseCase: ConfigureAndStartSessionUseCase,
) {
    suspend fun execute(userId: String) {
        session.close()
        reAuthHelper.data = null
        val result = session.profileService()
                .reLoginMultiAccount(userId = userId, authenticationService.getSessionCreator()) {
                    reAuthHelper.data = it.password
                }
        Timber.d("Session created")
        activeSessionHolder.setActiveSession(result)
        lightweightSettingsStorage.setLastSessionHash(result.sessionId)
        authenticationService.reset()
        Timber.d("Configure and start session")
        configureAndStartSessionUseCase.execute(result)
        Timber.i("handleSelectAccountAction ${result.sessionParams.credentials}")
    }
}
