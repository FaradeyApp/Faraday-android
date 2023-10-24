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

package im.vector.app.features.settings.passwordmanagement.passwordmanagementmain

import com.airbnb.mvrx.MavericksViewModelFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.core.di.MavericksAssistedViewModelFactory
import im.vector.app.core.di.hiltMavericksViewModelFactory
import im.vector.app.core.platform.VectorViewModel
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.session.Session

class VectorSettingsPasswordManagementViewModel @AssistedInject constructor(
        @Assisted initialState: VectorSettingsPasswordManagementViewState,
        private val session: Session
) : VectorViewModel<VectorSettingsPasswordManagementViewState, VectorSettingsPasswordManagementAction, VectorSettingsPasswordManagementViewEvents>(initialState) {

    @AssistedFactory
    interface Factory : MavericksAssistedViewModelFactory<VectorSettingsPasswordManagementViewModel, VectorSettingsPasswordManagementViewState> {
        override fun create(initialState: VectorSettingsPasswordManagementViewState): VectorSettingsPasswordManagementViewModel
    }

    companion object : MavericksViewModelFactory<VectorSettingsPasswordManagementViewModel, VectorSettingsPasswordManagementViewState> by hiltMavericksViewModelFactory()

    override fun handle(action: VectorSettingsPasswordManagementAction) {
        when (action) {
            is VectorSettingsPasswordManagementAction.OnClickNukePassword -> {
                withState {
                    _viewEvents.post(VectorSettingsPasswordManagementViewEvents.ShowPasswordDialog(it.nukePassword))
                }
            }
            is VectorSettingsPasswordManagementAction.DeletePassword -> deletePassword()
        }
    }

    init {
        fetchNukePassword()
    }

    private fun fetchNukePassword() {
        viewModelScope.launch {
            try {
                val nukePassword = session.applicationPasswordService().getNukePassword()
                setState {
                    copy(
                            nukePassword = nukePassword
                    )
                }
            } catch (failure: Throwable) {
                setState {
                    copy(
                            nukePassword = failure.message ?: "Error fetching nuke password"
                    )
                }
            }
        }
    }

    private fun deletePassword() = viewModelScope.launch {
        try {
            val isPasswordDeleted = session.applicationPasswordService().deleteApplicationPassword()
            if (isPasswordDeleted) {
                _viewEvents.post(VectorSettingsPasswordManagementViewEvents.OnPasswordDeleted)
            }
        } catch (failure: Throwable) {
            _viewEvents.post(VectorSettingsPasswordManagementViewEvents.ShowError(message = failure.message.orEmpty()))
        }
    }
}
