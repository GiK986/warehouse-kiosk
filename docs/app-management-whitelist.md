# Управление на Приложения (Whitelist/Blacklist)

## Обща Информация

След provisioning с `LEAVE_ALL_SYSTEM_APPS_ENABLED: True`, устройството има всички системни приложения инсталирани и включени.

За да контролираш кои приложения са достъпни за потребителя, използваш Device Owner API `setApplicationHidden()`.

---

## Device Owner API: setApplicationHidden()

```kotlin
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

class AppVisibilityManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
        as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

    /**
     * Скрива приложение (прави го недостъпно за потребителя)
     */
    fun hideApp(packageName: String): Boolean {
        return try {
            dpm.setApplicationHidden(adminComponent, packageName, true)
            true
        } catch (e: Exception) {
            Log.e("AppVisibility", "Failed to hide $packageName", e)
            false
        }
    }

    /**
     * Показва приложение (прави го достъпно за потребителя)
     */
    fun showApp(packageName: String): Boolean {
        return try {
            dpm.setApplicationHidden(adminComponent, packageName, false)
            true
        } catch (e: Exception) {
            Log.e("AppVisibility", "Failed to show $packageName", e)
            false
        }
    }

    /**
     * Проверява дали приложение е скрито
     */
    fun isAppHidden(packageName: String): Boolean {
        return dpm.isApplicationHidden(adminComponent, packageName)
    }
}
```

---

## Whitelist Подход (ПРЕПОРЪЧВАМ)

Скрий **всички** приложения освен тези в whitelist-а:

```kotlin
class KioskAppManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
        as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

    // Whitelist: само тези приложения са видими
    private val allowedApps = setOf(
        "com.warehouse.kiosk",           // Нашето приложение
        "com.android.settings",          // Settings (ако е нужно)
        "com.symbol.datawedge",          // Scanner (Zebra)
        "com.honeywell.scanconfiguration", // Scanner (Honeywell)
        // Добави други критични приложения тук
    )

    /**
     * Прилага whitelist: скрива всички приложения освен разрешените
     */
    suspend fun applyWhitelist() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val allPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        var hidden = 0
        var shown = 0

        allPackages.forEach { appInfo ->
            val packageName = appInfo.packageName

            // Пропускаме собственото си приложение
            if (packageName == context.packageName) {
                return@forEach
            }

            val shouldBeVisible = allowedApps.contains(packageName)

            try {
                val isCurrentlyHidden = dpm.isApplicationHidden(adminComponent, packageName)

                if (shouldBeVisible && isCurrentlyHidden) {
                    // Трябва да е видимо но е скрито → показваме го
                    dpm.setApplicationHidden(adminComponent, packageName, false)
                    shown++
                    Log.d("Whitelist", "Showed: $packageName")

                } else if (!shouldBeVisible && !isCurrentlyHidden) {
                    // Трябва да е скрито но е видимо → скриваме го
                    dpm.setApplicationHidden(adminComponent, packageName, true)
                    hidden++
                    Log.d("Whitelist", "Hidden: $packageName")
                }

            } catch (e: Exception) {
                Log.e("Whitelist", "Failed to process $packageName", e)
            }
        }

        Log.i("Whitelist", "Applied whitelist: $shown shown, $hidden hidden")
    }
}
```

---

## Blacklist Подход (Алтернативен)

Показва всички приложения **освен** тези в blacklist-а:

```kotlin
class KioskAppManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
        as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

    // Blacklist: само тези приложения са скрити
    private val blockedApps = setOf(
        "com.google.android.youtube",
        "com.android.chrome",
        "com.facebook.katana",
        "com.whatsapp",
        // Добави нежелани приложения тук
    )

    /**
     * Прилага blacklist: скрива само забранените приложения
     */
    suspend fun applyBlacklist() = withContext(Dispatchers.IO) {
        blockedApps.forEach { packageName ->
            try {
                dpm.setApplicationHidden(adminComponent, packageName, true)
                Log.d("Blacklist", "Hidden: $packageName")
            } catch (e: Exception) {
                // Приложението вероятно не е инсталирано
                Log.w("Blacklist", "Could not hide $packageName: ${e.message}")
            }
        }
    }
}
```

---

## Намиране на Package Names

За да намериш package name на приложение:

### Метод 1: ADB

```bash
# Всички приложения
adb shell pm list packages

# Търсене по ключова дума
adb shell pm list packages | grep scanner
adb shell pm list packages | grep zebra
adb shell pm list packages | grep honeywell

# Само системни приложения
adb shell pm list packages -s

# Само 3rd party приложения
adb shell pm list packages -3
```

### Метод 2: Програмно (в кода)

```kotlin
fun getAllInstalledPackages(): List<String> {
    val pm = context.packageManager
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

    return apps.map { it.packageName }.sorted()
}

fun printAllPackages() {
    getAllInstalledPackages().forEach { packageName ->
        Log.d("Packages", packageName)
    }
}
```

---

## Интеграция в MainActivity

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var appManager: KioskAppManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appManager = KioskAppManager(this)

        // Прилагаме whitelist при стартиране на приложението
        lifecycleScope.launch {
            appManager.applyWhitelist()
        }

        setContent {
            WarehouseKioskTheme {
                MainScreen()
            }
        }
    }
}
```

---

## Интеграция в ProvisioningCompleteActivity (Опционално)

Ако искаш да приложиш whitelist **веднага след provisioning**:

```kotlin
class ProvisioningCompleteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ... Device Owner проверка ...

        // Прилагаме whitelist преди да стартираме MainActivity
        lifecycleScope.launch {
            try {
                val appManager = KioskAppManager(this@ProvisioningCompleteActivity)
                appManager.applyWhitelist()
                Log.i(TAG, "Whitelist applied successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply whitelist", e)
            }

            // След това стартираме MainActivity
            finishAndStartMain()
        }
    }
}
```

---

## Важни Бележки

### 1. Системни vs 3rd Party Приложения

- **Системни** приложения: `setApplicationHidden()` работи перфектно
- **3rd party** приложения: Може да се скрият, но Device Owner може и да ги **uninstall** с `packageManager.deletePackage()`

### 2. Собственото Приложение

НИКОГА не скривай `com.warehouse.kiosk` - ще загубиш контрол!

```kotlin
if (packageName == context.packageName) {
    return // Пропускаме собственото приложение!
}
```

### 3. Критични Системни Приложения

Внимавай със скриването на:
- `com.android.systemui` - System UI
- `com.android.launcher` - Launcher (ако не използваш kiosk mode)
- `com.android.settings` - Settings (може да ти трябва за debugging)

### 4. Scanner Приложения (Често Срещани)

| Производител | Package Name |
|--------------|--------------|
| Zebra | `com.symbol.datawedge` |
| Honeywell | `com.honeywell.scanconfiguration` |
| Datalogic | `com.datalogic.softspot` |
| UROVO | `com.android.server.scannerservice` |

---

## Пример: Динамичен Whitelist от SharedPreferences

Ако искаш да управляваш whitelist-а през UI настройки:

```kotlin
class WhitelistRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE)
    private val WHITELIST_KEY = "app_whitelist"

    fun getWhitelist(): Set<String> {
        val json = prefs.getString(WHITELIST_KEY, "[]") ?: "[]"
        val type = object : TypeToken<Set<String>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun saveWhitelist(whitelist: Set<String>) {
        val json = Gson().toJson(whitelist)
        prefs.edit().putString(WHITELIST_KEY, json).apply()
    }

    fun addToWhitelist(packageName: String) {
        val current = getWhitelist().toMutableSet()
        current.add(packageName)
        saveWhitelist(current)
    }

    fun removeFromWhitelist(packageName: String) {
        val current = getWhitelist().toMutableSet()
        current.remove(packageName)
        saveWhitelist(current)
    }
}
```

---

## Тестване

След прилагане на whitelist:

```bash
# Виж скритите приложения
adb shell pm list packages -d

# Виж видимите приложения
adb shell pm list packages -e

# Провери конкретно приложение
adb shell pm list packages | grep <package_name>
```

---

## Резюме

✅ **Whitelist подход (препоръчвам)**: Скрий всички освен разрешените
✅ **Blacklist подход**: Покажи всички освен забранените
✅ Използвай `setApplicationHidden()` за контрол на видимостта
✅ Намери package names с ADB или програмно
✅ Интегрирай в MainActivity за динамично управление

---

**За повече информация**: [Device Owner API Documentation](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode)
