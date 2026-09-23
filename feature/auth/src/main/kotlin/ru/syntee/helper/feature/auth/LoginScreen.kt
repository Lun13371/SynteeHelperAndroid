package ru.syntee.helper.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.syntee.helper.core.designsystem.components.SynteeGhostButton
import ru.syntee.helper.core.designsystem.components.SynteeLogo
import ru.syntee.helper.core.designsystem.components.SynteePrimaryButton

/**
 * Экран входа через Syntee-Gateway.
 * Дизайн — перенос LoginScreen десктопа: логотип, две кнопки, мягкие
 * радиальные градиенты акцента на фоне.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    // Фон: два мягких радиальных градиента акцента (как login-screen__bg десктопа).
    val accentSubtle = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accentSubtle, Color.Transparent),
                        center = Offset(w * 0.25f, h * 0.3f),
                        radius = w * 0.55f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accentSubtle, Color.Transparent),
                        center = Offset(w * 0.75f, h * 0.7f),
                        radius = w * 0.6f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SynteeLogo(size = 64.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            // Стадия 02: startLogin() через Chrome Custom Tabs + deep link.
            SynteePrimaryButton(
                text = stringResource(R.string.login_button),
                onClick = onLoggedIn,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
            )
            Spacer(Modifier.height(8.dp))
            // Стадия 02: setGuestMode(true).
            SynteeGhostButton(
                text = stringResource(R.string.login_guest),
                onClick = onLoggedIn,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.login_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
