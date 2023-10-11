package de.spiritcroc.android.sdk.internal.database.migration

import de.spiritcroc.android.sdk.internal.util.database.ScRealmMigrator
import io.realm.DynamicRealm
import org.matrix.android.sdk.internal.database.model.RoomSummaryEntityFields

internal class MigrateScSessionTo003(realm: DynamicRealm) : ScRealmMigrator(realm, 3) {

    override fun doMigrate(realm: DynamicRealm) {
        realm.schema.get("RoomSummaryEntity")
                ?.addField(RoomSummaryEntityFields.AGGREGATED_UNREAD_COUNT, Int::class.java)
                ?.addField(RoomSummaryEntityFields.AGGREGATED_NOTIFICATION_COUNT, Int::class.java)
    }
}
