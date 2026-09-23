package ru.syntee.helper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.syntee.helper.core.data.auth.DeepLinkBus
import ru.syntee.helper.ui.SynteeApp
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deepLinkBus: DeepLinkBus

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            SynteeApp()
        }
    }

    /** Deep link возвращается в живое приложение (launchMode=singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * synteehelper://auth/callback?code=... → код в DeepLinkBus.
     * Код одноразовый (TTL 10 секунд) — LoginViewModel обрабатывает немедленно.
     */
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "synteehelper" && data.host == "auth" && data.path == "/callback") {
            data.getQueryParameter("code")?.let { code ->
                deepLinkBus.submitCode(code)
            }
        }
    }
}
