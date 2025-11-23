package com.warehouse.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.warehouse.kiosk.data.repository.ApkRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.warehouse.kiosk.services.DeviceOwnerReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONObject

/**
 * ProvisioningCompleteActivity
 *
 * Тази activity се стартира след успешен Device Owner provisioning
 * (когато Android изпрати ADMIN_POLICY_COMPLIANCE Intent).
 *
 * ЗАДАЧИ:
 * 1. Проверка дали сме Device Owner
 * 2. Настройка като Default Launcher (Home app)
 * 3. Конфигуриране на допълнителна WiFi мрежа (ако има)
 * 4. Запазване на provisioning статус
 * 5. Показване на резултатите
 * 6. Finish() за да продължи Setup Wizard
 *
 * ВАЖНО: НЕ задаваме kiosk policies тук!
 * MainActivity ще ги зададе след като Setup Wizard завърши.
 */
@AndroidEntryPoint
class ProvisioningCompleteActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ProvisioningComplete"
    }

    @Inject
    lateinit var apkRepository: ApkRepository

    // Data classes за резултатите
    data class ProvisioningStep(
        val name: String,
        val status: StepStatus,
        val message: String = "",
        val icon: ImageVector
    )

    enum class StepStatus {
        SUCCESS,
        WARNING,
        FAILED,
        SKIPPED
    }

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
            showErrorAndFinish()
            return
        }

        // Device Owner е потвърден
        Log.i(TAG, "Device Owner confirmed! Provisioning completed successfully.")

        // Прочитане на provisioning extras
        val extras: PersistableBundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Този ред е валиден само за Android 13+ (API 33)
            intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                PersistableBundle::class.java
            )
        } else {
            // За Android 12 и по-стари ползваме стария метод
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE
            ) as? PersistableBundle
        }
        if (extras != null) {
            Log.d(TAG, "Provisioning extras received: ${extras.keySet()}")
        }
        else {
            Log.d(TAG, "Provisioning extras not received")
        }

        // Изпълняване на provisioning стъпки и събиране на резултати
        val steps = mutableListOf<ProvisioningStep>()

        // 1. Set as Default Launcher
        steps.add(setAsDefaultLauncher(dpm, adminComponent))

        // 2. Configure WiFi - може да дойде като PersistableBundle ИЛИ като JSON string
        val wifiConfig = extractWifiConfig(extras)
        steps.add(configureLocationWifi(wifiConfig))

        // 3. Save provisioning status
        steps.add(saveProvisioningStatusWithResult())

        // 4. WMS APK install (async в background)
        val wmsApkUrl = extras?.getString("wms_apk_url")
        val wmsInstallStep: MutableState<ProvisioningStep?> = mutableStateOf(null)

        if (!wmsApkUrl.isNullOrBlank()) {
            Log.i(TAG, "WMS APK URL found: $wmsApkUrl")
            wmsInstallStep.value = ProvisioningStep(
                name = "WMS приложение",
                status = StepStatus.WARNING,
                message = "Изтегляне...",
                icon = Icons.Default.Download
            )

            // Стартираме download/install асинхронно
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Download
                    val apkFile = apkRepository.downloadApk(wmsApkUrl)

                    withContext(Dispatchers.Main) {
                        wmsInstallStep.value = ProvisioningStep(
                            name = "WMS приложение",
                            status = StepStatus.WARNING,
                            message = "Инсталиране...",
                            icon = Icons.Default.Download
                        )
                    }

                    // Install
                    val success = apkRepository.installApk(apkFile)

                    // Cleanup
                    apkRepository.cleanupApkFile(apkFile)

                    withContext(Dispatchers.Main) {
                        wmsInstallStep.value = if (success) {
                            ProvisioningStep(
                                name = "WMS приложение",
                                status = StepStatus.SUCCESS,
                                message = "Инсталирано успешно",
                                icon = Icons.Default.Download
                            )
                        } else {
                            ProvisioningStep(
                                name = "WMS приложение",
                                status = StepStatus.FAILED,
                                message = "Грешка при инсталация",
                                icon = Icons.Default.Download
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to install WMS APK", e)
                    withContext(Dispatchers.Main) {
                        wmsInstallStep.value = ProvisioningStep(
                            name = "WMS приложение",
                            status = StepStatus.FAILED,
                            message = e.message ?: "Грешка",
                            icon = Icons.Default.Download
                        )
                    }
                }
            }
        } else {
            Log.d(TAG, "No WMS APK URL in provisioning extras")
            wmsInstallStep.value = ProvisioningStep(
                name = "WMS приложение",
                status = StepStatus.SKIPPED,
                message = "Няма URL за изтегляне",
                icon = Icons.Default.Download
            )
        }

        // Показване на UI с резултатите
        setContent {
            MaterialTheme {
                ProvisioningSuccessScreen(
                    steps = steps,
                    wmsInstallStep = wmsInstallStep.value,
                    onContinue = { proceedToMainActivity() }
                )
            }
        }
    }

    @Composable
    private fun ProvisioningSuccessScreen(
        steps: List<ProvisioningStep>,
        wmsInstallStep: ProvisioningStep?,
        onContinue: () -> Unit
    ) {
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
                // Success header
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Устройството е настроено!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Device Owner режим е активиран",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Списък със стъпките
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        steps.forEachIndexed { index, step ->
                            ProvisioningStepRow(step)
                            if (index < steps.size - 1 || wmsInstallStep != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            }
                        }

                        // WMS Install step (динамичен)
                        if (wmsInstallStep != null) {
                            ProvisioningStepRow(wmsInstallStep)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))


                Spacer(modifier = Modifier.height(24.dp))

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

    @Composable
    private fun ProvisioningStepRow(step: ProvisioningStep) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = when (step.status) {
                        StepStatus.SUCCESS -> Color(0xFF4CAF50)
                        StepStatus.WARNING -> Color(0xFFFF9800)
                        StepStatus.FAILED -> Color(0xFFF44336)
                        StepStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = step.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (step.message.isNotEmpty()) {
                        Text(
                            text = step.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                imageVector = when (step.status) {
                    StepStatus.SUCCESS -> Icons.Default.CheckCircle
                    StepStatus.WARNING -> Icons.Default.Warning
                    StepStatus.FAILED -> Icons.Default.Error
                    StepStatus.SKIPPED -> Icons.Default.Remove
                },
                contentDescription = null,
                tint = when (step.status) {
                    StepStatus.SUCCESS -> Color(0xFF4CAF50)
                    StepStatus.WARNING -> Color(0xFFFF9800)
                    StepStatus.FAILED -> Color(0xFFF44336)
                    StepStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }

    /**
     * Извлича WiFi конфигурацията от admin extras.
     * Поддържа ДВА формата:
     * 1. PersistableBundle (nested object в QR code) - ИДЕАЛНО
     * 2. JSON String (workaround когато QR generator го serialized)
     */
    private fun extractWifiConfig(extras: PersistableBundle?): PersistableBundle? {
        if (extras == null) return null

        // Опит 1: Nested PersistableBundle (правилният формат)
        val bundleWifi = extras.getPersistableBundle("location_wifi")
        if (bundleWifi != null) {
            Log.d(TAG, "WiFi config found as PersistableBundle")
            return bundleWifi
        }

        // Опит 2: JSON String (fallback ако е serialized)
        val jsonString = extras.getString("location_wifi")
        if (!jsonString.isNullOrEmpty()) {
            Log.d(TAG, "WiFi config found as JSON string, parsing...")
            return try {
                val json = JSONObject(jsonString)
                PersistableBundle().apply {
                    putString("wifi_ssid", json.optString("wifi_ssid"))
                    putString("wifi_password", json.optString("wifi_password"))
                    putString("wifi_security_type", json.optString("wifi_security_type"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse WiFi JSON string", e)
                null
            }
        }

        Log.d(TAG, "No WiFi config found in admin extras")
        return null
    }

    /**
     * Прави това приложение HOME (Launcher) за устройството.
     */
    private fun setAsDefaultLauncher(
        dpm: DevicePolicyManager,
        adminComponent: ComponentName
    ): ProvisioningStep {
        return try {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }

            val activityComponent = ComponentName(this, MainActivity::class.java)
            dpm.addPersistentPreferredActivity(adminComponent, filter, activityComponent)

            Log.i(TAG, "Application set as Default Launcher successfully")
            ProvisioningStep(
                name = "Default Launcher",
                status = StepStatus.SUCCESS,
                message = "Приложението е Home екран",
                icon = Icons.Default.Home
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set as Default Launcher", e)
            ProvisioningStep(
                name = "Default Launcher",
                status = StepStatus.FAILED,
                message = e.message ?: "Грешка при настройка",
                icon = Icons.Default.Home
            )
        }
    }

    /**
     * Конфигурира допълнителна WiFi мрежа ако е зададена в provisioning extras.
     * ВАЖНО: location_wifi идва като nested PersistableBundle от QR code JSON!
     */
    private fun configureLocationWifi(wifiBundle: PersistableBundle?): ProvisioningStep {
        if (wifiBundle == null) {
            return ProvisioningStep(
                name = "WiFi мрежа",
                status = StepStatus.SKIPPED,
                message = "Няма допълнителна мрежа",
                icon = Icons.Default.WifiOff
            )
        }

        return try {
            // Извличаме данните от PersistableBundle (не от JSON!)
            val ssid = wifiBundle.getString("wifi_ssid") ?: ""
            val password = wifiBundle.getString("wifi_password") ?: ""
            val securityType = wifiBundle.getString("wifi_security_type") ?: "WPA"

            Log.d(TAG, "WiFi config from bundle: SSID=$ssid, Security=$securityType")

            if (ssid.isEmpty()) {
                return ProvisioningStep(
                    name = "WiFi мрежа",
                    status = StepStatus.SKIPPED,
                    message = "Липсва SSID",
                    icon = Icons.Default.WifiOff
                )
            }

            val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
            val suggestionBuilder = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setIsAppInteractionRequired(false)

            when (securityType.uppercase()) {
                "WPA", "WPA2" -> suggestionBuilder.setWpa2Passphrase(password)
                "WPA3", "SAE" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        suggestionBuilder.setWpa3Passphrase(password)
                    } else {
                        return ProvisioningStep(
                            name = "WiFi мрежа",
                            status = StepStatus.WARNING,
                            message = "WPA3 изисква Android 10+",
                            icon = Icons.Default.Wifi
                        )
                    }
                }
                "NONE" -> {
                    // Отворена мрежа
                }
            }

            val suggestion = suggestionBuilder.build()
            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))

            if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Log.i(TAG, "WiFi network $ssid added successfully")
                ProvisioningStep(
                    name = "WiFi мрежа",
                    status = StepStatus.SUCCESS,
                    message = "Добавена: $ssid",
                    icon = Icons.Default.Wifi
                )
            } else {
                Log.e(TAG, "Failed to add WiFi network. Status: $status")
                ProvisioningStep(
                    name = "WiFi мрежа",
                    status = StepStatus.WARNING,
                    message = "Код на грешка: $status",
                    icon = Icons.Default.Wifi
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring WiFi", e)
            ProvisioningStep(
                name = "WiFi мрежа",
                status = StepStatus.FAILED,
                message = e.message ?: "Грешка при конфигурация",
                icon = Icons.Default.Wifi
            )
        }
    }

    /**
     * Запазване на provisioning статус в SharedPreferences.
     */
    private fun saveProvisioningStatusWithResult(): ProvisioningStep {
        return try {
            val prefs = getSharedPreferences("kiosk_config", MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("is_provisioned", true)
                putLong("provisioned_at", System.currentTimeMillis())
                putString("provisioning_method", "qr_code")
                apply()
            }
            Log.d(TAG, "Provisioning status saved")
            ProvisioningStep(
                name = "Provisioning статус",
                status = StepStatus.SUCCESS,
                message = "Запазен успешно",
                icon = Icons.Default.Save
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save provisioning status", e)
            ProvisioningStep(
                name = "Provisioning статус",
                status = StepStatus.WARNING,
                message = "Грешка при запазване",
                icon = Icons.Default.Save
            )
        }
    }

    /**
     * Finish() и позволи на Setup Wizard да продължи.
     * MainActivity ще се стартира автоматично след като Setup Wizard завърши.
     */
    private fun proceedToMainActivity() {
        setResult(RESULT_OK)
        finish()
        Log.i(TAG, "ProvisioningCompleteActivity finished")
    }

    /**
     * Показване на грешка и затваряне на activity
     */
    private fun showErrorAndFinish() {
        val errorMessage = "Приложението не е Device Owner"
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
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}