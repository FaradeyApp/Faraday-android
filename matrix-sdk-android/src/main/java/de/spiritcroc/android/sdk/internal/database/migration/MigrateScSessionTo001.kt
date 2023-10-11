package de.spiritcroc.android.sdk.internal.database.migration

import de.spiritcroc.android.sdk.internal.util.database.ScRealmMigrator
import io.realm.DynamicRealm
import org.matrix.android.sdk.internal.database.model.RoomSummaryEntityFields

internal class MigrateScSessionTo001(realm: DynamicRealm) : ScRealmMigrator(realm, 1) {

    override fun doMigrate(realm: DynamicRealm) {
        realm.schema.get("RoomSummaryEntity")
                ?.addField(RoomSummaryEntityFields.MARKED_UNREAD, Boolean::class.java)
    }
}
