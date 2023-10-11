package org.matrix.android.sdk.api.session.room.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.matrix.android.sdk.api.session.room.model.message.ImageInfo

@JsonClass(generateAdapter = true)
data class EmoteImage(
        @Json(name = "url") val url: String,
        @Json(name = "body") val body: String? = null,
        @Json(name = "info") val info: ImageInfo? = null,
        @Json(name = "usage") val usage: List<String>? = null,
)
