package im.vector.app.features.home.room.detail

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.util.Log
import im.vector.app.R

class RoomDetailTaigaBoard : AppCompatActivity (){
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_taiga_board)

        val webView: WebView = findViewById(R.id.webview)

        webView.webViewClient = WebViewClient()
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("https://tree.taiga.io")
    }

    fun handleBoard(){
        Log.i("Board Init", "test")
    }
}
