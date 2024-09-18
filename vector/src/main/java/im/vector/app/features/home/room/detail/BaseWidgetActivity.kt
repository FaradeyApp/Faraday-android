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

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.webkit.WebView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import im.vector.app.R
import timber.log.Timber

interface WebViewClientProvider {
    val webViewClient: StateSafeWebViewClient
}

open class BaseWidgetActivity(private val webViewClientProvider: WebViewClientProvider, private val baseUrl: String) : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_webview)

        webView = findViewById(R.id.webview)

        setupNavbar()

        val webViewClient = webViewClientProvider.webViewClient

        webView.webViewClient = webViewClient
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptEnabled = true
        Timber.d("last url ${webViewClient.lastUrl}")
        webView.loadUrl(webViewClient.lastUrl ?: baseUrl)
    }

    private fun setupNavbar() {
        findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
        findViewById<ImageButton>(R.id.copy_btn).setOnClickListener {
            webView.evaluateJavascript("""
                function getSelectedText() {
                var selectedText = '';
                if (window.getSelection) {
                    selectedText = window.getSelection().toString();
                } else if (document.selection && document.selection.type != 'Control') {
                    selectedText = document.selection.createRange().text;
                }
                return selectedText;
            }""".trimIndent()) { }

            webView.evaluateJavascript("getSelectedText()") {
                val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied text", it.drop(1).dropLast(1))
                clipboard.setPrimaryClip(clip)
            }
        }
        findViewById<ImageButton>(R.id.paste_btn).setOnClickListener {
            val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text ?: return@setOnClickListener

            webView.evaluateJavascript("""
                function pasteTextAtCursor(text) {
                    var activeElement = document.activeElement;
                    if (activeElement && ['textarea', 'input'].includes(activeElement.tagName.toLowerCase())) {
                        var start = activeElement.selectionStart;
                        var end = activeElement.selectionEnd;
                        var value = activeElement.value;
                        var newValue = value.substring(0, start) + text + value.substring(end);
                        activeElement.value = newValue;
                        activeElement.setSelectionRange(start + text.length, start + text.length);
                    }
                }
            """.trimIndent()) { }

            val escapedText = escapeJsString(text.toString())
            webView.evaluateJavascript("(function(){document.activeElement.value = '$escapedText'})()") { }
        }
    }

    private fun escapeJsString(text: String): String {
        return text.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }
}
