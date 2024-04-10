/*
 * Copyright (c) 2024 New Vector Ltd
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

package org.matrix.android.sdk.internal.session.profile

import com.zhuinden.monarchy.Monarchy
import io.realm.kotlin.where
import org.matrix.android.sdk.internal.database.model.LocalAccountEntity
import org.matrix.android.sdk.internal.database.model.toLocalAccount
import org.matrix.android.sdk.internal.di.SessionDatabase
import org.matrix.android.sdk.internal.util.awaitTransaction
import javax.inject.Inject

internal interface LocalAccountStore {
    suspend fun getAccounts(): List<LocalAccount>
    suspend fun addAccount(
            userId: String,
            username: String? = null,
            password: String? = null,
            token: String? = null
    )

    suspend fun getAccount(userId: String): LocalAccount
}

internal class DefaultLocalAccountStore @Inject constructor(
        @SessionDatabase private val monarchy: Monarchy,
) : LocalAccountStore {
    override suspend fun addAccount(userId: String, username: String?, password: String?, token: String?) = monarchy.awaitTransaction { realm ->
        val accountEntity = LocalAccountEntity(userId, token, username, password)
        realm.insertOrUpdate(accountEntity)
    }

    override suspend fun getAccount(userId: String): LocalAccount = monarchy.awaitTransaction { realm ->
        realm.where<LocalAccountEntity>().findFirst()!!.toLocalAccount()
    }

    override suspend fun getAccounts(): List<LocalAccount> = monarchy.awaitTransaction { realm ->
        realm.where<LocalAccountEntity>().findAll().map { it.toLocalAccount() }
    }
}
