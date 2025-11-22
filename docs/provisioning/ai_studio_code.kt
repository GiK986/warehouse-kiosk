package com.warehouse.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.warehouse.kiosk.services.DeviceOwnerReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ProvisioningCompleteActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ProvisioningComplete"
        private const val AUTO_START_DELAY_MS = 3000L
    }

    // Флаг за предотвратяване на двойна навигация
    private val isNavigating = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceOwnerReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(packageName)) {
            Log.e(TAG, "App is NOT Device Owner!")
            showErrorAndFinish("Грешка: Приложението не е администратор!")
            return
        }

        // 1. ВАЖНО: Активиране на системни приложения (напр. Клавиатура)
        // При QR provisioning клавиатурата често е деактивирана по подразбиране.
        enableSystemApps(dpm, adminComponent)

        // 2. ВАЖНО: Предварително разрешаване на LockTask (Kiosk mode)
        // Това позволява на MainActivity да влезе в Kiosk режим без диалози.
        setupKioskPolicies(dpm, adminComponent)

        saveProvisioningStatus()

        setContent {
            MaterialTheme {
                ProvisioningSuccessScreen(
                    onContinue = { navigateToMain() }
                )
            }
        }

        lifecycleScope.launch {
            delay(AUTO_START_DELAY_MS)
            navigateToMain()
        }
    }

    /**
     * Разрешава системни приложения, които може да са скрити след provisioning.
     * Това е критично за клавиатурата (Gboard, Samsung Keyboard и др.)
     */
    private fun enableSystemApps(dpm: DevicePolicyManager, adminComponent: ComponentName) {
        try {
            // Този метод активира всички системни UI елементи, които може да са били спрени
            dpm.enableSystemApp(adminComponent, "com.google.android.inputmethod.latin") // Gboard
            dpm.enableSystemApp(adminComponent, "com.android.chrome") // WebView/Chrome
            // Може да добавиш и други специфични пакети за твоето устройство
            Log.i(TAG, "System apps enabled successfully")
        } catch (e: Exception) {
            // Не е фатално, но е добре да се логне
            Log.w(TAG, "Could not enable some system apps: ${e.message}")
        }
    }

    private fun setupKioskPolicies(dpm: DevicePolicyManager, adminComponent: ComponentName) {
        try {
            // Разрешаваме на нашето приложение да ползва LockTask (Kiosk) режим
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            Log.i(TAG, "LockTask packages set for: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set LockTask packages", e)
        }
    }

    /**
     * Thread-safe метод за навигация
     */
    private fun navigateToMain() {
        // Ако вече сме навигирали, не правим нищо (atomic check-and-set)
        if (isNavigating.getAndSet(true)) {
            return
        }

        try {
            Log.d(TAG, "Starting MainActivity...")
            val intent = Intent(this, MainActivity::class.java).apply {
                // Flags: 
                // NEW_TASK - стартира в нов task
                // CLEAR_TASK - изчиства всичко предишно (вкл. тази Activity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("from_provisioning", true)
            }
            startActivity(intent)
            
            // Изрично извикваме finish, въпреки CLEAR_TASK, за по-чисто поведение
            finish() 
            
            // Премахваме анимацията на преход, за да изглежда като мигновена смяна (по желание)
            overridePendingTransition(0, 0) 
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity", e)
            isNavigating.set(false) // Reset flag in case of error
            showErrorAndFinish("Грешка при стартиране.")
        }
    }
    
    // ... останалата част от UI кода (ProvisioningSuccessScreen) си е супер ...
    @Composable
    private fun ProvisioningSuccessScreen(onContinue: () -> Unit) {
       // Твоят код тук е добре
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Устройството е готово!",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onContinue) {
                    Text("СТАРТ")
                }
            }
        }
    }

    // ... saveProvisioningStatus и showErrorAndFinish са ок ...
    private fun saveProvisioningStatus() { /* Твоят код */ }
    private fun showErrorAndFinish(msg: String) { /* Твоят код */ }
}