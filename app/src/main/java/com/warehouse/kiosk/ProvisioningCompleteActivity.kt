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

/**
 * МИНИМАЛНА версия на ProvisioningCompleteActivity
 *
 * Тази activity се стартира след успешен Device Owner provisioning
 * (когато Android изпрати ADMIN_POLICY_COMPLIANCE Intent).
 *
 * ЗАДАЧИ:
 * 1. Проверка дали сме Device Owner
 * 2. Показване на потвърдително съобщение
 * 3. Стартиране на MainActivity
 *
 * БЕЛЕЖКА: Това е минимална версия без рискови Settings промени.
 * След успешен тест може да се разшири с допълнителна функционалност.
 */
class ProvisioningCompleteActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ProvisioningComplete"
        private const val AUTO_START_DELAY_MS = 3000L // 3 секунди
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "ProvisioningCompleteActivity started")

        // Инициализация на Device Policy Manager
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceOwnerReceiver::class.java)

        // Проверка дали сме Device Owner
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        Log.d(TAG, "Device Owner status: $isDeviceOwner")

        if (!isDeviceOwner) {
            Log.e(TAG, "App is NOT Device Owner! Provisioning may have failed.")
            showErrorAndFinish("Приложението не е Device Owner")
            return
        }

        // Device Owner е потвърден - показваме UI
        Log.i(TAG, "Device Owner confirmed! Provisioning completed successfully.")

        // Опционално: Прочитане на provisioning extras (ако има)
        val extras = intent.getBundleExtra("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE")
        if (extras != null) {
            Log.d(TAG, "Provisioning extras received: ${extras.keySet()}")
            // Можем да използваме extras за бъдеща конфигурация
        }

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

        // Auto-start след 3 секунди
        lifecycleScope.launch {
            delay(AUTO_START_DELAY_MS)
            finishAndStartMain()
        }
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
            val prefs = getSharedPreferences("kiosk_config", Context.MODE_PRIVATE)
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

            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("from_provisioning", true)
            }

            startActivity(intent)
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
}
