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

package org.matrix.android.sdk.internal.auth.db

import io.realm.RealmConfiguration
import io.realm.kotlin.where
import org.matrix.android.sdk.internal.auth.db.query.where
import org.matrix.android.sdk.internal.database.awaitTransaction
import org.matrix.android.sdk.internal.di.AuthDatabase
import org.matrix.android.sdk.internal.session.profile.LocalAccount
import javax.inject.Inject

interface LocalAccountStore {
    suspend fun getAccounts(): List<LocalAccount>

    suspend fun addAccount(account: LocalAccount)

    suspend fun getAccount(userId: String): LocalAccount

    suspend fun updatePassword(userId: String, newPassword: String)

    suspend fun markAsOld(userId: String)

    suspend fun deleteAccount(userId: String)

    suspend fun clearAll()
}

class DefaultLocalAccountStore @Inject constructor(
        @AuthDatabase private val realmConfiguration: RealmConfiguration,
) : LocalAccountStore {
    override suspend fun addAccount(
            account: LocalAccount
    ) = awaitTransaction(realmConfiguration) { realm ->
        realm.insertOrUpdate(account.toEntity())
    }

    override suspend fun getAccount(userId: String): LocalAccount = awaitTransaction(realmConfiguration) { realm ->
        LocalAccountEntity.where(realm, userId).findFirst()!!.toLocalAccount()
    }

    override suspend fun updatePassword(userId: String, newPassword: String) = awaitTransaction(realmConfiguration) { realm ->
        val oldEntity = LocalAccountEntity.where(realm, userId).findFirst()!!.toLocalAccount()

        val newEntity = oldEntity.run {
            oldEntity.copy(
                    password = newPassword
            ).toEntity()
        }

        realm.insertOrUpdate(newEntity)
    }

    override suspend fun markAsOld(userId: String) = awaitTransaction(realmConfiguration) { realm ->
        val oldEntity = LocalAccountEntity.where(realm, userId).findFirst()!!.toLocalAccount()

        val newEntity = oldEntity.run {
            oldEntity.copy(
                    isNew = false
            ).toEntity()
        }

        realm.insertOrUpdate(newEntity)
    }

    override suspend fun deleteAccount(userId: String) = awaitTransaction(realmConfiguration) { realm ->
        LocalAccountEntity.where(realm, userId).findFirst()!!.deleteFromRealm()
    }

    override suspend fun clearAll() = awaitTransaction(realmConfiguration) { realm ->
        realm.delete(LocalAccountEntity::class.java)
    }

    override suspend fun getAccounts(): List<LocalAccount> = awaitTransaction(realmConfiguration) { realm ->
        realm.where<LocalAccountEntity>().findAll().map { it.toLocalAccount() }
    }
}
