/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.spiritcroc.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import javax.inject.Inject

/**
 * Based on org.matrix.android.sdk.internal.session.content.ThumbnailExtractor, but more useful for
 * rendering video thumbnails locally (instead of the SDK's focus on uploading thumbnails).
 * I.e. we re-use existing MediaData classes, and keep the SDK's class package-private.
 */
class ThumbnailExtractor @Inject constructor(
        private val context: Context
) {

    companion object {
        private const val DEBUG_THUMBNAIL_EXTRACTOR = true
    }


    fun extractThumbnail(file: File): Result<File>? {
        // In case we want to generate thumbnails for non-video files here as well, we need to fix below MIME-type detection.
        // Currently, it returns false for isMimeTypeVideo on mp4 videos
        /*
        val type = MimeTypeMap.getFileExtensionFromUrl(file.absolutePath)
        if (DEBUG_THUMBNAIL_EXTRACTOR) Timber.v("MIME type is $type: isVideo: ${type.isMimeTypeVideo()}")
        return if (type.isMimeTypeVideo()) {
            extractVideoThumbnail(file)
        } else {
            null
        }
         */
        // Currently, only video thumbnail generation supported
        return extractVideoThumbnail(file)
    }

    private fun getThumbnailCacheFile(videoFile: File): File {
        val thumbnailCacheDir = File(context.cacheDir, "localThumbnails")
        return File(thumbnailCacheDir, "${videoFile.canonicalPath}.jpg")
    }

    private fun extractVideoThumbnail(file: File): Result<File> {
        val thumbnailFile = getThumbnailCacheFile(file)
        if (thumbnailFile.exists()) {
            if (DEBUG_THUMBNAIL_EXTRACTOR) Timber.d("Return cached thumbnail ${thumbnailFile.canonicalPath}")
            return Result.success(thumbnailFile)
        }
        if (DEBUG_THUMBNAIL_EXTRACTOR) Timber.d("Generate thumbnail ${thumbnailFile.canonicalPath}")
        val mediaMetadataRetriever = MediaMetadataRetriever()
        try {
            mediaMetadataRetriever.setDataSource(context, Uri.fromFile(file))
            mediaMetadataRetriever.frameAtTime?.let { thumbnail ->
                val outputStream = ByteArrayOutputStream()
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                /*
                val thumbnailWidth = thumbnail.width
                val thumbnailHeight = thumbnail.height
                val thumbnailSize = outputStream.size()
                 */
                val tmpFile = File(thumbnailFile.parentFile, "${file.name}.part")
                tmpFile.parentFile?.mkdirs()
                FileOutputStream(tmpFile).use {
                    it.write(outputStream.toByteArray())
                }
                tmpFile.renameTo(thumbnailFile)
                thumbnail.recycle()
                outputStream.reset()
            } ?: run {
                Timber.e("Cannot extract video thumbnail at %s", file.canonicalPath)
                return Result.failure(Exception("Cannot extract video thumbnail at ${file.canonicalPath}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Cannot extract video thumbnail")
            return Result.failure(e)
        } finally {
            mediaMetadataRetriever.release()
        }
        return Result.success(thumbnailFile)
    }

}
