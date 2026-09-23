package ru.syntee.helper.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Тема приложения Syntee — Material 3 с фирменной палитрой.
 *
 * Стадия 05 добавит: AMOLED-режим, динамические цвета (Material You),
 * акцентные пресеты и живое превью в настройках.
 */
@Composable
fun SynteeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SynteeDarkColors else SynteeLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SynteeTypography,
        shapes = SynteeShapes,
        content = content,
    )
}
