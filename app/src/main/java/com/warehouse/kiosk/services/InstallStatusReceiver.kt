package com.warehouse.kiosk.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * BroadcastReceiver за получаване на статус от APK install операция.
 *
 * Използва се от ApkRepository при silent install на APK-та.
 */
class InstallStatusReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "InstallStatusReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // За Device Owner това НЕ трябва да се случва
                // Silent install не изисква user action
                Log.w(TAG, "STATUS_PENDING_USER_ACTION - unexpected for Device Owner")
                @Suppress("DEPRECATION")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start user action", e)
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                Log.i(TAG, "APK installed successfully: $packageName")
                // Успешна инсталация - може да покажем notification или да update-нем UI
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                Log.e(TAG, "APK install failed for $packageName: $message (status: $status)")
            }

            else -> {
                Log.w(TAG, "Unknown install status: $status")
            }
        }
    }
}