package com.notifyai

import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.notifyai.ui.navigation.MainNavigation
import com.notifyai.ui.theme.NotifyAITheme
import com.notifyai.worker.WorkManagerScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.activity.viewModels

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var workManagerScheduler: WorkManagerScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Schedule periodic summarization automatically
        workManagerScheduler.schedulePeriodicSummarization()

        setContent {
            NotifyAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPerm = isNotificationServiceEnabled()
                    MainNavigation(hasNotificationPermission = hasPerm)
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, com.notifyai.service.NotifyAIListenerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
}
