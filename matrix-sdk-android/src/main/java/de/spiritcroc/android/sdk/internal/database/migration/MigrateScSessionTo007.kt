package de.spiritcroc.android.sdk.internal.database.migration

import de.spiritcroc.android.sdk.internal.util.database.ScRealmMigrator
import io.realm.DynamicRealm
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.internal.database.model.RoomSummaryEntity
import org.matrix.android.sdk.internal.database.model.RoomSummaryEntityFields

internal class MigrateScSessionTo007(realm: DynamicRealm) : ScRealmMigrator(realm, 7) {

    override fun doMigrate(realm: DynamicRealm) {
        realm.schema.get("RoomSummaryEntity")
                ?.addField(RoomSummaryEntityFields.TREAT_AS_UNREAD_LEVEL, Int::class.java)
                ?.transform { obj ->
                    val unreadCount = tryOrNull { obj.getInt(RoomSummaryEntityFields.UNREAD_COUNT) }
                    val treatAsUnreadLevel = RoomSummaryEntity.calculateUnreadLevel(
                            obj.getInt(RoomSummaryEntityFields.HIGHLIGHT_COUNT),
                            obj.getInt(RoomSummaryEntityFields.NOTIFICATION_COUNT),
                            obj.getBoolean(RoomSummaryEntityFields.MARKED_UNREAD),
                            unreadCount
                    )
                    obj.setInt(RoomSummaryEntityFields.TREAT_AS_UNREAD_LEVEL, treatAsUnreadLevel)
                }
                ?.addIndex(RoomSummaryEntityFields.TREAT_AS_UNREAD_LEVEL)
    }
}
