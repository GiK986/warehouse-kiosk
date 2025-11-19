package com.warehouse.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * ACTION_GET_PROVISIONING_MODE Handler (ЗАДЪЛЖИТЕЛНО за Android 12+)
 *
 * Android 12+ изисква DPC да имплементира този handler.
 * Android пита: "Какъв provisioning mode поддържаш?"
 * DPC отговаря: "Fully managed device (Device Owner)"
 *
 * Ако този handler липсва → provisioning fails!
 *
 * Reference:
 * https://source.android.com/docs/devices/admin/provision
 */
class GetProvisioningModeActivity : Activity() {

    companion object {
        private const val TAG = "GetProvisioningMode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "====================================")
        Log.i(TAG, "ACTION_GET_PROVISIONING_MODE called")
        Log.i(TAG, "====================================")

        // Извличане на allowed provisioning modes от Android
        val allowedModes = intent.getIntArrayExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
        )

        Log.d(TAG, "Allowed modes: ${allowedModes?.contentToString()}")

        // Избираме FULLY_MANAGED_DEVICE (Device Owner режим)
        val resultIntent = Intent().apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            )
        }

        Log.i(TAG, "Returning: PROVISIONING_MODE_FULLY_MANAGED_DEVICE")

        setResult(RESULT_OK, resultIntent)
        finish()

        Log.i(TAG, "GetProvisioningModeActivity finished")
    }
}
