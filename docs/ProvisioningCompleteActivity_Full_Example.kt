/**
 * ProvisioningCompleteActivity.kt
 * 
 * Това е активност която се стартира когато Device Owner provisioning е завършен.
 * 
 * Магистралния поток:
 * 1. Потребител сканира QR код на Welcome екран
 * 2. Система инсталира DPC приложението
 * 3. Система праща ADMIN_POLICY_COMPLIANCE Intent
 * 4. Тази активност се стартира
 * 5. Показваме приветствено съобщение
 * 6. Позволяваме на потребителя да отиде в главното приложение
 * 
 * Файл: app/src/main/java/com/example/mydeviceowner/ProvisioningCompleteActivity.kt
 */

package com.example.mydeviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Активност показвана след успешно Device Owner provisioning
 */
class ProvisioningCompleteActivity : AppCompatActivity() {

    // ════════════════════════════════════════════════════════════════
    // КОНСТАНТИ И ПРОМЕНЛИВИ
    // ════════════════════════════════════════════════════════════════
    
    companion object {
        private const val TAG = "ProvisioningComplete"
    }

    // Device Policy Manager за управление на политики
    private lateinit var dpm: DevicePolicyManager
    
    // Компонент на Device Admin Receiver
    private lateinit var adminComponent: ComponentName
    
    // UI елементи
    private lateinit var statusText: TextView
    private lateinit var messageText: TextView
    private lateinit var continueButton: Button
    private lateinit var enableKioskButton: Button
    private lateinit var progressBar: ProgressBar

    // ════════════════════════════════════════════════════════════════
    // LIFECYCLE МЕТОДИ
    // ════════════════════════════════════════════════════════════════
    
    /**
     * onCreate е извикана когато активността е създадена
     * 
     * Това се случва когато:
     * - Потребител стартира активност
     * - Система праща Intent след provisioning
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // СТЪПКА 1: Зареди layout от res/layout/activity_provisioning_complete.xml
        setContentView(R.layout.activity_provisioning_complete)
        
        Log.d(TAG, "════════════════════════════════════════════════════════════")
        Log.d(TAG, "ProvisioningCompleteActivity създадена")
        Log.d(TAG, "════════════════════════════════════════════════════════════")
        
        // СТЪПКА 2: Инициализирай Device Policy Manager
        // Това е системния сервис за управление на админ политики
        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        
        // СТЪПКА 3: Създай ComponentName на Device Admin Receiver
        // Това е референцата към нашия admin component
        adminComponent = ComponentName(this, DeviceOwnerReceiver::class.java)
        
        // СТЪПКА 4: Намери UI елементи от layout
        findViewElements()
        
        // СТЪПКА 5: Обработи Intent който е стартирал тази активност
        // Вътре са информацията за provisioning процеса
        handleProvisioningIntent(intent)
        
        // СТЪПКА 6: Провери дали сме Device Owner
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        Log.d(TAG, "Device Owner статус: $isDeviceOwner")
        
        // СТЪПКА 7: Актуализирай UI във зависимост от статуса
        updateUI(isDeviceOwner)
        
        // СТЪПКА 8: Установи button listener-и
        setupButtons()
        
        // СТЪПКА 9: Прилови начални политики ако е Device Owner
        if (isDeviceOwner) {
            applyInitialPolicies()
        }
    }

    /**
     * onResume е извикана когато активността е видима на екран
     * Можеш да обновиш UI тук ако трябва
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() - Активността е видима")
    }

    /**
     * onPause е извикана когато активността не е видима
     * Спри фоновите операции тук
     */
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() - Активността не е видима")
    }

    // ════════════════════════════════════════════════════════════════
    // НАМИРАНЕ НА UI ЕЛЕМЕНТИ
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Намери всички UI елементи от layout файла
     * findViewById() търси по ID в layout XML файла
     */
    private fun findViewElements() {
        statusText = findViewById(R.id.status_text)
        messageText = findViewById(R.id.message_text)
        continueButton = findViewById(R.id.continue_button)
        enableKioskButton = findViewById(R.id.enable_kiosk_button)
        progressBar = findViewById(R.id.progress_bar)
    }

    // ════════════════════════════════════════════════════════════════
    // ОБРАБОТКА НА INTENT
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Обработка на Intent който е стартирал тази активност
     * 
     * Intent-ът съдържа информацията за provisioning процеса
     * 
     * @param intent Intent който е стартирал активността
     */
    private fun handleProvisioningIntent(intent: Intent?) {
        
        // Проверка че intent не е null
        if (intent == null) {
            Log.w(TAG, "⚠️ Intent е null - не от provisioning процес")
            return
        }

        // Вземи действието (action) на Intent-а
        val action = intent.action
        Log.d(TAG, "Intent action: $action")

        // Вземи всички допълнителни данни (extras)
        val extras = intent.extras
        
        // Проверка дали това е ADMIN_POLICY_COMPLIANCE Intent
        if (action == "android.app.action.ADMIN_POLICY_COMPLIANCE") {
            Log.d(TAG, "✅ Получени ADMIN_POLICY_COMPLIANCE Intent")
            Log.d(TAG, "════════════════════════════════════════")
            
            // Извлеци информацията от extras Bundle
            if (extras != null) {
                // STATUS - дали е успешно?
                // 0 = успешно, други = неуспешно
                val status = extras.getInt(
                    "android.intent.extra.STATUS", 
                    -1
                )
                Log.d(TAG, "Статус: $status")
                
                // PACKAGE_NAME - кое приложение?
                val packageName = extras.getString(
                    "android.intent.extra.PACKAGE_NAME"
                )
                Log.d(TAG, "Пакет: $packageName")
                
                // SESSION_ID - ID на инсталационната сесия
                val sessionId = extras.getInt(
                    "android.intent.extra.SESSION_ID", 
                    -1
                )
                Log.d(TAG, "Session ID: $sessionId")
            }
        }

        // Проверка за ADMIN_EXTRAS_BUNDLE
        // Това са допълнителните параметри което предадохме в QR код
        val adminExtras = intent.getBundleExtra(
            "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"
        )
        
        if (adminExtras != null) {
            Log.d(TAG, "Admin extras получени:")
            for (key in adminExtras.keySet()) {
                Log.d(TAG, "  $key = ${adminExtras.get(key)}")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // АКТУАЛИЗИРАНЕ НА UI
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Актуализирай екранът въз основа на статуса на Device Owner
     * 
     * @param isDeviceOwner true ако приложението е Device Owner
     */
    private fun updateUI(isDeviceOwner: Boolean) {
        
        if (isDeviceOwner) {
            // СЛУЧАЙ 1: Успешен provisioning
            Log.d(TAG, "✅ Успешен provisioning - Device Owner статус е потвърден")
            
            // Скрий progress bar
            progressBar.visibility = View.GONE
            
            // Актуализирай главния статус текст
            statusText.text = "✅ Device Owner е активен!"
            statusText.setTextColor(Color.parseColor("#48bb78"))  // Зелен цвят
            
            // Актуализирай съобщението
            messageText.text = """
                Поздравления!
                
                Устройството е успешно конфигурирано като Device Owner.
                Можеш сега да управляваш устройството с административни привилегии.
                
                Възможности:
                • Управление на приложения
                • Задаване на парола политика
                • Активиране на киоск режим
                • Управление на системни настройки
            """.trimIndent()
            
            // Активирай бутоните
            continueButton.isEnabled = true
            enableKioskButton.isEnabled = true
            
        } else {
            // СЛУЧАЙ 2: Неуспешен provisioning
            Log.e(TAG, "❌ Неуспешен provisioning")
            
            // Скрий progress bar
            progressBar.visibility = View.GONE
            
            // Актуализирай главния статус текст
            statusText.text = "❌ Грешка при конфигуриране"
            statusText.setTextColor(Color.parseColor("#f56565"))  // Червен цвят
            
            // Актуализирай съобщението
            messageText.text = """
                Възникна проблем при конфигуриране на устройството.
                
                Устройството НЕ е Device Owner.
                
                Моля, повтори provisioning процеса:
                1. Направи factory reset на устройството
                2. На Welcome екран тапни 6 пъти
                3. Свържи се към Wyfi
                4. Сканирай QR кода отново
            """.trimIndent()
            
            // Деактивирай киоск button, но остави continue
            enableKioskButton.isEnabled = false
        }
    }

    // ════════════════════════════════════════════════════════════════
    // УСТАНОВЯВАНЕ НА BUTTON ДЕЙСТВИЯ
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Установи onclick listener за всички бутони
     */
    private fun setupButtons() {
        
        // CONTINUE BUTTON
        // Отвори главната активност на приложението
        continueButton.setOnClickListener {
            Log.d(TAG, "Continue button натиснат")
            startMainActivity()
        }
        
        // ENABLE KIOSK BUTTON
        // Активирай киоск режим (Lock Task)
        enableKioskButton.setOnClickListener {
            Log.d(TAG, "Enable kiosk button натиснат")
            enableKioskMode()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // СТАРТИРАНЕ НА ГЛАВНАТА АКТИВНОСТ
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Стартирай MainActivity и затвори тази активност
     * 
     * Intent flags:
     * - FLAG_ACTIVITY_CLEAR_TOP: Затвори всички активности над новата
     * - FLAG_ACTIVITY_NEW_TASK: Стартирай в нов task (за сигурност)
     */
    private fun startMainActivity() {
        Log.d(TAG, "Стартирам MainActivity...")
        
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        
        startActivity(intent)
        
        // Затвори тази активност
        finish()
    }

    // ════════════════════════════════════════════════════════════════
    // АКТИВИРАНЕ НА КИОСК РЕЖИМ
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Активирай киоск режим (Lock Task Mode)
     * 
     * Lock Task режим означава:
     * - Потребител НЕ може да напусне приложението
     * - Системни бутони са скрити
     * - Само нашето приложение работи
     * 
     * ВАЖНО: Това работи САМО ако сме Device Owner!
     */
    private fun enableKioskMode() {
        
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "Активиране на киоск режим...")
        Log.d(TAG, "════════════════════════════════════════")
        
        try {
            // СТЪПКА 1: Проверка че сме Device Owner
            if (!dpm.isDeviceOwnerApp(packageName)) {
                Log.e(TAG, "❌ Не е Device Owner - киоск режим не е възможен")
                statusText.text = "❌ Грешка: Не е Device Owner"
                return
            }
            
            Log.d(TAG, "✅ Device Owner потвърден - продължавам с киоск")
            
            // СТЪПКА 2: Постави lock task пакети
            // Казваме на системата че само нашето приложение могат да работи
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            Log.d(TAG, "✅ Lock task пакети зададени: $packageName")
            
            // СТЪПКА 3: Стартирай lock task
            // Веднага после активност влиза в киоск режим
            startLockTask()
            Log.d(TAG, "✅ Lock task стартирана")
            
            // СТЪПКА 4: Актуализирай UI
            statusText.text = "🔒 Киоск режим активиран!"
            statusText.setTextColor(Color.parseColor("#4299e1"))  // Син цвят
            
            // СТЪПКА 5: Стартирай главната активност
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            
            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "✅ Киоск режим успешно активиран!")
            Log.d(TAG, "════════════════════════════════════════")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Грешка при активиране на киоск: ${e.message}")
            statusText.text = "❌ Грешка: ${e.message}"
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ПРИЛАГАНЕ НА НАЧАЛНИ ПОЛИТИКИ
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Приложи начални Device Owner политики
     * 
     * Това е място където можеш да конфигурираш устройството
     * автоматично при първи път
     */
    private fun applyInitialPolicies() {
        
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "Прилагане на начални политики...")
        Log.d(TAG, "════════════════════════════════════════")
        
        try {
            // ПОЛИТИКА 1: Криптография
            // Изисквай криптография на устройството
            dpm.setStorageEncryption(adminComponent, true)
            Log.d(TAG, "✅ Криптография активирана")
            
            // ПОЛИТИКА 2: Парола политика
            // Задай минимална дължина на парола
            dpm.setPasswordMinimumLength(adminComponent, 6)
            Log.d(TAG, "✅ Парола политика зададена (мин. 6 символа)")
            
            // ПОЛИТИКА 3: Качество на пароля
            // Числа + букви
            dpm.setPasswordQuality(
                adminComponent, 
                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
            )
            Log.d(TAG, "✅ Качество на парола: букви + цифри")
            
            // ПОЛИТИКА 4: Деактивиране на камера
            // Забрани използване на камера
            dpm.setCameraDisabled(adminComponent, true)
            Log.d(TAG, "✅ Камера деактивирана")
            
            // ПОЛИТИКА 5: Екранна защита
            // Максимален timeout преди заключване
            dpm.setMaximumTimeToLock(adminComponent, 30000)  // 30 секунди
            Log.d(TAG, "✅ Timeout до заключване: 30 секунди")
            
            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "✅ Всички политики успешно приложени!")
            Log.d(TAG, "════════════════════════════════════════")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Грешка при прилагане на политики: ${e.message}")
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ОБРАБОТКА НА BACK BUTTON
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Обработка на back button
     * 
     * Ако е киоск режим, забрани излизане
     */
    override fun onBackPressed() {
        Log.d(TAG, "Back button натиснат")
        
        // Проверка дали е киоск режим
        if (isInLockTaskMode) {
            Log.d(TAG, "⛔ Lock task режим активен - back е забранен")
            // Не направи нищо - блокирай back
            return
        }
        
        // Ако не е киоск, позволи back по обичайния начин
        super.onBackPressed()
    }
}
