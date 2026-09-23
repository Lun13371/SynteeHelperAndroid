package ru.syntee.helper.feature.chat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown-рендерер сообщений ассистента.
 *
 * @param streaming во время стриминга таблицы не рендерим (перфоманс),
 *   код-блок без закрывающего маркера всё равно парсится до конца.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
) {
    val blocks = remember(markdown) { MdParser.parse(markdown) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Paragraph -> InlineRichText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is MdBlock.Heading -> InlineRichText(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                )
                is MdBlock.Code -> CodeBlock(language = block.language, code = block.code)
                is MdBlock.Quote -> QuoteBlock(text = block.text)
                is MdBlock.ListItem -> ListItemRow(item = block)
                is MdBlock.Table -> if (!streaming) TableBlock(table = block)
                MdBlock.Divider -> androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

// ── Инлайн-разметка ───────────────────────────────────────────────────────

@Composable
private fun InlineRichText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeColor = MaterialTheme.colorScheme.onSurface
    val spans = remember(text) { MdInline.parse(text) }
    Text(
        text = renderInline(spans, style, linkColor, codeBackground, codeColor),
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

private fun renderInline(
    spans: List<MdSpan>,
    style: androidx.compose.ui.text.TextStyle,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
    codeColor: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    fun emit(children: List<MdSpan>, span: SpanStyle) {
        for (child in children) {
            when (child) {
                is MdSpan.Text -> withStyle(span) { append(child.text) }
                is MdSpan.Code -> withStyle(
                    span.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (style.fontSize.value * 0.9).sp,
                        background = codeBackground,
                        color = codeColor,
                    ),
                ) { append(child.text) }
                is MdSpan.Emphasis -> emit(
                    child.children,
                    span.copy(
                        fontWeight = if (child.bold) FontWeight.Bold else span.fontWeight,
                        fontStyle = if (child.italic) FontStyle.Italic else span.fontStyle,
                    ),
                )
                is MdSpan.Strike -> emit(child.children, span.copy(textDecoration = TextDecoration.LineThrough))
                is MdSpan.Link -> withLink(
                    LinkAnnotation.Url(
                        url = child.url,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        ),
                    ),
                ) { append(child.text) }
            }
        }
    }
    emit(spans, SpanStyle())
}

// ── Код-блок ──────────────────────────────────────────────────────────────

@Composable
fun CodeBlock(language: String?, code: String) {
    val clipboard = LocalClipboardManager.current
    val darkBackground = androidx.compose.ui.graphics.Color(0xFF16161C)
    val headerBackground = androidx.compose.ui.graphics.Color(0xFF1E1E26)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBackground, RoundedCornerShape(10.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = language?.uppercase() ?: "CODE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = androidx.compose.ui.graphics.Color(0xFF9E9EB0),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(code))
            }) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Копировать код",
                    tint = androidx.compose.ui.graphics.Color(0xFF9E9EB0),
                    modifier = Modifier.height(20.dp).width(20.dp),
                )
            }
        }
        Text(
            text = remember(language, code) { SyntaxHighlighter.highlight(code, language) },
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
            ),
            color = androidx.compose.ui.graphics.Color(0xFFE3E3EC),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(darkBackground, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .padding(12.dp),
        )
    }
}

// ── Цитата ────────────────────────────────────────────────────────────────

@Composable
private fun QuoteBlock(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(10.dp))
        InlineRichText(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
        )
    }
}

// ── Списки ────────────────────────────────────────────────────────────────

@Composable
private fun ListItemRow(item: MdBlock.ListItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (item.indent * 16).dp),
    ) {
        Text(
            text = if (item.ordered) "${item.number}." else "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(22.dp),
        )
        InlineRichText(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Таблица ───────────────────────────────────────────────────────────────

@Composable
private fun TableBlock(table: MdBlock.Table) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            table.header.forEach { cell ->
                InlineRichText(
                    text = cell,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                )
            }
        }
        table.rows.forEach { row ->
            androidx.compose.material3.HorizontalDivider(color = borderColor)
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    InlineRichText(
                        text = cell,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}
