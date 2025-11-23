package com.warehouse.kiosk

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.warehouse.kiosk.data.repository.KioskRepository
import com.warehouse.kiosk.presentation.navigation.AppNavigation
import com.warehouse.kiosk.services.DeviceOwnerReceiver
import com.warehouse.kiosk.ui.theme.WarehouseKioskTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

// Global state to control the password dialog visibility
val showPasswordDialog = mutableStateOf(false)

// Global state to trigger navigation reset to launcher
val shouldResetToLauncher = mutableStateOf(false)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: KioskRepository

    companion object {
        const val EXTRA_SHOW_PASSWORD_DIALOG = "show_password_dialog"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("MainActivity", "====================================")
        Log.i("MainActivity", "MainActivity onCreate()")
        Log.i("MainActivity", "====================================")

        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this) {}

        // Check Device Owner status
        val deviceOwnerStatus = isDeviceOwner
        Log.i("MainActivity", "Device Owner status: $deviceOwnerStatus")

        if (deviceOwnerStatus) {
            // Check if we should set up kiosk policies
            // Set policies if ANY of these conditions is true:
            // 1. Coming from provisioning (QR code setup), OR
            // 2. Kiosk mode is active (normal restart), OR
            // 3. First time as Device Owner (ADB setup)
            val fromProvisioning = intent.getBooleanExtra("from_provisioning", false)

            lifecycleScope.launch {
                val kioskModeActive = repository.isKioskModeActive.first()
                val setupCompleted = repository.isInitialSetupCompleted.first()

                Log.i("MainActivity", "Setup check:")
                Log.i("MainActivity", "  - From provisioning: $fromProvisioning")
                Log.i("MainActivity", "  - Kiosk mode active: $kioskModeActive")
                Log.i("MainActivity", "  - Initial setup completed: $setupCompleted")

                // Determine if we should run setup
                val isFirstTimeSetup = !setupCompleted  // Never set up before (ADB or QR)
                val shouldRunSetup = fromProvisioning || kioskModeActive || isFirstTimeSetup

                if (shouldRunSetup) {
                    Log.i("MainActivity", "✓ Running kiosk setup")

                    if (isFirstTimeSetup) {
                        Log.i("MainActivity", "  Reason: First time Device Owner setup (ADB or QR)")
                    } else if (fromProvisioning) {
                        Log.i("MainActivity", "  Reason: Coming from provisioning")
                    } else if (kioskModeActive) {
                        Log.i("MainActivity", "  Reason: Kiosk mode is active (normal restart)")
                    }

                    // Set policies on main thread
                    withContext(Dispatchers.Main) {
                        setKioskPolicies()
                    }

                    // Mark setup as completed (if first time)
                    if (!setupCompleted) {
                        Log.i("MainActivity", "  Marking initial setup as completed")
                        repository.setInitialSetupCompleted(true)
                    }

                    // Ensure kiosk mode is marked as active
                    if (!kioskModeActive) {
                        Log.i("MainActivity", "  Activating kiosk mode")
                        repository.setKioskModeActive(true)
                    }
                } else {
                    Log.i("MainActivity", "✗ Skipping kiosk setup")
                    Log.i("MainActivity", "  Reason: Setup completed AND kiosk mode not active")
                    Log.i("MainActivity", "  This happens after 'Exit Kiosk Mode'")
                }

                // ВАЖНО: observeKioskMode() трябва ВИНАГИ да работи!
                // Това позволява kiosk mode да се включва/изключва динамично
                // без да се налага restart на устройството
                Log.i("MainActivity", "Starting kiosk mode observer...")
                observeKioskMode()
            }
        } else {
            Log.w("MainActivity", "We are NOT Device Owner - kiosk policies will NOT be set")
            Log.w("MainActivity", "To become Device Owner, perform QR code provisioning")
        }

        handleIntent(intent)

        setContent {
            WarehouseKioskTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }

    private suspend fun queryAllLaunchers(): List<ResolveInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    private fun pickNonSelfLauncher(launchers: List<ResolveInfo>, selfPackage: String): ResolveInfo? {
        val candidates = launchers.filter { it.activityInfo?.packageName != selfPackage }
        if (candidates.isEmpty()) return null
        val systemCandidates = candidates.filter {
            val flags = it.activityInfo?.applicationInfo?.flags ?: 0
            (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        }
        return systemCandidates.firstOrNull() ?: candidates.firstOrNull()
    }

    private fun pickSelfLauncher(launchers: List<ResolveInfo>, selfPackage: String): ResolveInfo? {
        return launchers.firstOrNull { it.activityInfo?.packageName == selfPackage }
    }

    private fun isInLockTaskModeCompat(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val state = try {
            am.lockTaskModeState
        } catch (_: Throwable) {
            ActivityManager.LOCK_TASK_MODE_NONE
        }
        return state != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun getCurrentLauncher(context: Context): ResolveInfo? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        return resolveInfo
    }
    private fun setHomeLauncher(target: ResolveInfo?) {
        if (!isDeviceOwner) return

        val dpm = getSystemService(DevicePolicyManager::class.java) as DevicePolicyManager
        val admin = ComponentName(this, DeviceOwnerReceiver::class.java)
        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        // 1. Find the current home launcher
        val currentHome = getCurrentLauncher(this)

        // 2. Dethrone the current king
        if (currentHome != null) {
            runCatching { dpm.clearPackagePersistentPreferredActivities(admin, currentHome.activityInfo.packageName) }
        }

        // 3. Crown the new king
        if (target != null) {
            val targetCmp = ComponentName(target.activityInfo.packageName, target.activityInfo.name)
            dpm.addPersistentPreferredActivity(admin, homeFilter, targetCmp)
        }
    }

//    private fun startHome() {
//        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
//            addCategory(Intent.CATEGORY_HOME)
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK
//        }
//        startActivity(homeIntent)
//    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_PASSWORD_DIALOG, false) == true) {
            showPasswordDialog.value = true
        }

        // Check if this is a Home button press (ACTION_MAIN with CATEGORY_HOME)
        if (intent?.action == Intent.ACTION_MAIN &&
            intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
            Log.i("MainActivity", "Home button pressed - resetting navigation to launcher")
            shouldResetToLauncher.value = true
        }
    }

    private fun observeKioskMode() {
        repository.isKioskModeActive.combine(repository.getEnabledApps()) { isActive, enabledApps ->
            Pair(isActive, enabledApps)
        }.onEach { (isActive, enabledApps) ->
            if (isDeviceOwner) {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponentName = ComponentName(this, DeviceOwnerReceiver::class.java)

                val allowedPackages = mutableListOf(packageName)
                if (isActive) {
                    allowedPackages.addAll(enabledApps.map { it.packageName })
                    allowedPackages.add("com.android.settings")
                }
                dpm.setLockTaskPackages(adminComponentName, allowedPackages.toTypedArray())

                if (isActive && !isInLockTaskMode) {
                    startLockTask()
                } else if (!isActive && isInLockTaskMode) {
                    stopLockTask()
                }
            }
        }.launchIn(lifecycleScope)
    }

    private val isDeviceOwner: Boolean
        get() = (getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isDeviceOwnerApp(packageName)

    private val isInLockTaskMode: Boolean
        get() = (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    private fun setKioskPolicies() {
        Log.i("MainActivity", "====================================")
        Log.i("MainActivity", "Setting Kiosk Policies...")
        Log.i("MainActivity", "====================================")

        lifecycleScope.launch {
            // Actively crown ourselves as the king (launcher)
            try {
                Log.d("MainActivity", "Setting home launcher...")
                val launchers = queryAllLaunchers()
                val selfTarget = pickSelfLauncher(launchers, packageName)
                setHomeLauncher(selfTarget)
                Log.i("MainActivity", "✓ Home launcher set successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "✗ Failed to set home launcher", e)
            }
        }

        // Set Lock Task Features
        try {
            Log.d("MainActivity", "Checking Lock Task Features...")
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponentName = ComponentName(this, DeviceOwnerReceiver::class.java)

            val desiredFlags = DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS

            // Check current features
            val currentFlags = dpm.getLockTaskFeatures(adminComponentName)
            Log.d("MainActivity", "Current Lock Task Features: $currentFlags")
            Log.d("MainActivity", "Desired Lock Task Features: $desiredFlags")

            // Only set if different
            if (currentFlags != desiredFlags) {
                Log.i("MainActivity", "Features need update - setting new features...")

                Log.d("MainActivity", "Flags to set: $desiredFlags")
                Log.d("MainActivity", "  - LOCK_TASK_FEATURE_HOME: ${DevicePolicyManager.LOCK_TASK_FEATURE_HOME}")
                Log.d("MainActivity", "  - LOCK_TASK_FEATURE_OVERVIEW: ${DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW}")
                Log.d("MainActivity", "  - LOCK_TASK_FEATURE_SYSTEM_INFO: ${DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO}")
                Log.d("MainActivity", "  - LOCK_TASK_FEATURE_GLOBAL_ACTIONS: ${DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS}")

                dpm.setLockTaskFeatures(adminComponentName, desiredFlags)

                // Verify features were set
                val actualFlags = dpm.getLockTaskFeatures(adminComponentName)
                Log.i("MainActivity", "✓ Lock Task Features set successfully!")
                Log.i("MainActivity", "Actual flags applied: $actualFlags")

                // Check each feature
                val hasHome = (actualFlags and DevicePolicyManager.LOCK_TASK_FEATURE_HOME) != 0
                val hasOverview = (actualFlags and DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW) != 0
                val hasSystemInfo = (actualFlags and DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO) != 0
                val hasGlobalActions = (actualFlags and DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS) != 0

                Log.i("MainActivity", "Feature verification:")
                Log.i("MainActivity", "  HOME: ${if (hasHome) "✓ ENABLED" else "✗ DISABLED"}")
                Log.i("MainActivity", "  OVERVIEW: ${if (hasOverview) "✓ ENABLED" else "✗ DISABLED"}")
                Log.i("MainActivity", "  SYSTEM_INFO: ${if (hasSystemInfo) "✓ ENABLED" else "✗ DISABLED"}")
                Log.i("MainActivity", "  GLOBAL_ACTIONS: ${if (hasGlobalActions) "✓ ENABLED" else "✗ DISABLED"}")

                if (!hasHome || !hasOverview) {
                    Log.w("MainActivity", "⚠ WARNING: HOME or OVERVIEW features were not applied!")
                    Log.w("MainActivity", "This is the problem you reported!")
                }
            } else {
                Log.i("MainActivity", "✓ Lock Task Features already correct - no update needed")

                // Still log current state for debugging
                val hasHome = (currentFlags and DevicePolicyManager.LOCK_TASK_FEATURE_HOME) != 0
                val hasOverview = (currentFlags and DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW) != 0
                val hasSystemInfo = (currentFlags and DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO) != 0
                val hasGlobalActions = (currentFlags and DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS) != 0

                Log.d("MainActivity", "Current features:")
                Log.d("MainActivity", "  HOME: ${if (hasHome) "✓" else "✗"}")
                Log.d("MainActivity", "  OVERVIEW: ${if (hasOverview) "✓" else "✗"}")
                Log.d("MainActivity", "  SYSTEM_INFO: ${if (hasSystemInfo) "✓" else "✗"}")
                Log.d("MainActivity", "  GLOBAL_ACTIONS: ${if (hasGlobalActions) "✓" else "✗"}")
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "✗ Failed to set Lock Task Features", e)
        }

        Log.i("MainActivity", "====================================")
        Log.i("MainActivity", "Kiosk Policies setup completed")
        Log.i("MainActivity", "====================================")
    }
}