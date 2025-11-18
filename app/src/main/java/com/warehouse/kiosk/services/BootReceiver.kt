package com.warehouse.kiosk.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.warehouse.kiosk.data.repository.KioskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: KioskRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED || context == null) return

        scope.launch {
            // We need to read the settings from our DataStore
            val kioskModeActive = repository.isKioskModeActive.first()
            val appToStart = repository.autoStartAppPackage.first()

            if (kioskModeActive && appToStart != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(appToStart)
                launchIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
        }
    }
}