/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.widgets


import android.content.Context
import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.google.common.io.ByteStreams
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WebViewFileUploader(private val context: Context) {
    fun initFileUploader(webV: WebView?, callBackListener: CallBackListener?) {
        listener = callBackListener
        webView = webV
        webView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        initChooser(webView, callBackListener)
    }

    private fun initChooser(webView1: WebView?, callBackListener1: CallBackListener?) {
        webView = webView1
        listener = callBackListener1
        webView?.webChromeClient = object : WebChromeClient() {

            //For Android 5.0+
            override fun onShowFileChooser(webView: WebView, filePathCallback1: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                filePathCallback = filePathCallback1
                listener?.onOpenedChooser(filePathCallback!!)
                return true
            }
        }
    }

    fun setActivityResult(uri: Uri?) {
        uri ?: return
        getMultimediaFileByUri(uri)?.let {
            filePathCallback?.onReceiveValue(arrayOf(it))
        }
    }

    private fun getMultimediaFileByUri(uri: Uri): Uri? {
        try {
            val absolutePath = FileUtils(context).getPath(uri)
            val file = getCopiedFileByAbsolutePath(absolutePath)
            return Uri.fromFile(file)
        } catch (ex: Exception) {
            return null
        }
    }

    private fun getCopiedFileByAbsolutePath(absolutePath: String): File? {
        var inputStream: FileInputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            val copiesDirectory = "${context.cacheDir}/copies"
            val copiesFolder = File(copiesDirectory)
            if (!copiesFolder.exists()) {
                copiesFolder.mkdirs()
            }

            val originalFile = File(absolutePath)
            val copiedFile = File(copiesDirectory, originalFile.name)

            inputStream = FileInputStream(originalFile)
            outputStream = FileOutputStream(copiedFile)

            ByteStreams.copy(inputStream, outputStream)

            return copiedFile
        } catch (ex: Exception) {
            return null
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }
    interface CallBackListener {
        fun onOpenedChooser(filePathCallback: ValueCallback<Array<Uri>>)
    }

    companion object {
        var listener: CallBackListener? = null
        var filePathCallback: ValueCallback<Array<Uri>>? = null
        var webView: WebView? = null
    }
}
