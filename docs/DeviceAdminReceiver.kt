package com.autoplus.kiosklauncher // TODO: Заменете с вашия package name

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Admin Receiver за Kiosk Mode
 * Този receiver се регистрира като Device Owner при provisioning
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        showToast(context, "Device Admin активиран")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        showToast(context, "Device Admin деактивиран")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        
        // Provisioning-ът завърши, стартирай ProvisioningCompleteActivity
        val completeIntent = Intent(context, ProvisioningCompleteActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtras(intent.extras ?: return)
        }
        context.startActivity(completeIntent)
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        showToast(context, "Kiosk Mode активиран")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        showToast(context, "Kiosk Mode деактивиран")
    }

    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "DeviceAdminReceiver"
    }
}
