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

package im.vector.app.features.useragent

import okhttp3.Interceptor
import okhttp3.Response

object VectorUserAgentInterceptor : UserAgentInterceptor {
    private var userAgent: String? = null

    override fun setUserAgent(userAgent: String?) {
        this.userAgent = userAgent
    }

    override fun intercept(chain: Interceptor.Chain): Response = chain.run {
        proceed(
                userAgent?.let {
                    request()
                            .newBuilder()
                            .removeHeader("User-Agent")
                            .addHeader("User-Agent", userAgent!!)
                            .build()
                } ?: request()
        )
    }
}
