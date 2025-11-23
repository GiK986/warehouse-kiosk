package com.warehouse.kiosk.services

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import com.warehouse.kiosk.ProvisioningCompleteActivity

/**
 * Device Admin Receiver за Device Owner режим
 *
 * Този receiver получава callbacks от Android за:
 * - Device Admin статус промени
 * - Device Owner provisioning completion
 * - Device Owner промени
 * - Boot complete
 */
class DeviceOwnerReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceOwnerReceiver"
    }

    /**
     * Изпълнява се когато приложението е зададено като Device Admin
     */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled")
    }

    /**
     * Изпълнява се когато приложението е премахнато като Device Admin
     */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled")
    }

    /**
     * КРИТИЧНО: Изпълнява се след успешен Device Owner provisioning
     *
     * Android изпраща този Intent след като:
     * 1. APK е download-нат и инсталиран
     * 2. Device Owner е setнат успешно
     * 3. Provisioning е завършен
     *
     * Стартира ProvisioningCompleteActivity за допълнителна настройка
     */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)

        Log.i(TAG, "====================================")
        Log.i(TAG, "Profile provisioning complete!")
        Log.i(TAG, "====================================")

        // ПРАВИЛНО: Извличане на admin extras като PersistableBundle
        val adminExtras: PersistableBundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                PersistableBundle::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        }

        if (adminExtras != null) {
            Log.d(TAG, "Admin extras bundle received")
            Log.d(TAG, "  warehouse_id: ${adminExtras.getString("warehouse_id")}")
            Log.d(TAG, "  location_name: ${adminExtras.getString("location_name")}")

            // Проверка за location_wifi (nested PersistableBundle)
            val locationWifi = adminExtras.getPersistableBundle("location_wifi")
            if (locationWifi != null) {
                Log.d(TAG, "  WiFi config found:")
                Log.d(TAG, "    SSID: ${locationWifi.getString("wifi_ssid")}")
                Log.d(TAG, "    Security: ${locationWifi.getString("wifi_security_type")}")
            } else {
                Log.d(TAG, "  No WiFi config in admin extras")
            }
        } else {
            Log.w(TAG, "No admin extras bundle found")
        }

        // Проверка на Device Owner статус
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)

            Log.i(TAG, "Device Owner status: $isDeviceOwner")

            if (isDeviceOwner) {
                Log.i(TAG, "SUCCESS! We are Device Owner!")

                // Стартиране на ProvisioningCompleteActivity с admin extras
                try {
                    val setupIntent = Intent(context, ProvisioningCompleteActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // Предаваме admin extras към ProvisioningCompleteActivity
                        if (adminExtras != null) {
                            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, adminExtras)
                        }
                    }
                    context.startActivity(setupIntent)
                    Log.i(TAG, "ProvisioningCompleteActivity started")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start ProvisioningCompleteActivity", e)
                }

            } else {
                Log.e(TAG, "ERROR: Not Device Owner after provisioning!")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onProfileProvisioningComplete", e)
        }

        Log.i(TAG, "onProfileProvisioningComplete finished")
    }

    /**
     * Изпълнява се след boot на устройството
     * (само ако сме Device Owner)
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Device booted - Device Owner active")
                // Можем да стартираме MainActivity автоматично при boot
            }
            "android.app.action.DEVICE_OWNER_CHANGED" -> {
                Log.i(TAG, "Device Owner status changed")
            }
        }
    }
}