/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
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

import com.squareup.moshi.Moshi
import dagger.Lazy
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.internal.di.ProxyProvider
import org.matrix.android.sdk.internal.util.ensureTrailingSlash
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject

internal class RetrofitFactory @Inject constructor(
        private val moshi: Moshi,
        private val lightweightSettingsStorage: LightweightSettingsStorage
) {

    /**
     * Use only for authentication service.
     */
    fun create(okHttpClient: OkHttpClient, baseUrl: String): Retrofit {
        val proxy = ProxyProvider(lightweightSettingsStorage).providesProxy()
        val client = okHttpClient
                .newBuilder()
                .proxy(proxy)
                .apply {
                    val username = lightweightSettingsStorage.getProxyUsername()
                    val password = lightweightSettingsStorage.getProxyPassword()
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        val credentials = Credentials.basic(username, password)
                        val authenticator = HttpAuthenticator(credentials = credentials, "Proxy-Authorization")
                        proxyAuthenticator(authenticator)
                    }
                }
                .build()
        return Retrofit.Builder()
                .baseUrl(baseUrl.ensureTrailingSlash())
                .client(client)
                .addConverterFactory(UnitConverterFactory)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
    }

    fun create(okHttpClient: Lazy<OkHttpClient>, baseUrl: String): Retrofit {
        val proxy = ProxyProvider(lightweightSettingsStorage).providesProxy()
        val client = okHttpClient
                .get()
                .newBuilder()
                .proxy(proxy)
                .apply {
                    val username = lightweightSettingsStorage.getProxyUsername()
                    val password = lightweightSettingsStorage.getProxyPassword()
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        val credentials = Credentials.basic(username, password)
                        val authenticator = HttpAuthenticator(credentials = credentials, "Proxy-Authorization")
                        proxyAuthenticator(authenticator)
                    }
                }
                .build()
        return Retrofit.Builder()
                .baseUrl(baseUrl.ensureTrailingSlash())
                .callFactory(object : Call.Factory {
                    override fun newCall(request: Request): Call {
                        return client.newCall(request)
                    }
                })
                .addConverterFactory(UnitConverterFactory)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
    }
}
