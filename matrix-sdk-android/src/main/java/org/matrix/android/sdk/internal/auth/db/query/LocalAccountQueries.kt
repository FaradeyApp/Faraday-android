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

package org.matrix.android.sdk.internal.auth.db.query

import io.realm.Realm
import io.realm.RealmQuery
import io.realm.kotlin.where
import org.matrix.android.sdk.internal.auth.db.LocalAccountEntity
import org.matrix.android.sdk.internal.auth.db.LocalAccountEntityFields

internal fun LocalAccountEntity.Companion.where(realm: Realm, userId: String): RealmQuery<LocalAccountEntity> {
    return realm
            .where<LocalAccountEntity>()
            .equalTo(LocalAccountEntityFields.USER_ID, userId)
}
