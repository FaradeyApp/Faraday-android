package de.spiritcroc.android.sdk.internal.database.migration

import de.spiritcroc.android.sdk.internal.util.database.ScRealmMigrator
import io.realm.DynamicRealm
import org.matrix.android.sdk.internal.database.model.ReactionAggregatedSummaryEntityFields

internal class MigrateScSessionTo005(realm: DynamicRealm) : ScRealmMigrator(realm, 5) {

    override fun doMigrate(realm: DynamicRealm) {
        realm.schema.get("ReactionAggregatedSummaryEntity")
                ?.addField(ReactionAggregatedSummaryEntityFields.URL, String::class.java)
    }
}
