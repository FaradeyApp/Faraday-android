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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import im.vector.app.core.extensions.postLiveEvent
import im.vector.app.core.utils.LiveEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class I2PEventListener @Inject constructor() {
    private val _I2PEventLiveData = MutableLiveData<LiveEvent<Boolean>>()
    val I2PEventLiveData: LiveData<LiveEvent<Boolean>>
        get() = _I2PEventLiveData

    fun onConnectionEstablished() {
        _I2PEventLiveData.postLiveEvent(true)
    }

    fun onConnectionFailed() {
        _I2PEventLiveData.postLiveEvent(false)
    }

    fun resetConnection() {
        _I2PEventLiveData.postLiveEvent(false)
    }
}
