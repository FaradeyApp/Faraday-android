package im.vector.app.features.html

import android.text.Spanned
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.html.HtmlTag
import io.noties.markwon.html.MarkwonHtmlRenderer
import io.noties.markwon.html.TagHandler
import java.util.Collections

/**
 * https://github.com/noties/Markwon/issues/181#issuecomment-571296484
 */

class DetailsTagHandler: TagHandler() {

    override fun handle(visitor: MarkwonVisitor, renderer: MarkwonHtmlRenderer, tag: HtmlTag) {
        var summaryEnd = -1
        var summaryStart = -1
        for (child in tag.asBlock.children()) {

            if (!child.isClosed) {
                continue
            }

            if ("summary" == child.name()) {
                summaryStart = child.start()
                summaryEnd = child.end()
            }

            val tagHandler = renderer.tagHandler(child.name())
            if (tagHandler != null) {
                tagHandler.handle(visitor, renderer, child)
            } else if (child.isBlock) {
                visitChildren(visitor, renderer, child.asBlock)
            }
        }

        if (summaryEnd > -1 && summaryStart > -1) {
            val summary = visitor.builder().subSequence(summaryStart, summaryEnd)
            val summarySpan = DetailsSummarySpan(summary)
            visitor.builder().setSpan(summarySpan, summaryStart, summaryEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            visitor.builder().setSpan(DetailsParsingSpan(summarySpan), tag.start(), tag.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    override fun supportedTags(): Collection<String> {
        return Collections.singleton("details")
    }
}

data class DetailsSummarySpan(val text: CharSequence)

enum class DetailsSpanState { DORMANT_CLOSE, DORMANT_OPEN, CLOSED, OPENED }

data class DetailsParsingSpan(
    val summary: DetailsSummarySpan,
    var state: DetailsSpanState = DetailsSpanState.CLOSED
)
