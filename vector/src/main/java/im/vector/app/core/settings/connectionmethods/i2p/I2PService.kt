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

package im.vector.app.core.settings.connectionmethods.i2p

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.i2p.client.I2PClientFactory
import net.i2p.client.I2PSession
import net.i2p.client.I2PSessionException
import net.i2p.data.Destination
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

class I2PService(input: String, output: String, private val listener: I2PEventListener) {
    val session: I2PSession
    private val destination: Destination

    init {
        val clientProperties = Properties()
        clientProperties.setProperty("i2cp.closeIdleTime", "1800000")
        clientProperties.setProperty("i2cp.tcp.host", "113.30.191.89")
        clientProperties.setProperty("i2cp.tcp.port", "7654")
        val outputStream: OutputStream = FileOutputStream(output)
        val inputStream: InputStream = FileInputStream(input)
        val client = I2PClientFactory.createClient()
        destination = client.createDestination(outputStream)
        session = client.createSession(inputStream, clientProperties)
    }

    fun connect() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                session.connect()
            }.invokeOnCompletion {
                listener.onConnectionEstablished()
                Timber.tag("I2PBroadcaster").d("I2P session connected")
            }
        } catch (e: I2PSessionException) {
            Timber.tag("I2PBroadcaster").d(e.stackTraceToString())
        }
    }

    fun disconnect() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                session.destroySession()
            }.invokeOnCompletion {
                listener.onConnectionFailed()
                Timber.tag("I2PBroadcaster").d("I2P session disconnected")
            }
        } catch (e: I2PSessionException) {
            Timber.tag("I2PBroadcaster").d(e.stackTraceToString())
        }
    }
}
