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

package org.matrix.android.sdk.internal.util

import java.security.SecureRandom

fun simpleEncrypt() {
}

fun simpleDecrypt() {
}

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 13

private val charset = ('A'..'Z') + ('a'..'z') + ('0'..'9')

fun generatePassword(): String {
    val secureRandom = SecureRandom()
    val length = MIN_PASSWORD_LENGTH + secureRandom.nextInt(MAX_PASSWORD_LENGTH - MIN_PASSWORD_LENGTH)
    return (1..length)
            .map { charset[secureRandom.nextInt(charset.size)] }
            .joinToString("")
}
