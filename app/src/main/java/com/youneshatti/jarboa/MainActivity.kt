package com.youneshatti.jarboa

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.youneshatti.jarboa.service.XmppConnectionService
import com.youneshatti.jarboa.notifications.JarboaNotifier
import com.youneshatti.jarboa.ui.JarboaApp
import com.youneshatti.jarboa.ui.theme.JarboaTheme

class MainActivity : ComponentActivity() {
    private val applicationContainer: AppContainer
        get() = (application as JarboaApplication).container

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, applicationContainer)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A notification can recreate a killed Activity with saved state too.
        // The extra is consumed below, so rotation won't reopen an old target.
        openNotification(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            JarboaTheme {
                JarboaApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (applicationContainer.accountStore.hasAccount()) {
            XmppConnectionService.start(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotification(intent)
    }

    private fun openNotification(intent: Intent) {
        val jid = intent.getStringExtra(JarboaNotifier.EXTRA_CONVERSATION) ?: return
        intent.removeExtra(JarboaNotifier.EXTRA_CONVERSATION)
        if (applicationContainer.accountStore.hasAccount()) viewModel.openNotificationConversation(jid)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setForeground(true)
    }

    override fun onPause() {
        viewModel.setForeground(false)
        super.onPause()
    }
}
