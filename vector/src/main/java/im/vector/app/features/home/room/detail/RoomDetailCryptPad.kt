package im.vector.app.features.home.room.detail

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import javax.inject.Inject

private const val CRYPTPAD_URL = "https://cryptodocs.pm/"

@AndroidEntryPoint
class RoomDetailCryptPad : AppCompatActivity() {
    @Inject lateinit var stateSafeWebViewClient: StateSafeWebViewClient

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cryptpad)

        val webView: WebView = findViewById(R.id.webview)

        webView.webViewClient = stateSafeWebViewClient
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptEnabled = true
        webView.loadUrl(stateSafeWebViewClient.lastUrl ?: CRYPTPAD_URL)
    }

    fun handleBoard(){
        Log.i("Cryptpad Init", "test")
    }
}
