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

package im.vector.app.core.settings.connectionmethods.onion


import io.matthewnelson.topl_service.TorServiceController
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton



/**
 * Class provides access to TorServiceController generated in VectorApplication class.
 */
@Singleton
class TorService @Inject constructor(
        val lightweightSettingsStorage: LightweightSettingsStorage,
) {
    var proxyPort = 0
    var isProxyRunning = false

    private fun startTor() {
        Timber.d("Start tor")
        TorServiceController.startTor()
    }

    private fun stopTor() {
        Timber.d("Stop tor")
        TorServiceController.stopTor()
    }

    fun switchTorPrefState(newActive: Boolean) {
        if (newActive) {
            startTor()
        } else {
            stopTor()
        }
    }
}
