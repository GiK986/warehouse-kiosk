package com.warehouse.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
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
//import java.util.concurrent.atomic.AtomicBoolean

/**
 * РАЗШИРЕНА версия на ProvisioningCompleteActivity
 *
 * Тази activity се стартира след успешен Device Owner provisioning
 * (когато Android изпрати ADMIN_POLICY_COMPLIANCE Intent).
 *
 * ЗАДАЧИ:
 * 1. Проверка дали сме Device Owner
 * 2. Активиране на системни приложения (Gboard, Chrome)
 * 3. Настройка като Default Launcher (Home app)
 * 4. Конфигуриране на System UI бутони (Home, Global Actions)
 * 5. Подготовка за Kiosk режим (Lock Task)
 * 6. Показване на потвърдително съобщение
 * 7. Стартиране на MainActivity
 */
class ProvisioningCompleteActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ProvisioningComplete"
        private const val AUTO_START_DELAY_MS = 2000L // 2 секунди
    }

    // Флаг за предотвратяване на двойна навигация
    // private val isNavigating = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "ProvisioningCompleteActivity started")

        // Инициализация на Device Policy Manager
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceOwnerReceiver::class.java)

        // Проверка дали сме Device Owner
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        Log.d(TAG, "Device Owner status: $isDeviceOwner")

        if (!isDeviceOwner) {
            Log.e(TAG, "App is NOT Device Owner! Provisioning may have failed.")
            showErrorAndFinish("Приложението не е Device Owner")
            return
        }

        // Device Owner е потвърден - конфигуриране на устройството
        Log.i(TAG, "Device Owner confirmed! Provisioning completed successfully.")

        // Опционално: Прочитане на provisioning extras (ако има)
        val extras = intent.getBundleExtra("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE")
        if (extras != null) {
            Log.d(TAG, "Provisioning extras received: ${extras.keySet()}")
        }

        // 1. ВАЖНО: Активиране на системни приложения (напр. Клавиатура)
        // При QR provisioning клавиатурата често е деактивирана по подразбиране.
        enableSystemApps(dpm, adminComponent)

        // 2. ВАЖНО: Настройка като Default Launcher (Home app)
        // Това кара Android да стартира MainActivity веднага след boot
        // и прави така, че Home бутонът да връща в твоето приложение.
        setAsDefaultLauncher(dpm, adminComponent)

        // 3. ВАЖНО: Конфигуриране на System UI бутони (Home, Global Actions)
        // Това оправя проблема със "сив Home бутон" в Lock Task режим
        configureSystemButtons(dpm, adminComponent)

        // 4. Подготовка за Kiosk режим (Lock Task)
//        setupKioskPolicies(dpm, adminComponent)

        // Запазване на provisioning статус
        saveProvisioningStatus()

        // Показване на UI
        setContent {
            MaterialTheme {
                ProvisioningSuccessScreen(
                    onContinue = { finishAndStartMain() }
                )
            }
        }

//        // Auto-start след 3 секунди
//        lifecycleScope.launch {
//            delay(AUTO_START_DELAY_MS)
//            finishAndStartMain()
//        }
    }

    @Composable
    private fun ProvisioningSuccessScreen(onContinue: () -> Unit) {
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
                // Success icon/text
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Success message
                Text(
                    text = "Устройството е настроено успешно!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Device Owner режим е активиран",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Auto-start indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Стартиране на приложението...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Manual continue button
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Продължи сега")
                }
            }
        }
    }

    /**
     * Запазване на provisioning статус в SharedPreferences
     */
    private fun saveProvisioningStatus() {
        try {
            val prefs = getSharedPreferences("kiosk_config", MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("is_provisioned", true)
                putLong("provisioned_at", System.currentTimeMillis())
                putString("provisioning_method", "qr_code")
                apply()
            }
            Log.d(TAG, "Provisioning status saved")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save provisioning status", e)
        }
    }

    /**
     * Стартиране на MainActivity и затваряне на тази activity
     */
    private fun finishAndStartMain() {
        try {
            Log.d(TAG, "Starting MainActivity")

            setResult(RESULT_OK)

//            val intent = Intent(this, MainActivity::class.java).apply {
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                putExtra("from_provisioning", true)
//            }

//            startActivity(intent)
            finish()

            Log.i(TAG, "MainActivity started, ProvisioningCompleteActivity finished")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity", e)
            showErrorAndFinish("Грешка при стартиране на приложението")
        }
    }

    /**
     * Показване на грешка и затваряне на activity
     */
    private fun showErrorAndFinish(errorMessage: String) {
        Log.e(TAG, "Error: $errorMessage")

        setContent {
            MaterialTheme {
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
                            text = "✗",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Auto-close след 5 секунди
        lifecycleScope.launch {
            delay(5000)
            finish()
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
            // Можеш да добавиш и други специфични пакети за твоето устройство:
            // dpm.enableSystemApp(adminComponent, "com.samsung.android.honeyboard") // Samsung клавиатура
            Log.i(TAG, "System apps enabled successfully")
        } catch (e: Exception) {
            // Не е фатално, но е добре да се логне
            Log.w(TAG, "Could not enable some system apps: ${e.message}")
        }
    }

    /**
     * Прави това приложение HOME (Launcher) за устройството.
     * След това Home бутонът ще стартира MainActivity.
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

            Log.i(TAG, "Application set as Default Launcher successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set as Default Launcher", e)
        }
    }

    /**
     * Управлява системните бутони (Home, Overview, Notifications)
     * КРИТИЧНО: Това оправя проблема със "сив Home бутон" в Lock Task режим!
     */
    private fun configureSystemButtons(dpm: DevicePolicyManager, adminComponent: ComponentName) {
        try {
            // ВАЖНО: Тези флагове определят какво работи, когато си в Kiosk (startLockTask)

            // LOCK_TASK_FEATURE_HOME -> Home бутонът работи (не е сив)
            // LOCK_TASK_FEATURE_GLOBAL_ACTIONS -> Power менюто работи (при задържане на Power)
            // LOCK_TASK_FEATURE_NOTIFICATIONS -> Можеш да дърпаш щората (опционално)
            // LOCK_TASK_FEATURE_OVERVIEW -> Recents бутонът (обикновено не се слага за Kiosk)

            val flags = DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW    // разкоментирай ако искаш мултитаскинг
            // or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS // разкоментирай ако искаш щора

            dpm.setLockTaskFeatures(adminComponent, flags)

            Log.i(TAG, "System buttons configured. Flags: $flags")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure LockTask features", e)
        }
    }

    /**
     * Подготвя устройството за Kiosk режим (Lock Task).
     * Разрешава на това приложение да използва Lock Task mode.
     */
    private fun setupKioskPolicies(dpm: DevicePolicyManager, adminComponent: ComponentName) {
        try {
            // Разрешаваме на нашето приложение да ползва LockTask (Kiosk) режим
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            Log.i(TAG, "LockTask packages set for: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set LockTask packages", e)
        }
    }
}
