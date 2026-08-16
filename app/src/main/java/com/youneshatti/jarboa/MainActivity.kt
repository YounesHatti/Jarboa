package com.youneshatti.jarboa

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.youneshatti.jarboa.service.XmppConnectionService
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (applicationContainer.accountStore.hasAccount()) {
            XmppConnectionService.start(this)
        }
        setContent {
            JarboaTheme {
                JarboaApp(viewModel)
            }
        }
    }
}
