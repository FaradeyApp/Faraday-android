package org.matrix.android.sdk.api.session.room.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.matrix.android.sdk.api.session.room.powerlevels.Role

/**
 * Class representing the EventType.ROOM_EMOTE state event content.
 */
@JsonClass(generateAdapter = true)
data class RoomEmoteContent(
        @Json(name = "images") val images: Map<String, EmoteImage>? = null,
        @Json(name = "pack") val pack: RoomEmotePackContent? = null,
) {
    companion object {
        const val USAGE_EMOTICON = "emoticon"
        const val USAGE_STICKER = "sticker"
    }
}

@JsonClass(generateAdapter = true)
data class RoomEmotePackContent(
        @Json(name = "display_name") val displayName: String? = null,
        @Json(name = "avatar_url") val avatarUrl: String? = null,
        @Json(name = "usage") val usage: Array<String>? = null,
        @Json(name = "attribution") val attribution: String? = null,
)
