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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.core.extensions.cleanup
import im.vector.app.core.extensions.configureWith
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.databinding.FragmentGenericRecyclerBinding
import javax.inject.Inject

@AndroidEntryPoint
class NukePasswordNotificationsFragment :
        VectorBaseFragment<FragmentGenericRecyclerBinding>(), NukePasswordNotificationsController.Callback {

    @Inject lateinit var nukePasswordNotificationsController: NukePasswordNotificationsController
    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentGenericRecyclerBinding {
        return FragmentGenericRecyclerBinding.inflate(inflater, container, false)
    }

    private val viewModel: NukePasswordNotificationsViewModel by fragmentViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewEvents()
        nukePasswordNotificationsController.callback = this
        views.genericRecyclerView.configureWith(nukePasswordNotificationsController)
    }

    override fun onDestroyView() {
        nukePasswordNotificationsController.callback = null
        views.genericRecyclerView.cleanup()
        super.onDestroyView()
    }

    override fun invalidate() = withState(viewModel) { state ->
        nukePasswordNotificationsController.update(state)
    }

    private fun observeViewEvents() {
        viewModel.observeViewEvents { event ->
            when (event) {
                is NukePasswordNotificationsViewEvents.ShowError -> {
                    Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onNotificationSelected(notification: NukeNotificationModel) {
        viewModel.handle(NukePasswordNotificationsAction.OnNotificationSelected(notification = notification))
    }
}
