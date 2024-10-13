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

package org.matrix.android.sdk.internal.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject

class MultiServerInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        Timber.d("MultiServer request: ${chain.request()}")
        var request = chain.request()
        val tag = request.tag(MultiServerCredentials::class.java)

        if (tag != null) {
            val newRequestBuilder = request.newBuilder()
            tag.accessToken?.let { token ->
                newRequestBuilder.header(HttpHeaders.Authorization, "Bearer $token")
            }
            tag.homeserver?.let {
                it.toHttpUrlOrNull()?.let { base ->
                    newRequestBuilder.url(
                        request.url.newBuilder()
                                .scheme(base.scheme)
                                .host(base.host)
                                .port(base.port)
                                .build()
                    )
                }
            }
            request = newRequestBuilder.build()
        }

        Timber.d("Proceed multi-server request: $request")
        return chain.proceed(request)
    }
}
