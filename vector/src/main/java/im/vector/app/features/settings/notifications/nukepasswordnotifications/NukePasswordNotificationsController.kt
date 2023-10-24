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

import com.airbnb.epoxy.EpoxyController
import javax.inject.Inject

class NukePasswordNotificationsController @Inject constructor() : EpoxyController() {

    var callback: Callback? = null

    private var viewState: NukePasswordNotificationsViewState? = null
    override fun buildModels() {
        val nonNullViewState = viewState ?: return
        buildNotificationsModels(notifications = nonNullViewState.asyncNotifications())
    }

    fun update(state: NukePasswordNotificationsViewState) {
        viewState = state
        requestModelBuild()
    }

    private fun buildNotificationsModels(
            notifications: List<NukeNotificationModel>?
    ) {
        val host = this
        notifications?.forEach { notification ->
            nukePasswordNotificationItem {
                id(notification.id)
                listener { host.callback?.onNotificationSelected(notification) }
                nukeNotificationModel(notification)
            }
        }
    }

    interface Callback {
        fun onNotificationSelected(notification: NukeNotificationModel)
    }
}
