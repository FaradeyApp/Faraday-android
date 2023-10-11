package de.spiritcroc.android.sdk.internal.util.database

import io.realm.DynamicRealm
import org.matrix.android.sdk.internal.util.database.RealmMigrator
import timber.log.Timber

internal abstract class ScRealmMigrator(private val realm: DynamicRealm,
                               private val targetScSchemaVersion: Int): RealmMigrator(realm, 0) {
    override fun perform() {
        Timber.d("Migrate ${realm.configuration.realmFileName} to SC-version $targetScSchemaVersion")
        doMigrate(realm)
    }
}
