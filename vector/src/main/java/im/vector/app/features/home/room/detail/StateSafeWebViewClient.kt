/*
 * Copyright (c) 2024 New Vector Ltd
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

package im.vector.app.features.home.room.detail

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import timber.log.Timber

class StateSafeWebViewClient : WebViewClient() {
    var lastUrl: String? = null
        private set

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        Timber.d("LAST URL:" + lastUrl)
        lastUrl = request.url.toString()
        Timber.d("LAST URL:" + lastUrl)
        return super.shouldOverrideUrlLoading(view, request)
    }
}
