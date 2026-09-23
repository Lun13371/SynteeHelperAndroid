package ru.syntee.helper.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Фирменная палитра Syntee — перенос дизайна десктоп-агента (globals.css):
 *
 *  - DARK (дефолт): нейтральный cool gray, светлый акцент #E8E8EC,
 *    текст на акценте — тёмный (#1A1A20), как на десктопе.
 *  - LIGHT: тёплый кремовый фон (#F4F1EC), тёмно-коричневый акцент #4A3F2F.
 *
 * AMOLED-тема, динамические цвета (Material You) и акцентные пресеты —
 * стадия 05 (кастомизация).
 */

internal val SynteeDarkColors = darkColorScheme(
    primary = Color(0xFFE8E8EC),
    onPrimary = Color(0xFF1A1A20),
    primaryContainer = Color(0xFF2E2E38),
    onPrimaryContainer = Color(0xFFE8E8EC),
    secondary = Color(0xFF9A9AA5),
    onSecondary = Color(0xFF16161C),
    secondaryContainer = Color(0xFF26262E),
    onSecondaryContainer = Color(0xFFC6C6CE),
    tertiary = Color(0xFFB9B9C6),
    onTertiary = Color(0xFF1A1A20),
    background = Color(0xFF0E0E12),
    onBackground = Color(0xFFE8E8EC),
    surface = Color(0xFF14141A),
    onSurface = Color(0xFFE8E8EC),
    surfaceVariant = Color(0xFF1C1C24),
    onSurfaceVariant = Color(0xFF9A9AA5),
    surfaceContainerLowest = Color(0xFF101015),
    surfaceContainerLow = Color(0xFF18181F),
    surfaceContainer = Color(0xFF1C1C24),
    surfaceContainerHigh = Color(0xFF26262E),
    surfaceContainerHighest = Color(0xFF2E2E38),
    surfaceDim = Color(0xFF0C0C10),
    surfaceBright = Color(0xFF3A3A46),
    outline = Color(0xFF3A3A46),
    outlineVariant = Color(0xFF2A2A34),
    inverseSurface = Color(0xFFE8E8EC),
    inverseOnSurface = Color(0xFF16161C),
    inversePrimary = Color(0xFF4A4A56),
    error = Color(0xFFFF8080),
    onError = Color(0xFF2A0F0F),
    errorContainer = Color(0xFF3A1A1A),
    onErrorContainer = Color(0xFFFFB3B3),
)

internal val SynteeLightColors = lightColorScheme(
    primary = Color(0xFF4A3F2F),
    onPrimary = Color(0xFFFCF9F4),
    primaryContainer = Color(0xFFE8E2D8),
    onPrimaryContainer = Color(0xFF3A3122),
    secondary = Color(0xFF6B655C),
    onSecondary = Color(0xFFFCF9F4),
    secondaryContainer = Color(0xFFE8E2D8),
    onSecondaryContainer = Color(0xFF4A443A),
    tertiary = Color(0xFF8A8272),
    onTertiary = Color(0xFFFCF9F4),
    background = Color(0xFFF4F1EC),
    onBackground = Color(0xFF2A2620),
    surface = Color(0xFFFCF9F4),
    onSurface = Color(0xFF2A2620),
    surfaceVariant = Color(0xFFF3EFE8),
    onSurfaceVariant = Color(0xFF6B655C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF7F1),
    surfaceContainer = Color(0xFFF3EFE8),
    surfaceContainerHigh = Color(0xFFEDE8DF),
    surfaceContainerHighest = Color(0xFFE8E2D8),
    surfaceDim = Color(0xFFDDD8CF),
    surfaceBright = Color(0xFFFCF9F4),
    outline = Color(0xFFB5AC9E),
    outlineVariant = Color(0xFFD8D2C6),
    inverseSurface = Color(0xFF2A2620),
    inverseOnSurface = Color(0xFFF4F1EC),
    inversePrimary = Color(0xFFE8E2D8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDE),
    onErrorContainer = Color(0xFF410E0B),
)
