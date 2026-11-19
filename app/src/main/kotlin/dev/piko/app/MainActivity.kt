package dev.piko.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.piko.app.ui.LocalSkin
import dev.piko.app.ui.XiaomanRoot
import dev.piko.app.ui.XiaomanTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as XiaomanApp).graph.uiVisible.value = true
    }

    override fun onStop() {
        (application as XiaomanApp).graph.uiVisible.value = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as XiaomanApp).graph
        setContent {
            val skin by graph.settings.skin.collectAsStateWithLifecycle()
            XiaomanTheme(skin) {
                SystemBarIcons()
                XiaomanRoot(graph)
            }
        }
    }
}

/** A dark skin can run while the system is in light mode, so bars follow the skin, not the system. */
@Composable
private fun SystemBarIcons() {
    val dark = LocalSkin.current.dark
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
    else -> null
}
