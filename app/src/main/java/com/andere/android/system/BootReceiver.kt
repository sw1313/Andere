package com.andere.android.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andere.android.AndereApplication
import com.andere.android.domain.model.BackgroundTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as? AndereApplication ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val repo = app.appContainer.preferencesRepository
                val scheduler = app.appContainer.wallpaperScheduler
                val wpConfig = repo.wallpaperConfig.first()
                scheduler.syncSchedule(wpConfig, BackgroundTarget.Wallpaper)
                val lsConfig = repo.lockscreenConfig.first()
                scheduler.syncSchedule(lsConfig, BackgroundTarget.LockScreen)
            } finally {
                pending.finish()
            }
        }
    }
}
