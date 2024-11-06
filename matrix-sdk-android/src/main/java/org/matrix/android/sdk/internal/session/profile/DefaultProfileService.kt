/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.matrix.android.sdk.internal.session.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import com.zhuinden.monarchy.Monarchy
import io.realm.kotlin.where
import kotlinx.coroutines.withContext
import org.matrix.android.sdk.api.MatrixCoroutineDispatchers
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.auth.UserInteractiveAuthInterceptor
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.identity.ThreePid
import org.matrix.android.sdk.api.session.profile.ProfileService
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import org.matrix.android.sdk.api.session.profile.model.AccountLoginCredentials
import org.matrix.android.sdk.api.util.JsonDict
import org.matrix.android.sdk.api.util.MimeTypes
import org.matrix.android.sdk.api.util.Optional
import org.matrix.android.sdk.internal.auth.SessionCreator
import org.matrix.android.sdk.internal.auth.db.LocalAccountStore
import org.matrix.android.sdk.internal.auth.registration.RegistrationParams
import org.matrix.android.sdk.internal.database.model.PendingThreePidEntity
import org.matrix.android.sdk.internal.database.model.UserThreePidEntity
import org.matrix.android.sdk.internal.di.SessionDatabase
import org.matrix.android.sdk.internal.network.MultiServerCredentials
import org.matrix.android.sdk.internal.network.TimeOutInterceptor
import org.matrix.android.sdk.internal.session.content.FileUploader
import org.matrix.android.sdk.internal.session.sync.MultiServerSyncApi
import org.matrix.android.sdk.internal.session.sync.SyncPresence
import org.matrix.android.sdk.internal.session.user.UserStore
import org.matrix.android.sdk.internal.task.TaskExecutor
import org.matrix.android.sdk.internal.task.configureWith
import timber.log.Timber
import javax.inject.Inject

internal class DefaultProfileService @Inject constructor(
        private val syncApi: MultiServerSyncApi,
        private val taskExecutor: TaskExecutor,
        @SessionDatabase private val monarchy: Monarchy,
        private val coroutineDispatchers: MatrixCoroutineDispatchers,
        private val refreshUserThreePidsTask: RefreshUserThreePidsTask,
        private val getProfileInfoTask: GetProfileInfoTask,
        private val setDisplayNameTask: SetDisplayNameTask,
        private val setAvatarUrlTask: SetAvatarUrlTask,
        private val addThreePidTask: AddThreePidTask,
        private val validateSmsCodeTask: ValidateSmsCodeTask,
        private val finalizeAddingThreePidTask: FinalizeAddingThreePidTask,
        private val deleteThreePidTask: DeleteThreePidTask,
        private val addNewAccountTask: AddNewAccountTask,
        private val getLoginByTokenTask: GetLoginByTokenTask,
        private val reLoginInMultiAccountTask: ReLoginInMultiAccountTask,
        private val registerNewAccountTask: RegisterNewAccountTask,
        private val pendingThreePidMapper: PendingThreePidMapper,
        private val userStore: UserStore,
        private val fileUploader: FileUploader,
        private val authenticationService: AuthenticationService
) : ProfileService {
    private val localAccountStore: LocalAccountStore = authenticationService.getLocalAccountStore()

    override suspend fun getDisplayName(userId: String): Optional<String> {
        val params = GetProfileInfoTask.Params(userId)
        val data = getProfileInfoTask.execute(params)
        val displayName = data[ProfileService.DISPLAY_NAME_KEY] as? String
        return Optional.from(displayName)
    }

    override suspend fun setDisplayName(userId: String, newDisplayName: String) {
        withContext(coroutineDispatchers.io) {
            setDisplayNameTask.execute(SetDisplayNameTask.Params(userId = userId, newDisplayName = newDisplayName))
            userStore.updateDisplayName(userId, newDisplayName)
        }
    }

    override suspend fun updateAvatar(userId: String, newAvatarUri: Uri, fileName: String) {
        val response = fileUploader.uploadFromUri(newAvatarUri, fileName, MimeTypes.Jpeg)
        setAvatarUrlTask.execute(SetAvatarUrlTask.Params(userId = userId, newAvatarUrl = response.contentUri))
        userStore.updateAvatar(userId, response.contentUri)
    }

    override suspend fun getAvatarUrl(userId: String): Optional<String> {
        val params = GetProfileInfoTask.Params(userId)
        val data = getProfileInfoTask.execute(params)
        val avatarUrl = data[ProfileService.AVATAR_URL_KEY] as? String
        return Optional.from(avatarUrl)
    }

    override suspend fun getProfile(userId: String, homeServerUrl: String?): JsonDict {
        val params = GetProfileInfoTask.Params(userId, homeServerUrl = homeServerUrl)
        return getProfileInfoTask.execute(params)
    }

    override fun getThreePids(): List<ThreePid> {
        return monarchy.fetchAllMappedSync(
                { it.where<UserThreePidEntity>() },
                { it.asDomain() }
        )
    }

    override fun getThreePidsLive(refreshData: Boolean): LiveData<List<ThreePid>> {
        if (refreshData) {
            // Force a refresh of the values
            refreshThreePids()
        }

        return monarchy.findAllMappedWithChanges(
                { it.where<UserThreePidEntity>() },
                { it.asDomain() }
        )
    }

    private fun refreshThreePids() {
        refreshUserThreePidsTask
                .configureWith()
                .executeBy(taskExecutor)
    }

    override fun getPendingThreePids(): List<ThreePid> {
        return monarchy.fetchAllMappedSync(
                { it.where<PendingThreePidEntity>() },
                { pendingThreePidMapper.map(it).threePid }
        )
    }

    override fun getPendingThreePidsLive(): LiveData<List<ThreePid>> {
        return monarchy.findAllMappedWithChanges(
                { it.where<PendingThreePidEntity>() },
                { pendingThreePidMapper.map(it).threePid }
        )
    }

    override suspend fun addThreePid(threePid: ThreePid) {
        addThreePidTask.execute(AddThreePidTask.Params(threePid))
    }

    override suspend fun submitSmsCode(threePid: ThreePid.Msisdn, code: String) {
        validateSmsCodeTask.execute(ValidateSmsCodeTask.Params(threePid, code))
    }

    override suspend fun finalizeAddingThreePid(
            threePid: ThreePid,
            userInteractiveAuthInterceptor: UserInteractiveAuthInterceptor
    ) {
        finalizeAddingThreePidTask
                .execute(
                        FinalizeAddingThreePidTask.Params(
                                threePid = threePid,
                                userInteractiveAuthInterceptor = userInteractiveAuthInterceptor,
                                userWantsToCancel = false
                        )
                )
        refreshThreePids()
    }

    override suspend fun cancelAddingThreePid(threePid: ThreePid) {
        finalizeAddingThreePidTask
                .execute(
                        FinalizeAddingThreePidTask.Params(
                                threePid = threePid,
                                userInteractiveAuthInterceptor = null,
                                userWantsToCancel = true
                        )
                )
        refreshThreePids()
    }

    override suspend fun deleteThreePid(threePid: ThreePid) {
        deleteThreePidTask.execute(DeleteThreePidTask.Params(threePid))
        refreshThreePids()
    }

    private suspend fun getUnreadCount(homeServer: String, token: String): Int {
        return syncApi.sync(
                requestCredentials = MultiServerCredentials(token, homeServer),
                params = hashMapOf(
                        "timeout" to (6000L).toString(),
                        "set_presence" to SyncPresence.Offline.value
                ),
                readTimeOut = (6000L + 10_000).coerceAtLeast(TimeOutInterceptor.DEFAULT_LONG_TIMEOUT)
        ).rooms?.let {
            Timber.d("sync response multiserver: $it")
            var unreadCount = 0
            it.join.values.forEach { room ->
                room.unreadNotifications?.notificationCount?.let { unreadCount += it }
            }
            it.leave.values.forEach { room ->
                room.unreadNotifications?.notificationCount?.let { unreadCount += it }
            }
            Timber.d("Unread count is $unreadCount for the $homeServer:$token}")
            unreadCount
        } ?: 0
    }

    override suspend fun getMultipleAccount(userId: String): List<AccountItem> {
        return localAccountStore.getAccounts().filter {
            it.userId != userId
        }.map {
            try {
                val data = getProfile(it.userId, it.homeServerUrl)
                AccountItem(
                        userId = it.userId,
                        displayName = data.get(ProfileService.DISPLAY_NAME_KEY) as? String ?: "",
                        avatarUrl = data.get(ProfileService.AVATAR_URL_KEY) as? String,
                        unreadCount = getUnreadCount(it.homeServerUrl, it.token!!)
                )
            } catch (throwable: Throwable) {
                Timber.i("Error get multiple account data: $throwable")
                AccountItem(
                        userId = it.userId,
                        displayName = it.userId.removePrefix("@").split(':')[0],
                        avatarUrl = null
                )
            }
        }
    }

    override fun getAccounts(): LiveData<List<LocalAccount>> {
        return localAccountStore.getAccountsLive()
    }

    override suspend fun reLoginMultiAccount(userId: String, sessionCreator: SessionCreator, actionForNew: (LocalAccount) -> Unit): Session {
        return reLoginInMultiAccountTask.execute(
                ReLoginInMultiAccountTask.Params(userId, sessionCreator, actionForNew)
        )
    }

    override suspend fun getDeviceId(userId: String): String {
        val accounts = localAccountStore.getAccounts()
        val user = accounts.find { it.userId == userId }
        user?.deviceId?.let { return it }

        val ids = accounts.map { it.deviceId.orEmpty() }
        return authenticationService.generateDeviceId(userId, ids)
    }

    override suspend fun createAccount(userName: String?, password: String?, initialDeviceDisplayName: String?, homeServerConnectionConfig: HomeServerConnectionConfig): Boolean {
        val params = RegistrationParams(
                username = userName,
                password = password,
                initialDeviceDisplayName = initialDeviceDisplayName,
                deviceId = getDeviceId(userName.orEmpty())
        )
        val account = registerNewAccountTask.execute(RegisterNewAccountTask.Params(params, homeServerConnectionConfig)) ?: return false

        localAccountStore.addAccount(account)
        return true
    }

    override suspend fun addNewAccount(userName: String, password: String, homeServerUrl: String): Boolean {
        val account = addNewAccountTask.execute(
            AddNewAccountTask.Params(
                    username = userName,
                    password = password,
                    homeServerUrl = homeServerUrl,
                    deviceId = getDeviceId(userName)
            )
        ) ?: return false

        localAccountStore.addAccount(account)
        return true
    }

    override suspend fun storeAccount(account: LocalAccount) {
        localAccountStore.addAccount(account)
    }

    override suspend fun clearMultiAccount() {
        localAccountStore.clearAll()
    }

    override suspend fun getLoginByToken(token: String): AccountLoginCredentials {
        return getLoginByTokenTask.execute(
                GetLoginByTokenTask.Params(
                        token = token
                )
        )
    }

    companion object {
        const val APP_PREFIX = "Faraday"
    }
}

private fun UserThreePidEntity.asDomain(): ThreePid {
    return when (medium) {
        ThirdPartyIdentifier.MEDIUM_EMAIL -> ThreePid.Email(address)
        ThirdPartyIdentifier.MEDIUM_MSISDN -> ThreePid.Msisdn(address)
        else -> error("Invalid medium type")
    }
}
