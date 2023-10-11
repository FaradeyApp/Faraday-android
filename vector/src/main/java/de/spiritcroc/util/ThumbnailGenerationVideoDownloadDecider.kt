package de.spiritcroc.util

import im.vector.app.features.home.room.detail.timeline.item.MessageInformationData
import org.matrix.android.sdk.api.extensions.orFalse
import javax.inject.Inject

class ThumbnailGenerationVideoDownloadDecider @Inject constructor() /*val context: Context, val vectorPreference: VectorPreference)*/ {

    fun enableVideoDownloadForThumbnailGeneration(informationData: MessageInformationData? = null): Boolean {
        // Disable automatic download for public rooms
        if (informationData?.isPublic.orFalse()) {
            return false
        }

        // Disable automatic download for metered connections
        /* Since this may change later, better check in VectorGlideModelLoader directly
        context.getSystemService<ConnectivityManager>()!!.apply {
            if (isActiveNetworkMetered) {
                return false
            }
        }
         */

        // Else, enable automatic download
        return true
    }
}
