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

import io.matthewnelson.topl_service_base.TorPortInfo
import io.matthewnelson.topl_service_base.TorServiceEventBroadcaster
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import timber.log.Timber
import javax.inject.Inject

class TorEventBroadcaster @Inject constructor(
        private val torService: TorService,
        private val torEventListener: TorEventListener,
        private val lightweightSettingsStorage: LightweightSettingsStorage
) : TorServiceEventBroadcaster() {

    override fun broadcastPortInformation(torPortInfo: TorPortInfo) {
        Timber.d("PortInfo: " + torPortInfo.httpPort)
        if (torPortInfo.httpPort != null) {
            val port = Integer.valueOf(torPortInfo.httpPort!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1])
            torService.isProxyRunning = true
            torService.proxyPort = port

            lightweightSettingsStorage.setProxyPort(port)
            Timber.i("torServiceEventBroadcasterListener $port")
            torEventListener.onConnectionEstablished()
        } else {
            torService.isProxyRunning = false
            torEventListener.onConnectionFailed()
        }
    }

    override fun broadcastBandwidth(bytesRead: String, bytesWritten: String) {
        Timber.v("bandwidth: $bytesRead, $bytesWritten")
    }

    override fun broadcastDebug(msg: String) {
        Timber.d("debug: $msg")
    }

    override fun broadcastException(msg: String?, e: Exception) {
        Timber.e("exception: " + msg + ", " + e.message)
        torEventListener.onConnectionFailed()
    }

    override fun broadcastLogMessage(logMessage: String?) {
        Timber.d(logMessage.orEmpty())
    }

    override fun broadcastNotice(msg: String) {
        Timber.v(msg)
        if(msg.startsWith("NOTICE|BaseEventListener|Bootstrapped")) {
            msg.substringAfterLast('|').substringBeforeLast("%")
            val logMessage = msg.substringAfterLast('|').substringBeforeLast("%")
            torEventListener.onTorLogEvent("Tor: $logMessage%")
        }
    }

    override fun broadcastTorState(state: String, networkState: String) {
        Timber.d("$state, $networkState")
        torEventListener.onTorLogEvent("$state, $networkState")
    }
}
