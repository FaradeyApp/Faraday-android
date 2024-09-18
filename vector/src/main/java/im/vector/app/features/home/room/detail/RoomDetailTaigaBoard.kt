package im.vector.app.features.home.room.detail

private const val TAIGA_URL = "https://testfaraday.onlyoffice.com/doceditor?fileId=552310&without_redirect=true"

class RoomDetailTaigaBoard : BaseWidgetActivity(
        webViewClientProvider = Companion,
        baseUrl = TAIGA_URL
) {
    companion object : WebViewClientProvider {
        override val webViewClient = StateSafeWebViewClient()
    }
}
