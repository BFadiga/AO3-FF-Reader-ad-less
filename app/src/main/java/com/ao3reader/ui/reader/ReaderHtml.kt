package com.ao3reader.ui.reader

import com.ao3reader.data.model.Chapter
import com.ao3reader.data.prefs.ReaderSettings

/** Colors the reader page is rendered with. */
data class ReaderColors(val background: String, val text: String, val muted: String, val accent: String)

/** Builds the HTML document shown in the reader's WebView for one chapter. */
object ReaderHtml {

    fun build(
        chapter: Chapter,
        workTitle: String,
        settings: ReaderSettings,
        colors: ReaderColors,
        hasNext: Boolean,
        isFirst: Boolean,
        workSummaryHtml: String?,
    ): String {
        val css = """
            html, body { background: ${colors.background}; color: ${colors.text}; margin: 0; padding: 0; }
            body {
                font-family: ${settings.font.css};
                font-size: ${settings.fontSize}px;
                line-height: ${settings.lineSpacing};
                font-weight: normal;
                text-align: ${settings.align.css};
                padding: 20px ${settings.margin}px 64px;
                overflow-wrap: break-word;
                -webkit-text-size-adjust: 100%;
                -webkit-tap-highlight-color: transparent;
            }
            p { margin: 0 0 ${settings.paragraphSpacing}em 0; }
            /* The author's own bold, italics, underlines etc. are left exactly as written. */
            strong, b { font-weight: bold; }
            em, i { font-style: italic; }
            a { color: ${colors.accent}; }
            img { max-width: 100%; height: auto; }
            hr { border: none; border-top: 1px solid ${colors.muted}; margin: 1.6em 0; }
            blockquote { border-left: 3px solid ${colors.muted}; margin: 1em 0; padding-left: 1em; }
            table { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
            td, th { border: 1px solid ${colors.muted}; padding: 4px 8px; }
            .work-title { font-size: 0.85em; text-align: center; opacity: 0.7; margin: 0 0 0.4em; }
            .chapter-title { font-size: 1.35em; line-height: 1.3; text-align: center; margin: 0 0 1.2em; font-weight: bold; }
            .notes { font-size: 0.9em; border: 1px solid ${colors.muted}; border-radius: 10px; padding: 0.4em 1em; margin: 0 0 1.4em; opacity: 0.9; }
            .notes .label { font-weight: bold; font-size: 0.85em; opacity: 0.8; margin: 0.6em 0 0.2em; }
            .next { display: block; text-align: center; margin: 2.5em 0 1em; padding: 0.8em; border-radius: 12px;
                    border: 1px solid ${colors.accent}; color: ${colors.accent}; text-decoration: none; font-weight: bold; }
            .end { text-align: center; opacity: 0.6; margin: 2.5em 0 1em; }
            ${if (!settings.useWorkSkins) "[style] { color: inherit !important; background: transparent !important; font-family: inherit !important; font-size: inherit !important; }" else ""}
            .loading { text-align: center; opacity: 0.6; margin: 3em 0; }
        """.trimIndent()

        val notes = buildString {
            if (settings.showAuthorNotes) {
                if (isFirst && !workSummaryHtml.isNullOrBlank()) note("Summary", workSummaryHtml)
                chapter.summaryHtml?.takeIf { it.isNotBlank() }?.let { note("Chapter summary", it) }
                chapter.notesHtml?.takeIf { it.isNotBlank() }?.let { note("Notes", it) }
            }
        }
        val endNotes = if (settings.showAuthorNotes) {
            chapter.endNotesHtml?.takeIf { it.isNotBlank() }?.let { buildString { note("End notes", it) } }.orEmpty()
        } else ""

        val footer = if (hasNext) {
            """<a class="next" href="#" onclick="Reader.next(); return false;">Next chapter →</a>"""
        } else {
            """<div class="end">— End of posted chapters —</div>"""
        }

        return """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>$css</style>
            </head><body>
            <div class="work-title">${escape(workTitle)}</div>
            <h1 class="chapter-title">${escape(chapter.title)}</h1>
            $notes
            <div class="userstuff">${chapter.contentHtml}</div>
            $endNotes
            $footer
            <script>$SCRIPT</script>
            </body></html>
        """.trimIndent()
    }

    private fun StringBuilder.note(label: String, html: String) {
        append("""<div class="notes"><div class="label">""").append(escape(label)).append("</div>").append(html).append("</div>")
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** Reports scroll position (throttled) and taps outside links back to the app. */
    private val SCRIPT = """
        (function() {
          var pending = false;
          function ratio() {
            var max = document.documentElement.scrollHeight - window.innerHeight;
            return max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 1;
          }
          window.addEventListener('scroll', function() {
            if (pending) return;
            pending = true;
            setTimeout(function() { pending = false; Reader.onScroll(ratio()); }, 400);
          }, { passive: true });
          document.addEventListener('click', function(e) {
            if (e.target.closest('a')) return;
            Reader.onTap(e.clientY / window.innerHeight);
          });
          window.restoreScroll = function(r) {
            var max = document.documentElement.scrollHeight - window.innerHeight;
            window.scrollTo(0, Math.max(0, max * r));
          };
        })();
    """.trimIndent()
}
