package ru.syntee.helper.feature.chat.markdown

/**
 * Инлайн-разметка: **bold**, *italic*, `code`, ~~strike~~, [текст](url).
 * Рекурсивный парсер: приоритет code > bold > strike > link > italic.
 */
sealed interface MdSpan {
    data class Text(val text: String) : MdSpan
    data class Emphasis(val children: List<MdSpan>, val bold: Boolean, val italic: Boolean) : MdSpan
    data class Code(val text: String) : MdSpan
    data class Strike(val children: List<MdSpan>) : MdSpan
    data class Link(val text: String, val url: String) : MdSpan
}

object MdInline {

    private val CODE = Regex("`([^`\\n]+)`")
    private val BOLD = Regex("\\*\\*([\\s\\S]+?)\\*\\*|__([\\s\\S]+?)__")
    private val STRIKE = Regex("~~([\\s\\S]+?)~~")
    private val LINK = Regex("\\[([^\\]\\n]*)\\]\\(([^)\\s]+)\\)")
    private val ITALIC = Regex("\\*([^*\\n]+)\\*|(?<![\\w*_])_([^_\\n]+)_(?![\\w*_])")

    fun parse(text: String): List<MdSpan> {
        if (text.isEmpty()) return emptyList()

        data class Candidate(val start: Int, val end: Int, val priority: Int, val build: () -> MdSpan)

        val candidates = mutableListOf<Candidate>()
        CODE.find(text)?.let { m ->
            candidates += Candidate(m.range.first, m.range.last, 0) { MdSpan.Code(m.groupValues[1]) }
        }
        BOLD.find(text)?.let { m ->
            val inner = m.groupValues[1].ifEmpty { m.groupValues[2] }
            candidates += Candidate(m.range.first, m.range.last, 1) {
                MdSpan.Emphasis(parse(inner), bold = true, italic = false)
            }
        }
        STRIKE.find(text)?.let { m ->
            candidates += Candidate(m.range.first, m.range.last, 2) {
                MdSpan.Strike(parse(m.groupValues[1]))
            }
        }
        LINK.find(text)?.let { m ->
            candidates += Candidate(m.range.first, m.range.last, 3) {
                MdSpan.Link(m.groupValues[1].ifEmpty { m.groupValues[2] }, m.groupValues[2])
            }
        }
        ITALIC.find(text)?.let { m ->
            val inner = m.groupValues[1].ifEmpty { m.groupValues[2] }
            candidates += Candidate(m.range.first, m.range.last, 4) {
                MdSpan.Emphasis(parse(inner), bold = false, italic = true)
            }
        }

        val winner = candidates.minByOrNull { it.start * 10 + it.priority }
            ?: return listOf(MdSpan.Text(text))

        val before = text.substring(0, winner.start)
        val after = text.substring(winner.end + 1)

        val result = mutableListOf<MdSpan>()
        if (before.isNotEmpty()) result += MdSpan.Text(before)
        result += winner.build()
        result += parse(after)
        return result
    }
}
