package com.warehouse.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity който се изпълнява след успешно QR code provisioning
 * Настройва допълнителни параметри които не могат да се зададат през QR кода
 */
class ProvisioningCompleteActivity : ComponentActivity() {

    // ==================== КОНФИГУРАЦИЯ - ПРОМЕНЕТЕ ТЕЗИ СТОЙНОСТИ ====================
    
    companion object {

        private const val ADMIN_RECEIVER_CLASS = "com.warehouse.kiosk.DeviceAdminReceiver"
        

        private const val MAIN_LAUNCHER_CLASS = "com.warehouse.kiosk.MainActivity"
        
        // TODO: Изберете клавиатура (uncomment една от опциите)
        private const val DEFAULT_KEYBOARD = "com.google.android.inputmethod.latin/.LatinIME" // Gboard
//         private const val DEFAULT_KEYBOARD = "com.android.inputmethod.latin/.LatinIME" // AOSP
        
        // TODO: Допълнителни приложения за инсталиране
        private val ADDITIONAL_APPS = listOf(
            AppToInstall(
                name = "Barcode Scanner",
                url = "https://your-server.com/apps/scanner.apk", // TODO: Заменете
                packageName = "com.example.scanner"
            )
        )
        
        // TODO: Kiosk Mode настройки
        private const val ENABLE_FULL_KIOSK = true // true = само launcher, false = multi-app
        private val ALLOWED_APPS = listOf(
            "com.warehouse.kiosk", // TODO: Вашият package
             "com.android.settings", // Uncomment ако искате достъп до Settings
        )
        
        // TODO: Екран настройки
        private const val SCREEN_TIMEOUT_MS = 600000 // 10 минути (0 = never)
        private const val SCREEN_BRIGHTNESS = 200 // 0-255, или -1 за auto
        private const val STAY_AWAKE_WHILE_CHARGING = true
        
        // TODO: Звук настройки
        private const val SOUND_ENABLED = false
        
        // TODO: Warehouse настройки (от provisioning extras)
        private const val EXTRA_WAREHOUSE_ID = "warehouse_id"
        private const val EXTRA_SERVER_URL = "server_url"
    }
    
    // ==================== DATA CLASSES ====================
    
    data class AppToInstall(
        val name: String,
        val url: String,
        val packageName: String
    )
    
    data class ProvisioningStep(
        val title: String,
        val status: StepStatus = StepStatus.PENDING
    )
    
    enum class StepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
    
    // ==================== ACTIVITY LIFECYCLE ====================
    
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализация
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, ADMIN_RECEIVER_CLASS)
        
        // Проверка дали сме Device Owner
        if (!dpm.isDeviceOwnerApp(packageName)) {
            showError("Приложението не е Device Owner!")
            return
        }
        
        // Четене на provisioning extras
        val extras = intent.getBundleExtra("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE")
        val warehouseId = extras?.getString(EXTRA_WAREHOUSE_ID) ?: "UNKNOWN"
        val serverUrl = extras?.getString(EXTRA_SERVER_URL) ?: "https://default-server.com"
        
        // Compose UI
        setContent {
            MaterialTheme {
                ProvisioningScreen(
                    warehouseId = warehouseId,
                    serverUrl = serverUrl,
                    onComplete = { finishProvisioning() }
                )
            }
        }
    }
    
    // ==================== COMPOSE UI ====================
    
    @Composable
    fun ProvisioningScreen(
        warehouseId: String,
        serverUrl: String,
        onComplete: () -> Unit
    ) {
        var steps by remember {
            mutableStateOf(
                listOf(
                    ProvisioningStep("Настройка на клавиатура"),
                    ProvisioningStep("Конфигуриране на екран"),
                    ProvisioningStep("Настройка на звук"),
                    ProvisioningStep("Изтегляне на допълнителни приложения"),
                    ProvisioningStep("Активиране на Kiosk Mode"),
                    ProvisioningStep("Запазване на конфигурация")
                )
            )
        }
        
        var isComplete by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        
        // Автоматично стартиране на provisioning
        LaunchedEffect(Unit) {
            scope.launch {
                executeProvisioningSteps(
                    warehouseId = warehouseId,
                    serverUrl = serverUrl,
                    onStepUpdate = { index, status ->
                        steps = steps.toMutableList().apply {
                            this[index] = this[index].copy(status = status)
                        }
                    },
                    onComplete = {
                        isComplete = true
                        onComplete()
                    }
                )
            }
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                // Header
                Text(
                    text = "Настройка на устройство",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Warehouse: $warehouseId",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Progress indicator
                if (!isComplete) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                // Steps list
                steps.forEachIndexed { index, step ->
                    ProvisioningStepItem(
                        step = step,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index < steps.size - 1) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Complete message
                if (isComplete) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✓ Настройката завърши успешно!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Устройството ще стартира автоматично...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun ProvisioningStepItem(
        step: ProvisioningStep,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = when (step.status) {
                    StepStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                    StepStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                
                when (step.status) {
                    StepStatus.PENDING -> Text("⏳", style = MaterialTheme.typography.headlineSmall)
                    StepStatus.IN_PROGRESS -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    StepStatus.COMPLETED -> Text("✓", style = MaterialTheme.typography.headlineSmall)
                    StepStatus.FAILED -> Text("✗", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
    
    // ==================== PROVISIONING LOGIC ====================
    
    private suspend fun executeProvisioningSteps(
        warehouseId: String,
        serverUrl: String,
        onStepUpdate: (Int, StepStatus) -> Unit,
        onComplete: () -> Unit
    ) {
        try {
            // Стъпка 1: Клавиатура
            onStepUpdate(0, StepStatus.IN_PROGRESS)
            delay(500)
            configureKeyboard()
            onStepUpdate(0, StepStatus.COMPLETED)
            
            // Стъпка 2: Екран
            onStepUpdate(1, StepStatus.IN_PROGRESS)
            delay(500)
            configureScreen()
            onStepUpdate(1, StepStatus.COMPLETED)
            
            // Стъпка 3: Звук
            onStepUpdate(2, StepStatus.IN_PROGRESS)
            delay(500)
            configureSound()
            onStepUpdate(2, StepStatus.COMPLETED)
            
            // Стъпка 4: Допълнителни приложения
            onStepUpdate(3, StepStatus.IN_PROGRESS)
            installAdditionalApps()
            onStepUpdate(3, StepStatus.COMPLETED)
            
            // Стъпка 5: Kiosk Mode
            onStepUpdate(4, StepStatus.IN_PROGRESS)
            delay(500)
            enableKioskMode()
            onStepUpdate(4, StepStatus.COMPLETED)
            
            // Стъпка 6: Запазване на конфигурация
            onStepUpdate(5, StepStatus.IN_PROGRESS)
            delay(500)
            saveConfiguration(warehouseId, serverUrl)
            onStepUpdate(5, StepStatus.COMPLETED)
            
            // Готово!
            onComplete()
            
        } catch (e: Exception) {
            e.printStackTrace()
            // TODO: Handle error - можете да добавите error dialog
        }
    }
    
    // ==================== CONFIGURATION METHODS ====================
    
    private fun configureKeyboard() {
        try {
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                DEFAULT_KEYBOARD
            )
            
            // TODO: Допълнителни keyboard настройки при нужда
            // Settings.Secure.putInt(contentResolver, "sound_effects_enabled", 0)
            // Settings.System.putInt(contentResolver, "haptic_feedback_enabled", 0)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun configureScreen() {
        try {
            // Screen timeout
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                SCREEN_TIMEOUT_MS
            )
            
            // Brightness
            if (SCREEN_BRIGHTNESS >= 0) {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    SCREEN_BRIGHTNESS
                )
            } else {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
            }
            
            // Stay awake while charging
            if (STAY_AWAKE_WHILE_CHARGING) {
                dpm.setGlobalSetting(
                    adminComponent,
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                    "7" // USB + AC + Wireless
                )
            }
            
            // TODO: Rotation lock при нужда
            // Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            // Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun configureSound() {
        try {
            // Touch sounds
            Settings.System.putInt(
                contentResolver,
                Settings.System.SOUND_EFFECTS_ENABLED,
                if (SOUND_ENABLED) 1 else 0
            )
            
            // TODO: Volume настройки при нужда
            // val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private suspend fun installAdditionalApps() {
        // TODO: Имплементирайте инсталация на APK-та
        // Вариант 1: PackageInstaller API
        // Вариант 2: DevicePolicyManager.installPackage() (Android 9+)
        
        for (app in ADDITIONAL_APPS) {
            try {
                // Simulate download
                delay(1000)
                
                // TODO: Real implementation
                // val apkFile = downloadApk(app.url)
                // installApk(apkFile, app.packageName)
                
                println("Installing: ${app.name}")
                
            } catch (e: Exception) {
                e.printStackTrace()
                // TODO: Handle failed installation
            }
        }
    }
    
    private fun enableKioskMode() {
        try {
            if (ENABLE_FULL_KIOSK) {
                // Lock task mode за пълен kiosk
                dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
                
                // Persistent preferred activities
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                
                dpm.clearPackagePersistentPreferredActivities(
                    adminComponent,
                    packageName
                )
                
                dpm.addPersistentPreferredActivity(
                    adminComponent,
                    intent.component?.let { android.content.IntentFilter().apply { addAction(Intent.ACTION_MAIN) } },
                    ComponentName(packageName, MAIN_LAUNCHER_CLASS)
                )
            }
            
            // Забрани status bar (Android 9+)
            dpm.setStatusBarDisabled(adminComponent, ENABLE_FULL_KIOSK)
            
            // Забрани keyguard (lock screen)
            dpm.setKeyguardDisabled(adminComponent, true)
            
            // TODO: Допълнителни ограничения
            // dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            // dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            // dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun saveConfiguration(warehouseId: String, serverUrl: String) {
        // TODO: Запазете конфигурацията в SharedPreferences или файл
        val prefs = getSharedPreferences("kiosk_config", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("warehouse_id", warehouseId)
            putString("server_url", serverUrl)
            putLong("provisioned_at", System.currentTimeMillis())
            putBoolean("is_provisioned", true)
            apply()
        }
    }
    
    // ==================== FINISHING ====================
    
    private fun finishProvisioning() {
        // Стартирай главния Launcher
        val intent = Intent(this, Class.forName(MAIN_LAUNCHER_CLASS))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        
        // Ако е пълен kiosk, стартирай Lock Task Mode
        if (ENABLE_FULL_KIOSK) {
            startLockTask()
        }
        
        finish()
    }
    
    private fun showError(message: String) {
        // TODO: Покажете error dialog или екран
        println("ERROR: $message")
        finish()
    }
}
