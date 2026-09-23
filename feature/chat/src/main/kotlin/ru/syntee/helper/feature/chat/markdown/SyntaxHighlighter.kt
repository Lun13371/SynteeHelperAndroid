package ru.syntee.helper.feature.chat.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Палитра подсветки (фиксированная тёмная — код всегда на тёмной плашке). */
data class HighlightColors(
    val plain: androidx.compose.ui.graphics.Color,
    val keyword: androidx.compose.ui.graphics.Color,
    val string: androidx.compose.ui.graphics.Color,
    val comment: androidx.compose.ui.graphics.Color,
    val number: androidx.compose.ui.graphics.Color,
) {
    companion object {
        val Dark = HighlightColors(
            plain = androidx.compose.ui.graphics.Color(0xFFE3E3EC),
            keyword = androidx.compose.ui.graphics.Color(0xFFC792EA),
            string = androidx.compose.ui.graphics.Color(0xFFC3E88D),
            comment = androidx.compose.ui.graphics.Color(0xFF7A7A8C),
            number = androidx.compose.ui.graphics.Color(0xFFF78C6C),
        )
    }
}

/**
 * Лёгкий regex-хайлайтер: строки, комментарии, числа, ключевые слова.
 * Языки: Kotlin, JSON, Python, JS/TS, Java, C/C++, SQL, Bash, HTML, CSS, Go, Rust.
 */
object SyntaxHighlighter {

    private val KEYWORDS: Map<String, String> = mapOf(
        "kotlin" to "fun|val|var|class|object|interface|data|sealed|enum|companion|override|private|internal|public|protected|import|package|return|if|else|when|for|while|do|try|catch|finally|throw|is|as|in|out|null|true|false|this|super|suspend|inline|reified|operator|typealias|const|lateinit|by|get|set|init|abstract|open|vararg|where|break|continue",
        "java" to "public|private|protected|class|interface|enum|extends|implements|static|final|void|int|long|double|float|boolean|char|byte|short|new|return|if|else|for|while|do|try|catch|finally|throw|throws|import|package|this|super|null|true|false|abstract|synchronized|volatile|instanceof|break|continue|record|var",
        "python" to "def|class|return|if|elif|else|for|while|break|continue|import|from|as|pass|raise|try|except|finally|with|lambda|global|nonlocal|yield|assert|del|in|is|not|and|or|None|True|False|async|await|self|match|case",
        "javascript" to "function|const|let|var|class|extends|return|if|else|for|while|do|switch|case|break|continue|new|this|typeof|instanceof|null|undefined|true|false|import|export|from|default|async|await|try|catch|finally|throw|yield|of|in|delete|void|static|get|set",
        "typescript" to "function|const|let|var|class|extends|implements|interface|type|enum|return|if|else|for|while|do|switch|case|break|continue|new|this|typeof|instanceof|null|undefined|true|false|import|export|from|default|async|await|try|catch|finally|throw|yield|of|in|delete|void|static|readonly|public|private|protected|as|satisfies",
        "json" to "true|false|null",
        "sql" to "SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|ALTER|DROP|INDEX|VIEW|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|ON|AS|AND|OR|NOT|NULL|IS|IN|BETWEEN|LIKE|ORDER|BY|GROUP|LIMIT|OFFSET|HAVING|DISTINCT|UNION|ALL|CASE|WHEN|THEN|ELSE|END|PRIMARY|KEY|FOREIGN|REFERENCES|DEFAULT|UNIQUE|CASCADE|EXISTS|COUNT|SUM|AVG|MIN|MAX|RETURNING|WITH|INT",
        "bash" to "if|then|else|elif|fi|for|while|do|done|case|esac|function|return|local|export|echo|exit|source|in|break|continue|readonly|declare",
        "go" to "func|package|import|var|const|type|struct|interface|map|chan|go|defer|return|if|else|for|range|switch|case|default|break|continue|select|nil|true|false|new|make|panic|recover",
        "rust" to "fn|let|mut|const|static|struct|enum|trait|impl|for|while|loop|if|else|match|return|use|mod|pub|crate|self|super|where|async|await|move|dyn|ref|as|in|break|continue|true|false|Some|None|Ok|Err",
        "html" to "html|head|body|div|span|p|a|img|ul|ol|li|table|tr|td|th|form|input|button|script|style|link|meta|title|h1|h2|h3|h4|h5|h6|section|header|footer|nav|main|article|aside",
        "css" to "important|media|keyframes|import|supports|root|from|to",
        "c" to "int|long|short|char|float|double|void|struct|union|enum|typedef|static|const|unsigned|signed|return|if|else|for|while|do|switch|case|default|break|continue|sizeof|null|NULL|true|false|include|define|pragma|class|public|private|protected|virtual|override|template|typename|namespace|using|new|delete|this|auto",
        "yaml" to "true|false|null|yes|no|on|off",
    )

    /** Языки, где `#` — комментарий. */
    private val HASH_COMMENT = setOf("python", "bash", "sh", "shell", "yaml", "yml", "ruby", "rb", "r", "makefile", "dockerfile", "toml", "ini", "properties", "conf")

    fun highlight(code: String, language: String?): AnnotatedString {
        val lang = language?.trim()?.lowercase().let { normalize(it) }
        val keywords = KEYWORDS[lang] ?: KEYWORDS["c"].orEmpty()
        val hashComment = lang in HASH_COMMENT

        val commentPart = if (hashComment) {
            "//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/"
        } else {
            "//[^\\n]*|/\\*[\\s\\S]*?\\*/"
        }

        val pattern = Regex(
            "(" +
                "\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*'|(?:^|\\s)`(?:[^`\\\\]|\\\\.)*`" + // строки
                ")|(" + commentPart + ")|(\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFlLdDuUbBxX]*\\b)|(\\b(?:${keywords})\\b)",
        )

        val colors = HighlightColors.Dark
        return buildAnnotatedString {
            var last = 0
            for (m in pattern.findAll(code)) {
                if (m.range.first > last) append(code.substring(last, m.range.first))
                val style = when {
                    m.groups[1] != null -> SpanStyle(color = colors.string)
                    m.groups[2] != null -> SpanStyle(color = colors.comment)
                    m.groups[3] != null -> SpanStyle(color = colors.number)
                    else -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.Medium)
                }
                withStyle(style) { append(m.value) }
                last = m.range.last + 1
            }
            if (last < code.length) append(code.substring(last))
        }
    }

    private val ALIASES = mapOf(
        "kt" to "kotlin", "kts" to "kotlin",
        "py" to "python",
        "js" to "javascript", "jsx" to "javascript", "mjs" to "javascript",
        "ts" to "typescript", "tsx" to "typescript",
        "sh" to "bash", "shell" to "bash", "zsh" to "bash", "console" to "bash", "terminal" to "bash",
        "yml" to "yaml",
        "c++" to "c", "cpp" to "c", "hpp" to "c", "cc" to "c", "h" to "c",
        "golang" to "go",
        "rs" to "rust",
        "html" to "html", "xml" to "html",
        "postgres" to "sql", "postgresql" to "sql", "mysql" to "sql", "sqlite" to "sql",
        "json" to "json", "json5" to "json",
    )

    private fun normalize(lang: String?): String? {
        if (lang.isNullOrBlank()) return null
        return ALIASES[lang] ?: lang
    }
}
