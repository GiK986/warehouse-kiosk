package com.warehouse.kiosk.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class DeviceOwnerReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Called when the app is set as a device app_selection
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Called when the app is removed as a device app_selection
    }


}