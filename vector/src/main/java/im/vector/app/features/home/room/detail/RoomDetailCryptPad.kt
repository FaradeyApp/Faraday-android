package im.vector.app.features.home.room.detail

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import im.vector.app.R

class RoomDetailCryptPad : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cryptpad)

        val webView: WebView = findViewById(R.id.webview)

        webView.webViewClient = WebViewClient()
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("https://cryptodocs.pm/")
    }

    fun handleBoard(){
        Log.i("Cryptpad Init", "test")
    }
}
