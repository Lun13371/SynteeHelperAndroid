package ru.syntee.helper.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.syntee.helper.core.designsystem.components.SynteeGhostButton
import ru.syntee.helper.core.designsystem.components.SynteeLogo
import ru.syntee.helper.core.designsystem.components.SynteePrimaryButton

/**
 * Экран входа через Syntee-Gateway (Custom Tabs + deep link).
 * Дизайн — перенос LoginScreen десктопа: логотип, мягкие радиальные градиенты.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) onLoggedIn()
    }

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

            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "login_state",
            ) { current ->
                when (current) {
                    is LoginUiState.WaitingForBrowser -> WaitingBrowserContent(
                        onCancel = viewModel::cancelWaiting,
                    )
                    is LoginUiState.Exchanging -> ExchangingContent()
                    is LoginUiState.Error -> ErrorContent(
                        message = current.message,
                        onRetry = viewModel::reset,
                    )
                    else -> IdleContent(
                        onLogin = viewModel::startLogin,
                        onGuest = viewModel::continueAsGuest,
                    )
                }
            }

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

@Composable
private fun IdleContent(onLogin: () -> Unit, onGuest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SynteePrimaryButton(
            text = stringResource(R.string.login_button),
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
        )
        Spacer(Modifier.height(8.dp))
        SynteeGhostButton(
            text = stringResource(R.string.login_guest),
            onClick = onGuest,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
        )
    }
}

@Composable
private fun WaitingBrowserContent(onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.login_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        SynteeGhostButton(
            text = stringResource(R.string.login_cancel),
            onClick = onCancel,
        )
    }
}

@Composable
private fun ExchangingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.login_exchanging),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        SynteePrimaryButton(
            text = stringResource(R.string.login_retry),
            onClick = onRetry,
        )
    }
}
