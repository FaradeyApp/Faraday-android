package im.vector.app.features.html

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.image.AsyncDrawableScheduler

/**
 * https://github.com/noties/Markwon/issues/181#issuecomment-571296484
 */

class DetailsTagPostProcessor constructor(
        private val eventHtmlRenderer: EventHtmlRenderer
) : AbstractMarkwonPlugin() {

    override fun afterSetText(textView: TextView) {
        postProcessDetails(SpannableStringBuilder(textView.text), textView, true)
    }

    /**
     * Post-process details statements in the text. They act like `<spoiler>` or `<cut>` tag in some websites
     * @param spanned text to be modified to cut out details tags and insert replacements instead of them
     * @param view resulting text view to accept the modified spanned string
     * @param onBind whether we call this externally or internally
     */
    private fun postProcessDetails(spanned: SpannableStringBuilder, view: TextView, onBind: Boolean) {
        val spans = spanned.getSpans(0, spanned.length, DetailsParsingSpan::class.java)
        spans.sortBy { spanned.getSpanStart(it) }

        // if we have no details, proceed as usual (single text-view)
        if (spans.isNullOrEmpty()) {
            // no details
            return
        }

        for (span in spans) {
            val startIdx = spanned.getSpanStart(span)
            val endIdx = spanned.getSpanEnd(span)

            val summaryStartIdx = spanned.getSpanStart(span.summary)
            val summaryEndIdx = spanned.getSpanEnd(span.summary)

            // details tags can be nested, skip them if they were hidden
            if (startIdx == -1 || endIdx == -1) {
                continue
            }

            // On re-bind, reset span state
            if (onBind) {
                span.state = when (span.state) {
                    DetailsSpanState.DORMANT_CLOSE -> DetailsSpanState.CLOSED
                    DetailsSpanState.DORMANT_OPEN -> DetailsSpanState.OPENED
                    else -> span.state
                }
            }

            // replace text inside spoiler tag with just spoiler summary that is clickable
            val summaryText = when (span.state) {
                // Make sure to not convert the summary to string by accident, to not lose existing spans (like clickable links)
                DetailsSpanState.CLOSED -> {
                    SpannableStringBuilder(span.summary.text).apply {
                        insert(0, "▶ ")
                        if (endIdx < spanned.length-1) {
                            append("\n\n")
                        }
                    }
                }
                DetailsSpanState.OPENED -> {
                    SpannableStringBuilder(span.summary.text).apply {
                        insert(0, "▼ ")
                        if (endIdx < spanned.length-1) {
                            append("\n\n")
                        }
                    }
                }
                else -> ""
            }

            when (span.state) {

                DetailsSpanState.CLOSED -> {
                    span.state = DetailsSpanState.DORMANT_CLOSE
                    spanned.removeSpan(span.summary) // will be added later

                    // spoiler tag must be closed, all the content under it must be hidden

                    // retrieve content under spoiler tag and hide it
                    // if it is shown, it should be put in blockquote to distinguish it from text before and after
                    val innerSpanned = spanned.subSequence(summaryEndIdx, endIdx) as SpannableStringBuilder
                    spanned.replace(summaryStartIdx, endIdx, summaryText)
                    spanned.setSpan(span.summary, startIdx, startIdx + summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // expand text on click
                    val wrapper = object : ClickableSpan() {

                        // replace wrappers with real previous spans on click
                        override fun onClick(widget: View) {
                            span.state = DetailsSpanState.OPENED

                            val start = spanned.getSpanStart(this)
                            val end = spanned.getSpanEnd(this)

                            spanned.removeSpan(this)
                            spanned.insert(end, innerSpanned)

                            // make details span cover all expanded text
                            spanned.removeSpan(span)
                            spanned.setSpan(span, start, end + innerSpanned.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                            // edge-case: if the span around this text is now too short, expand it as well
                            spanned.getSpans(end, end, Any::class.java)
                                .filter { spanned.getSpanEnd(it) == end }
                                .forEach {
                                    if (it is DetailsSummarySpan) {
                                        // don't expand summaries, they are meant to end there
                                        return@forEach
                                    }

                                    val bqStart = spanned.getSpanStart(it)
                                    spanned.removeSpan(it)
                                    spanned.setSpan(it, bqStart, end + innerSpanned.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                }

                            postProcessAndSetText(spanned, view)

                            AsyncDrawableScheduler.schedule(view)
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            // Override without setting any color to preserve original colors
                            //ds.color = ThemeUtils.getColor(view.context, R.attr.vctr_content_primary)
                        }
                    }
                    spanned.setSpan(wrapper, startIdx, startIdx + summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    view.text = spanned
                }

                DetailsSpanState.OPENED -> {
                    span.state = DetailsSpanState.DORMANT_OPEN

                    // put the hidden text into blockquote if needed
                    /*
                    var bq = spanned.getSpans(summaryEndIdx, endIdx, BlockQuoteSpan::class.java)
                        .firstOrNull { spanned.getSpanStart(it) == summaryEndIdx && spanned.getSpanEnd(it) == endIdx }
                    if (bq == null) {
                        bq = BlockQuoteSpan(eventHtmlRenderer.theme)
                        spanned.setSpan(bq, summaryEndIdx, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                     */

                    // content under spoiler tag is shown, but should be hidden again on click
                    // change summary text to opened variant
                    spanned.replace(summaryStartIdx, summaryEndIdx, summaryText)
                    spanned.setSpan(span.summary, startIdx, startIdx + summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val wrapper = object : ClickableSpan() {

                        // hide text again on click
                        override fun onClick(widget: View) {
                            span.state = DetailsSpanState.CLOSED

                            spanned.removeSpan(this)

                            postProcessAndSetText(spanned, view)
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            // Override without setting any color to preserve original colors
                            //ds.color = ThemeUtils.getColor(view.context, R.attr.vctr_content_primary)
                        }
                    }
                    spanned.setSpan(wrapper, startIdx, startIdx + summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    view.text = spanned
                }

                DetailsSpanState.DORMANT_CLOSE,
                DetailsSpanState.DORMANT_OPEN -> {
                    // this state is present so that details spans that were already processed won't be processed again
                    // nothing should be done
                }
            }
        }
    }

    private fun postProcessAndSetText(spanned: SpannableStringBuilder, view: TextView) {
        view.text = spanned

        eventHtmlRenderer.plugins.forEach { plugin ->
            if (plugin is DetailsTagPostProcessor) {
                // Keep dormant state by not using the external interface that resets it
                plugin.postProcessDetails(spanned, view, false)
            } else {
                plugin.afterSetText(view)
            }
        }
    }

}
