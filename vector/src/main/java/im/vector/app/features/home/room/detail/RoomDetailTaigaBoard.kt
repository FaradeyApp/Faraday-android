package im.vector.app.features.home.room.detail

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import javax.inject.Inject

private const val TAIGA_URL = "http://teamddtawkczeuj6bxqrcrkuq6vasjwed3dleybrao4z4grae4rpg7ad.onion/"

@AndroidEntryPoint
class RoomDetailTaigaBoard : AppCompatActivity () {
    @Inject lateinit var stateSafeWebViewClient: StateSafeWebViewClient

    private lateinit var webView: WebView

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_taiga_board)

        webView = findViewById(R.id.webview)

        setupNavbar()

        webView.webViewClient = stateSafeWebViewClient
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptEnabled = true
        webView.loadUrl(stateSafeWebViewClient.lastUrl ?: TAIGA_URL)
    }

    @RequiresApi(Build.VERSION_CODES.M)
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

    fun handleBoard(){
        Log.i("Board Init", "test")
    }
}
