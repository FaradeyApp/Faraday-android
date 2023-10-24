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

package im.vector.app.features.settings.notifications.nukepasswordnotifications

import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.Success
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.core.di.MavericksAssistedViewModelFactory
import im.vector.app.core.di.hiltMavericksViewModelFactory
import im.vector.app.core.platform.VectorViewModel
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.session.Session

class NukePasswordNotificationsViewModel @AssistedInject constructor(
        @Assisted initialState: NukePasswordNotificationsViewState,
        private val session: Session
) : VectorViewModel<NukePasswordNotificationsViewState, NukePasswordNotificationsAction, NukePasswordNotificationsViewEvents>(initialState) {

    @AssistedFactory
    interface Factory : MavericksAssistedViewModelFactory<NukePasswordNotificationsViewModel, NukePasswordNotificationsViewState> {
        override fun create(initialState: NukePasswordNotificationsViewState): NukePasswordNotificationsViewModel
    }

    companion object : MavericksViewModelFactory<NukePasswordNotificationsViewModel, NukePasswordNotificationsViewState> by hiltMavericksViewModelFactory()

    override fun handle(action: NukePasswordNotificationsAction) {
        when (action) {
            is NukePasswordNotificationsAction.OnNotificationSelected -> viewNotification(notification = action.notification)
        }
    }

    init {
        fetchNotifications()
    }

    private fun fetchNotifications() = viewModelScope.launch {
        try {
            val result = session.applicationPasswordService().getNukePasswordNotifications()
            setState {
                copy(
                        asyncNotifications = Success(result.map { it.toNukeNotificationModel() })
                )
            }
        } catch (failure: Throwable) {
            if (failure is Failure.ServerError) {
                _viewEvents.post(
                        NukePasswordNotificationsViewEvents.ShowError(
                                message = failure.error.message
                        )
                )
            }
        }
    }

    private fun viewNotification(notification: NukeNotificationModel) = viewModelScope.launch {
        if (notification.viewed) return@launch
        try {
            val isViewed = session.applicationPasswordService().setNukePasswordNotificationViewed(
                    id = notification.id
            )
            if (isViewed) {
                withState {
                    setState {
                        copy(
                                asyncNotifications = Success(it.asyncNotifications.invoke()?.map {
                                    if (it.id == notification.id) it.copy(viewed = true) else it
                                } ?: emptyList())
                        )
                    }
                }
            }
        } catch (failure: Throwable) {
            if (failure is Failure.ServerError) {
                _viewEvents.post(
                        NukePasswordNotificationsViewEvents.ShowError(
                                message = failure.error.message
                        )
                )
            }
        }
    }
}
