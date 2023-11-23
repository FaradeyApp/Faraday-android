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

package im.vector.app.features.login

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.airbnb.mvrx.activityViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.extensions.observeEvent
import im.vector.app.core.settings.connectionmethods.onion.TorEvent
import im.vector.app.features.settings.connectionmethod.ConnectionSettingsBaseFragment
import org.matrix.android.sdk.api.util.ConnectionType

@AndroidEntryPoint
class LoginConnectionSettingsFragment : ConnectionSettingsBaseFragment() {

    private val loginViewModel: LoginViewModel by activityViewModel()

    private var torLoggingDialog: AlertDialog? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        initDialog()
    }

    override fun bindPref() {
        super.bindPref()
        savePreference?.let { pref ->
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                when (connectionTypePreference?.type ?: return@OnPreferenceClickListener true) {
                    ConnectionType.MATRIX -> {
                        when (switchUseProxyPreference?.isChecked ?: return@OnPreferenceClickListener true) {
                            true -> {
                                if (proxyFieldsAreValid()) {
                                    getStarted(ConnectionType.MATRIX)
                                }
                            }
                            false -> {
                                disableProxy()
                                getStarted(ConnectionType.MATRIX)
                            }
                        }
                    }

                    ConnectionType.ONION -> {
                        when (torService.isProxyRunning) {
                            true -> getStarted(ConnectionType.ONION)
                            false -> {
                                torService.switchTorPrefState(true)
                                observeTorEvents()
                            }
                        }
                    }

                    ConnectionType.I2P -> {
                        getStarted(ConnectionType.I2P)
                    }
                }
                true
            }
        }
    }

    private fun initDialog() {
        torLoggingDialog = MaterialAlertDialogBuilder(requireContext())
                .setCancelable(false)
                .create()
    }

    override fun observeTorEvents() {
        torEventListener.torEventLiveData.observeEvent(this) { torEvent ->
            when (torEvent) {
                is TorEvent.ConnectionEstablished -> {
                    torLoggingDialog?.dismiss()
                    getStarted(ConnectionType.ONION)
                }

                is TorEvent.ConnectionFailed -> {
                    torLoggingDialog?.setMessage(getString(R.string.tor_connection_failed))
                    if (torLoggingDialog?.isShowing == false) torLoggingDialog?.show()
                    torLoggingDialog?.setCancelable(true)
                }

                is TorEvent.TorLogEvent -> {
                    torLoggingDialog?.setMessage(torEvent.message)
                    if (torLoggingDialog?.isShowing == false) {
                        torLoggingDialog?.show()
                    }
                }
            }
        }
    }

    private fun getStarted(connectionType: ConnectionType) {
        if (connectionType != ConnectionType.ONION && torService.isProxyRunning) {
            torService.switchTorPrefState(false)
        }
        lightweightSettingsStorage.setConnectionType(connectionType)
        loginViewModel.handle(LoginAction.OnGetStarted(resetLoginConfig = false))
    }
}
