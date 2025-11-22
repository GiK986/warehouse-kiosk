package com.warehouse.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.warehouse.kiosk.services.DeviceOwnerReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ProvisioningCompleteActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ProvisioningComplete"
    }

    private val isNavigating = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceOwnerReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(packageName)) {
            // ... error handling ...
            return
        }

        // 1. Разрешаваме системните приложения (Клавиатура и т.н.)
        enableSystemApps(dpm, adminComponent)

        // 2. СЕТВАМЕ ПРИЛОЖЕНИЕТО КАТО HOME (LAUNCHER)
        // Това кара Android да стартира MainActivity веднага след boot
        // и прави така, че Home бутонът да връща в твоето приложение.
        setAsDefaultLauncher(dpm, adminComponent)

        // 3. КОНФИГУРИРАМЕ БУТОНИТЕ (HOME, OVERVIEW, GLOBAL ACTIONS)
        // Тук оправяме проблема със "сив Home бутон"
        configureSystemButtons(dpm, adminComponent)

        // 4. Подготовка за Kiosk
        setupKioskPolicies(dpm, adminComponent)

        saveProvisioningStatus()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Настройката завърши!\nСтартиране...")
                    }
                }
            }
        }

        lifecycleScope.launch {
            delay(2000)
            navigateToMain()
        }
    }

    /**
     * Прави това приложение HOME (Launcher) за устройството.
     */
    private fun setAsDefaultLauncher(dpm: DevicePolicyManager, adminComponent: ComponentName) {
        try {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            
            // Указваме, че MainActivity е новият Home екран
            val activityComponent = ComponentName(this, MainActivity::class.java)
            
            dpm.addPersistentPreferredActivity(adminComponent, filter, activityComponent)
            
            Log.i(TAG, "Application set as Default Launcher successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set as Default Launcher", e)
        }
    }

    /**
     * Управлява системните бутони (Home, Overview, Notifications)
     * Ако Home и Overview са ти неактивни, това е заради LockTask Features.
     */
    private fun configureSystemButtons(dpm: DevicePolicyManager, adminComponent: ComponentName) {
        try {
            // ВАЖНО: Тези флагове определят какво работи, когато си в Kiosk (startLockTask)
            
            // LOCK_TASK_FEATURE_HOME -> Home бутонът работи (не е сив)
            // LOCK_TASK_FEATURE_GLOBAL_ACTIONS -> Power менюто работи (при задържане на Power)
            // LOCK_TASK_FEATURE_NOTIFICATIONS -> Можеш да дърпаш щората (опционално)
            // LOCK_TASK_FEATURE_OVERVIEW -> Recents бутонът (обикновено не се слага за Kiosk)
            
            val flags = DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                        DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                        // or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS // разкоментирай ако искаш щора
                        // or DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW    // разкоментирай ако искаш мултитаскинг

            dpm.setLockTaskFeatures(adminComponent, flags)
            
            Log.i(TAG, "System buttons configured. Flags: $flags")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure LockTask features", e)
        }
    }
    
    // ... enableSystemApps, setupKioskPolicies, navigateToMain (от предния отговор) ...
     private fun enableSystemApps(dpm: DevicePolicyManager, adminComponent: ComponentName) {
        try {
            dpm.enableSystemApp(adminComponent, "com.google.android.inputmethod.latin")
            dpm.enableSystemApp(adminComponent, "com.android.chrome")
        } catch (e: Exception) { Log.w(TAG, "Err enable apps: $e") }
    }

    private fun setupKioskPolicies(dpm: DevicePolicyManager, adminComponent: ComponentName) {
        dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
    }

    private fun saveProvisioningStatus() { /*...*/ }
    
    private fun navigateToMain() {
        if (isNavigating.getAndSet(true)) return
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}