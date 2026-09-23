package ru.syntee.helper.feature.chat.markdown

/**
 * Блочный Markdown-парсер, заточенный под вывод LLM:
 * код-блоки, заголовки, списки, таблицы, цитаты, разделители, абзацы.
 */
sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class ListItem(val ordered: Boolean, val number: Int, val text: String, val indent: Int) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

object MdParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val UL = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val OL = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val TABLE_ROW = Regex("^\\s*\\|(.+)\\|\\s*$")
    private val TABLE_SEP = Regex("^\\s*\\|?[\\s:|-]*-{3,}[\\s:|-]*\\|?\\s*$")
    private val DIVIDER = Regex("^ {0,3}([-*_])[ \\t]*\\1[ \\t]*\\1[\\s\\-*_]*$")
    private val FENCE = Regex("^ {0,3}(```+|~~~+)\\s*(\\S+)?\\s*$")

    fun parse(markdown: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = markdown.lines()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            if (text.isNotEmpty()) blocks += MdBlock.Paragraph(text)
            paragraph.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // ── Код-блок ──
            val fence = FENCE.find(line)
            if (fence != null) {
                flushParagraph()
                val marker = fence.groupValues[1].take(3)
                val lang = fence.groupValues[2].takeIf { it.isNotEmpty() }
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker)) {
                    code.appendLine(lines[i])
                    i++
                }
                if (i < lines.size) i++ // закрывающий маркер
                blocks += MdBlock.Code(lang, code.toString().trimEnd('\n'))
                continue
            }

            // ── Заголовок ──
            val heading = HEADING.find(line)
            if (heading != null) {
                flushParagraph()
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                i++
                continue
            }

            // ── Разделитель ──
            if (DIVIDER.matches(line)) {
                flushParagraph()
                blocks += MdBlock.Divider
                i++
                continue
            }

            // ── Таблица ──
            if (TABLE_ROW.matches(line) && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1])) {
                flushParagraph()
                val header = splitRow(line)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && TABLE_ROW.matches(lines[i])) {
                    rows += splitRow(lines[i])
                    i++
                }
                blocks += MdBlock.Table(header, rows)
                continue
            }

            // ── Список ──
            val ul = UL.find(line)
            if (ul != null) {
                flushParagraph()
                blocks += MdBlock.ListItem(
                    ordered = false,
                    number = 0,
                    text = ul.groupValues[2],
                    indent = indentOf(ul.groupValues[1]),
                )
                i++
                continue
            }
            val ol = OL.find(line)
            if (ol != null) {
                flushParagraph()
                blocks += MdBlock.ListItem(
                    ordered = true,
                    number = ol.groupValues[2].toIntOrNull() ?: 1,
                    text = ol.groupValues[3],
                    indent = indentOf(ol.groupValues[1]),
                )
                i++
                continue
            }

            // ── Цитата ──
            if (line.startsWith(">")) {
                flushParagraph()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].startsWith(">")) {
                    quote.appendLine(lines[i].removePrefix(">").removePrefix(" "))
                    i++
                }
                blocks += MdBlock.Quote(quote.toString().trimEnd('\n'))
                continue
            }

            // ── Абзац ──
            if (line.isBlank()) {
                flushParagraph()
            } else {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trimEnd())
            }
            i++
        }
        flushParagraph()
        return blocks
    }

    private fun splitRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    private fun indentOf(whitespace: String): Int = whitespace.length / 2
}
