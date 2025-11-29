package com.warehouse.kiosk.domain.manager

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import com.warehouse.kiosk.services.DeviceOwnerReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized manager for Device Owner operations and policies.
 * Handles all DevicePolicyManager interactions and lock task mode management.
 */
@Singleton
class DeviceOwnerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeviceOwnerManager"

        // Default lock task features for kiosk mode
        const val DEFAULT_LOCK_TASK_FEATURES =
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
            DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
            DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
            DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
            DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
    }

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, DeviceOwnerReceiver::class.java)
    }

    /**
     * Check if this app is the Device Owner
     */
    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(context.packageName)

    /**
     * Check if device is currently in lock task mode
     */
    val isInLockTaskMode: Boolean
        get() = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    /**
     * Set this app as the persistent home launcher
     */
    suspend fun setAsHomeLauncher(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            if (!isDeviceOwner) {
                throw SecurityException("App is not Device Owner")
            }

            Log.d(TAG, "Setting app as home launcher...")
            val launchers = queryAllLaunchers()
            val selfLauncher = launchers.firstOrNull {
                it.activityInfo?.packageName == context.packageName
            } ?: throw IllegalStateException("Cannot find own launcher activity")

            setHomeLauncher(selfLauncher)
            Log.i(TAG, "✓ Successfully set as home launcher")
        }
    }

    /**
     * Configure lock task features for kiosk mode
     */
    fun setLockTaskFeatures(features: Int = DEFAULT_LOCK_TASK_FEATURES): Result<Unit> {
        return runCatching {
            if (!isDeviceOwner) {
                throw SecurityException("App is not Device Owner")
            }

            val currentFeatures = dpm.getLockTaskFeatures(adminComponent)

            if (currentFeatures != features) {
                Log.d(TAG, "Updating lock task features from $currentFeatures to $features")
                dpm.setLockTaskFeatures(adminComponent, features)

                val actualFeatures = dpm.getLockTaskFeatures(adminComponent)
                Log.i(TAG, "✓ Lock task features set: $actualFeatures")

                logFeatureStatus(actualFeatures)
            } else {
                Log.d(TAG, "Lock task features already correct")
            }
        }
    }

    /**
     * Set allowed packages for lock task mode
     */
    fun setLockTaskPackages(packages: List<String>): Result<Unit> {
        return runCatching {
            if (!isDeviceOwner) {
                throw SecurityException("App is not Device Owner")
            }

            dpm.setLockTaskPackages(adminComponent, packages.toTypedArray())
            Log.d(TAG, "Set lock task packages: ${packages.joinToString()}")
        }
    }

    /**
     * Query all available launcher activities
     */
    private suspend fun queryAllLaunchers(): List<ResolveInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    /**
     * Get the current default launcher
     */
    private fun getCurrentLauncher(): ResolveInfo? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    /**
     * Set a specific launcher as the persistent home activity
     */
    private fun setHomeLauncher(target: ResolveInfo) {
        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        // Clear current home launcher
        getCurrentLauncher()?.let { currentHome ->
            runCatching {
                dpm.clearPackagePersistentPreferredActivities(
                    adminComponent,
                    currentHome.activityInfo.packageName
                )
            }
        }

        // Set new home launcher
        val targetComponent = ComponentName(
            target.activityInfo.packageName,
            target.activityInfo.name
        )
        dpm.addPersistentPreferredActivity(adminComponent, homeFilter, targetComponent)
    }

    /**
     * Set Lock Screen Info for the device
     */
    fun setLockScreenInfo(screenInfo: String) {
        if (!isDeviceOwner) {
            throw SecurityException("App is not Device Owner")
        }
        dpm.setDeviceOwnerLockScreenInfo(adminComponent, screenInfo)
    }

    /**
     * Log the status of each lock task feature
     */
    private fun logFeatureStatus(features: Int) {
        val hasHome = (features and DevicePolicyManager.LOCK_TASK_FEATURE_HOME) != 0
        val hasOverview = (features and DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW) != 0
        val hasSystemInfo = (features and DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO) != 0
        val hasGlobalActions = (features and DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS) != 0
        val hasKeyguard = (features and DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD) != 0

        Log.d(TAG, "Feature status:")
        Log.d(TAG, "  HOME: ${if (hasHome) "✓" else "✗"}")
        Log.d(TAG, "  OVERVIEW: ${if (hasOverview) "✓" else "✗"}")
        Log.d(TAG, "  SYSTEM_INFO: ${if (hasSystemInfo) "✓" else "✗"}")
        Log.d(TAG, "  GLOBAL_ACTIONS: ${if (hasGlobalActions) "✓" else "✗"}")
        Log.d(TAG, "  KEYGUARD: ${if (hasKeyguard) "✓" else "✗"}")

        if (!hasHome || !hasOverview) {
            Log.w(TAG, "⚠ WARNING: HOME or OVERVIEW features not enabled!")
        }
    }
}