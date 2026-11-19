package stapel.tools.codegen

/** KDoc text rules of §8: source text verbatim, made safe for a block comment and for Dokka. */
object KDoc {
    private val REST_LITERAL = Regex("``([^`]+)``")

    /** Escapes [text] so it can sit inside `/** */` and never resolves as a Dokka symbol link. */
    fun escape(text: String): String {
        var t = text.replace("\r\n", "\n")
        t = REST_LITERAL.replace(t) { "`" + it.groupValues[1] + "`" }
        t = t.replace("*/", "*&#47;").replace("/*", "/&#42;")
        // A `[text]` that is not a markdown link would be resolved by Dokka as a symbol.
        t = escapeBrackets(t)
        return t.lines().joinToString("\n") { line ->
            val trimmed = line.trimEnd()
            if (trimmed.trimStart().startsWith("@")) trimmed.replaceFirst("@", "&#64;") else trimmed
        }.trim()
    }

    private fun escapeBrackets(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '[') {
                val close = text.indexOf(']', i)
                val isLink = close > 0 && close + 1 < text.length && text[close + 1] == '(' && !text.substring(i + 1, close).contains('[')
                if (isLink) {
                    out.append(text, i, close + 1)
                    i = close + 1
                    continue
                }
                out.append("\\[")
            } else if (c == ']') {
                out.append("\\]")
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** First sentence of [text]: up to the first blank line, markdown emphasis stripped, at most 120 chars. */
    fun firstSentence(text: String): String {
        val para = text.split(Regex("\\n\\s*\\n")).first().replace('\n', ' ').replace("**", "").trim()
        val dot = Regex("(?<=[.!?])\\s").find(para)?.range?.first
        val sentence = if (dot != null) para.substring(0, dot) else para
        return if (sentence.length <= 120) sentence else sentence.substring(0, 117).trimEnd() + "..."
    }

    /** A fenced JSON block of at most [maxLines] lines. */
    fun jsonBlock(json: String, maxLines: Int = 20): String {
        val pretty = runCatching {
            prettyJsonOrdered(parseJson.parseToJsonElement(json)).trimEnd()
        }.getOrDefault(json)
        val lines = pretty.lines()
        val shown = if (lines.size > maxLines) lines.take(maxLines) + "…" else lines
        return "```json\n" + shown.joinToString("\n").replace("*/", "*&#47;").replace("/*", "/&#42;") + "\n```"
    }
}
