package de.spiritcroc.android.sdk.internal.database.migration

import de.spiritcroc.android.sdk.internal.util.database.ScRealmMigrator
import io.realm.DynamicRealm
import org.matrix.android.sdk.internal.database.model.RoomSummaryEntityFields

internal class MigrateScSessionTo004(realm: DynamicRealm) : ScRealmMigrator(realm, 4) {

    override fun doMigrate(realm: DynamicRealm) {
        realm.schema.get("RoomSummaryEntity")
                ?.setNullable(RoomSummaryEntityFields.UNREAD_COUNT, true)
    }
}
