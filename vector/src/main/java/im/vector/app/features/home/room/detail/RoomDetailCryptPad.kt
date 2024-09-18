package im.vector.app.features.home.room.detail

private const val CRYPTPAD_URL = "https://cryptodocs.pm/"

class RoomDetailCryptPad : BaseWidgetActivity(
        webViewClientProvider = Companion,
        baseUrl = CRYPTPAD_URL
) {
    companion object : WebViewClientProvider {
        override val webViewClient = StateSafeWebViewClient()
    }
}
