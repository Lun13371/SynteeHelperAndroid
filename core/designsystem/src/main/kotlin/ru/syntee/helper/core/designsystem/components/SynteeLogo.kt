package ru.syntee.helper.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Логотип Syntee — закруглённый квадрат с буквой «S».
 * Пропорции как у логотипа десктопа (viewBox 32, rx 7, font-size 18).
 */
@Composable
fun SynteeLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(size * (7f / 32f)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "S",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = (size.value * 18f / 32f).sp,
            lineHeight = (size.value * 18f / 32f).sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
