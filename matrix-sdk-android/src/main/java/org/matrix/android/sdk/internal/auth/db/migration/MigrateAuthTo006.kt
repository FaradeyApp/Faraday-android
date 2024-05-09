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

package org.matrix.android.sdk.internal.auth.db.migration

import io.realm.DynamicRealm
import org.matrix.android.sdk.internal.util.database.RealmMigrator
import org.matrix.android.sdk.internal.auth.db.LocalAccountEntityFields

internal class MigrateAuthTo006(realm: DynamicRealm) : RealmMigrator(realm, 6) {

    override fun doMigrate(realm: DynamicRealm) {
        realm.schema.create("LocalAccountEntity")
                .addField(LocalAccountEntityFields.USER_ID, String::class.java)
                .addPrimaryKey(LocalAccountEntityFields.USER_ID)
                .addField(LocalAccountEntityFields.TOKEN, String::class.java)
                .setNullable(LocalAccountEntityFields.TOKEN, true)
                .addField(LocalAccountEntityFields.USERNAME, String::class.java)
                .setNullable(LocalAccountEntityFields.USERNAME, true)
                .addField(LocalAccountEntityFields.PASSWORD, String::class.java)
                .setNullable(LocalAccountEntityFields.PASSWORD, true)
                .addField(LocalAccountEntityFields.HOME_SERVER_URL, String::class.java)
    }
}

