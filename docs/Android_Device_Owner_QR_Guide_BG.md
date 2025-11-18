# Пълно ръководство: Device Owner режим + QR Code инсталация на Android

## Съдържание
1. [Преглед и изисквания](#преглед-и-изисквания)
2. [Архитектура и компоненти](#архитектура-и-компоненти)
3. [Подготовка на DPC приложението](#подготовка-на-dpc-приложението)
4. [Калкулиране на APK контролна сума](#калкулиране-на-apk-контролна-сума)
5. [Генериране на QR код](#генериране-на-qr-код)
6. [Инсталация и provisioning](#инсталация-и-provisioning)
7. [Тестване и отстраняване на грешки](#тестване-и-отстраняване-на-грешки)
8. [Практически примери](#практически-примери)

---

## Преглед и изисквания

### Какво е Device Owner режим?
Device Owner (DO) е Android Enterprise функция, която позволява пълна контрола над устройството. За разлика от Profile Owner, който управлява само работния профил, Device Owner управлява цялото устройство включително системните настройки, инсталираните приложения и устройственото оборудване (камера, микрофон и т.н.).

### Когато можеш да използваш Device Owner?
- **Киоск режим** - устройство с единствена функция (POS терминал, информационен дисплей)
- **Управлявани устройства** - компании, които разпределят устройства на служители
- **Складски терминали** - индустриални устройства с ограничени функции
- **Специализирани приложения** - медицински, финансови или лични системи

### Системни изисквания

| Параметър | Изисквание |
|-----------|-----------|
| **Android версия** | 5.0 (API 21) и по-нова |
| **Статус на устройството** | Ново или factory reset |
| **Google Play услуги** | Инсталирани |
| **Интернет връзка** | Wyfi или мобилна мрежа |
| **Потребителски акаунти** | Без активни Google акаунти |
| **Криптография** | За някои функции е препоръчана |

### Ключови ограничения
- Device Owner може да се постави **само** на неинициализирано (factory reset) устройство
- Не можеш имать повече от един Device Owner на устройство
- Ако `Settings.Secure.USER_SETUP_COMPLETE` е вече постаен, устройството е считано за инициализирано
- За повторен provisioning се изисква factory reset

---

## Архитектура и компоненти

### Структура на системата

```
┌─────────────────────────────────────────────────────────────┐
│                    УПРАВЛЯВАНА СИСТЕМА                      │
├─────────────────────────────────────────────────────────────┤
│  DPC Приложение (твоята app)                                │
│  ├─ DeviceAdminReceiver (получател на администраторски     │
│  │  събития)                                                │
│  ├─ Services (фонови услуги)                                │
│  └─ Activities (потребителски интерфейс)                    │
├─────────────────────────────────────────────────────────────┤
│  DevicePolicyManager (система за управление на политики)    │
├─────────────────────────────────────────────────────────────┤
│  Installer приложения (Play Store или custom)               │
└─────────────────────────────────────────────────────────────┘
```

### Процес на provisioning

```
1. Factory Reset Device
   ↓
2. Включи устройството и тапни Welcome Screen 6 пъти
   ↓
3. Включи Wyfi и сканирай QR код
   ↓
4. Setup Wizard изтегля QR код четец (ако е нужно)
   ↓
5. QR код е сканиран и прочитан
   ↓
6. Устройството изтегля DPC приложението от посочения URL
   ↓
7. Верифицира се контролната сума на APK файла
   ↓
8. DPC приложението се инсталира и стартира
   ↓
9. DeviceAdminReceiver получава ACTION_PROFILE_PROVISIONING_COMPLETE
   ↓
10. DPC приложението прилага политики и конфигурации
   ↓
11. Устройството е в Device Owner режим и управляемо
```

---

## Подготовка на DPC приложението

### 1. Структура на AndroidManifest.xml

Твоя DPC приложение трябва да дефинира `DeviceAdminReceiver`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.mydeviceowner"
    android:versionCode="1"
    android:versionName="1.0">

    <!-- ЗАДЪЛЖИТЕЛНИ PERMISSIONS -->
    <uses-permission android:name="android.permission.DOWNLOAD_WITHOUT_NOTIFICATION" />
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />
    <uses-permission android:name="android.permission.MANAGE_ACCOUNTS" />
    <uses-permission android:name="android.permission.WRITE_SYNC_SETTINGS" />
    <uses-permission android:name="com.google.android.providers.gsf.permission.READ_GSERVICES" />
    
    <!-- ДОПЪЛНИТЕЛНИ PERMISSIONS (за Device Owner функции) -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.CHANGE_CONFIGURATION" />

    <application
        android:label="@string/app_name">

        <!-- ГЛАВНИЯ RECEIVER - DeviceAdminReceiver -->
        <receiver
            android:name=".DeviceOwnerReceiver"
            android:description="@string/app_name"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_DEVICE_ADMIN"
            android:exported="true">
            
            <!-- Metadata за политиките -->
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_owner_receiver" />
            
            <!-- Необходими intent filteri -->
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
            
            <!-- За Device Owner -->
            <intent-filter>
                <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
            </intent-filter>
            
            <!-- За Admin Policy Compliance (препоръчано) -->
            <intent-filter>
                <action android:name="android.app.action.ADMIN_POLICY_COMPLIANCE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </receiver>

        <!-- ГЛАВНА АКТИВНОСТ -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- PROVISIONING COMPLETE ACTIVITY (препоръчано) -->
        <activity
            android:name=".ProvisioningCompleteActivity"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <intent-filter>
                <action android:name="android.app.action.ADMIN_POLICY_COMPLIANCE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- SERVICE ЗА DEVICE OWNER (за Android 8.0+) -->
        <service
            android:name=".DeviceOwnerService"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_SERVICE" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

### 2. DeviceOwnerReceiver реализация

```kotlin
package com.example.mydeviceowner

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

class DeviceOwnerReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceOwnerReceiver"
    }

    /**
     * Извиква се когато Device Owner provisioning е завършено
     */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.d(TAG, "Provisioning complete!")
        super.onProfileProvisioningComplete(context, intent)
        
        // Стартирай главната активност
        val mainIntent = Intent(context, MainActivity::class.java)
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(mainIntent)
    }

    /**
     * Извиква се когато устройството е заключено
     */
    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Log.d(TAG, "Lock task mode entering for package: $pkg")
        super.onLockTaskModeEntering(context, intent, pkg)
    }

    /**
     * Извиква се когато устройството е отключено
     */
    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Log.d(TAG, "Lock task mode exiting")
        super.onLockTaskModeExiting(context, intent)
    }

    /**
     * Извиква се преди admin да бъде деактивиран
     */
    override fun onDisabled(context: Context, intent: Intent) {
        Log.d(TAG, "Device admin disabled")
        super.onDisabled(context, intent)
    }

    /**
     * Извиква се когато има промяна в admin политиката
     */
    override fun onSystemUpdatePending(context: Context, intent: Intent, updateReceivedTime: Long) {
        Log.d(TAG, "System update pending")
        super.onSystemUpdatePending(context, intent, updateReceivedTime)
    }
}
```

### 3. Файл с политики (res/xml/device_owner_receiver.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- ОСНОВНИ ПОЛИТИКИ -->
    <limit-password
        android:quality="numeric"
        android:length="4" />
    
    <!-- КАМЕРА И МИКРОФОН -->
    <disable-camera />
    <disable-keyguard-features android:value="disable_fingerprint|disable_remote_keyguard" />
    
    <!-- УПРАВЛЕНИЕ НА ПРИЛОЖЕНИЯТА -->
    <enable-system-app-update />
    
    <!-- КРИПТОГРАФИЯ -->
    <encrypted-storage />
    
    <!-- BLUETOOTH УПРАВЛЕНИЕ -->
    <disable-bluetooth-contact-sharing />
    
</device-admin>
```

### 4. MainActivity реализация

```kotlin
package com.example.mydeviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mydeviceowner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var devicePolicyManager: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Получи DevicePolicyManager
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        
        // Провери дали си Device Owner
        val componentName = ComponentName(this, DeviceOwnerReceiver::class.java)
        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)
        
        binding.statusText.text = if (isDeviceOwner) {
            "✓ Приложението е Device Owner"
        } else {
            "✗ Приложението НЕ е Device Owner"
        }
        
        // Применяне на Lock Task режим (киоск режим)
        if (isDeviceOwner) {
            binding.enableKioskButton.setOnClickListener {
                enableKioskMode()
            }
        } else {
            binding.enableKioskButton.isEnabled = false
        }
    }

    private fun enableKioskMode() {
        val componentName = ComponentName(this, DeviceOwnerReceiver::class.java)
        val taskLockPackages = arrayOf(packageName)
        
        try {
            devicePolicyManager.setLockTaskPackages(componentName, taskLockPackages)
            startLockTask()
            binding.statusText.text = "Киоск режим активиран ✓"
        } catch (e: Exception) {
            binding.statusText.text = "Грешка при активиране на киоск режим: ${e.message}"
        }
    }
}
```

---

## Калкулиране на APK контролна сума

### Защо е нужна контролна сума?
Контролната сума гарантира че устройството инсталира точно твоят подписан APK файл без каквото и да е пакостване. Android тарифирует контролната сума при provisioning.

### Два типа контролни суми

| Тип | Параметър | Кога се използва | Преди |
|-----|-----------|-----------------|-------|
| **Package Checksum** | `PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM` | За конкретна версия/build на приложението | Android 5.0 (API 21) |
| **Signature Checksum** | `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` | За всички версии подписани със същия сертификат | Android 6.0 (API 23) |

**Препоръка:** Използвай Signature Checksum - така всяка нова версия на приложението ще работи без регенериране на QR код.

### Метод 1: Калкулиране с apksigner (ПРЕПОРЪЧАН)

`apksigner` е най-надеждният начин и поддържа както v1, така и v2 схеми на подпис.

```bash
# Където е apksigner?
# Обикновено: <Android SDK>/build-tools/<version>/apksigner

# Linux/Mac:
apksigner verify --print-certs /path/to/your/app.apk | \
  grep "Signer #1 certificate SHA-256" | \
  sed 's/.*SHA-256 digest: //' | \
  xxd -r -p | \
  openssl base64 | \
  tr -- '+/' '-_' | \
  tr -d '='

# Windows (PowerShell):
$output = & apksigner verify --print-certs "C:\path\to\app.apk" | `
  Select-String "Signer #1 certificate SHA-256"
$hash = $output -split "SHA-256 digest: " | Select-Object -Last 1
[Convert]::ToBase64String(($hash -split ':' | % {[Convert]::ToInt32($_,16)})) `
  -replace '\+','-' -replace '/','_'
```

**Очаквана продукция:**
```
I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg
```

### Метод 2: Калкулиране с keytool (ако apksigner не е наличен)

```bash
# Само за APK с v1 схема
keytool -list -printcert -jarfile your_app.apk | \
  grep "SHA256:" | \
  sed 's/.*SHA256: //' | \
  xxd -r -p | \
  openssl base64 | \
  tr -- '+/' '-_' | \
  tr -d '='
```

### Метод 3: Калкулиране на Package Checksum (SHA256 на цял файл)

```bash
# Linux/Mac:
sha256sum your_app.apk | \
  cut -d' ' -f1 | \
  xxd -r -p | \
  openssl base64 | \
  tr -- '+/' '-_' | \
  tr -d '='

# Windows (PowerShell):
$file = "your_app.apk"
$hash = (Get-FileHash -Path $file -Algorithm SHA256).Hash
[Convert]::ToBase64String(($hash -split '..' | `
  Where-Object {$_} | ForEach-Object {[Convert]::ToInt32($_,16)})) `
  -replace '\+','-' -replace '/','_'
```

### Хранилище на APK файла

APK файлът трябва да бъде публично достъпен от интернет. Възможности:

1. **Google Drive** - публичен файл
2. **AWS S3** - публичен bucket
3. **Собствен сървър** - HTTPS адрес
4. **GitHub Releases** - автоматично HTTPS URL

**ВАЖНО:** URL трябва да завършва с `.apk` и трябва да позволява директно скачване!

Пример URL:
```
https://example.com/downloads/my-dpc-app.apk
https://storage.googleapis.com/my-bucket/app.apk
https://github.com/myrepo/releases/download/v1.0/app.apk
```

---

## Генериране на QR код

### Структура на provisioning JSON

Това е JSON обект, който съдържа всички информации за provisioning:

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.example.mydeviceowner/.DeviceOwnerReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://example.com/downloads/my-dpc-app.apk",
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": false,
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": false,
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
    "custom_param_1": "value1",
    "custom_param_2": "value2"
  },
  "android.app.extra.PROVISIONING_WIFI_SSID": "MyWiFi",
  "android.app.extra.PROVISIONING_WIFI_PASSWORD": "MyPassword123",
  "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA"
}
```

### Таблица с параметри

| Параметър | Тип | Задължителен | Описание |
|-----------|-----|--------------|---------|
| `PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME` | String | ✓ | Пълното име на DeviceAdminReceiver в формат `package/.Class` |
| `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` | String | ✓ | SHA-256 контролна сума на подписа (base64, URL-safe) |
| `PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION` | String | ✓ | HTTPS URL към APK файла |
| `PROVISIONING_SKIP_ENCRYPTION` | Boolean | ✗ | Skip криптография (не препоръчвам) |
| `PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED` | Boolean | ✗ | Остави всички system apps активни |
| `PROVISIONING_ADMIN_EXTRAS_BUNDLE` | Object | ✗ | Допълнителни параметри за твоята app |
| `PROVISIONING_WIFI_SSID` | String | ✗ | Wyfi име (облегчава setup) |
| `PROVISIONING_WIFI_PASSWORD` | String | ✗ | Wyfi парола |
| `PROVISIONING_WIFI_SECURITY_TYPE` | String | ✗ | WPA/WEP/OPEN |
| `PROVISIONING_DEVICE_ADMIN_TYPE` | String | ✗ | "DEVICE_OWNER" или "PROFILE_OWNER" |

### Генериране на QR код - онлайн метод

1. Отвори https://www.qr-code-generator.com/
2. Избери "Text" режим
3. Постави целия JSON (без переводи на ред):
```
{"android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":"com.example.mydeviceowner/.DeviceOwnerReceiver","android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM":"I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg","android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":"https://example.com/downloads/my-dpc-app.apk"}
```
4. Генерирай и свали QR код като PNG/PDF

### Генериране на QR код - Python скрипт

```python
#!/usr/bin/env python3
import json
import qrcode
import sys

def generate_device_owner_qr(
    package_name,
    receiver_class,
    signature_checksum,
    apk_url,
    output_file="qr_code.png",
    skip_encryption=False,
    leave_all_apps=False,
    wifi_ssid=None,
    wifi_password=None
):
    """
    Генерира QR код за Device Owner provisioning
    """
    
    # Изграждане на JSON структурата
    provisioning_data = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": 
            f"{package_name}/{receiver_class}",
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": 
            signature_checksum,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": 
            apk_url,
        "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": 
            skip_encryption,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": 
            leave_all_apps,
    }
    
    # Добави Wyfi ако е предоставена
    if wifi_ssid:
        provisioning_data["android.app.extra.PROVISIONING_WIFI_SSID"] = wifi_ssid
        if wifi_password:
            provisioning_data["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = wifi_password
    
    # Конвертирай в JSON (без преводи)
    json_str = json.dumps(provisioning_data, separators=(',', ':'))
    
    print(f"JSON за QR: {json_str}")
    print(f"Дължина: {len(json_str)} символа")
    
    # Генерирай QR код
    qr = qrcode.QRCode(
        version=None,  # Auto-detect size
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=10,
        border=4,
    )
    qr.add_data(json_str)
    qr.make(fit=True)
    
    # Събеседу QR код
    img = qr.make_image(fill_color="black", back_color="white")
    img.save(output_file)
    print(f"QR код сохранен в: {output_file}")
    
    return output_file

# Пример за използване:
if __name__ == "__main__":
    generate_device_owner_qr(
        package_name="com.example.mydeviceowner",
        receiver_class=".DeviceOwnerReceiver",
        signature_checksum="I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg",
        apk_url="https://example.com/downloads/my-dpc-app.apk",
        output_file="device_owner_qr.png",
        skip_encryption=False,
        leave_all_apps=False,
        wifi_ssid="MyWiFi",
        wifi_password="MyPassword123"
    )
```

**Инсталирай qrcode библиотека:**
```bash
pip install qrcode[pil]
```

### Генериране на QR код - PowerShell (Windows)

```powershell
# Инсталирай необходимите модули
Install-Module -Name QRCodeGenerator -Force

$provisioningJson = @{
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME" = "com.example.mydeviceowner/.DeviceOwnerReceiver"
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM" = "I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg"
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION" = "https://example.com/downloads/my-dpc-app.apk"
    "android.app.extra.PROVISIONING_SKIP_ENCRYPTION" = $false
    "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED" = $false
} | ConvertTo-Json -Compress

# Генерирай QR код
New-QRCode -InputObject $provisioningJson -OutPath "device_owner_qr.png"
```

---

## Инсталация и provisioning

### Стъпка 1: Подготовка на устройството

1. **Factory Reset устройството**
   - Настройки → Система → Отнулирай → Отнулирай устройството
   - Всички данни ще бъдат изтрити

2. **Включи устройството след reset**
   - Очаквай начален екран на Welcome

### Стъпка 2: Активация на QR код режим

На Welcome екран (преди да влезеш в система):
1. **Тапни 6 пъти на Welcome текста**
   - Първи тап
   - Втори тап
   - Трети тап
   - Четвърти тап
   - Пети тап
   - **Шести тап** - активира QR скенер

2. На Android 7.0 и по-нови:
   - Вътрешния QR скенер ще бъде активиран
   - Или ще те изиска да влезеш в Wyfi първо

3. На Android 6.0 и по-стари:
   - Трябва да влезеш в Wyfi за да изтегли QR скенер приложение

### Стъпка 3: Wyfi подключване

1. Избери своята Wyfi мрежа
2. Въведи пароля
3. Чакай да се подключи

### Стъпка 4: Сканиране на QR код

1. Появи QR кода на екран на компютъра или отпечатай на хартия
2. **Насочи камерата към QR код**
3. Очаквай да бъде разпознат
4. Потвърди "Продължи" ако е попитан

### Стъпка 5: Изтегляне и инсталиране на DPC приложението

1. Системата ще прочете provisioning данните от QR
2. Ще изтегли твоя APK файл от дадения URL
3. Ще верифицира контролната сума
4. Ще инсталира APK файла
5. Ще дава админ привилегии на приложението

### Стъпка 6: Завършване на provisioning

1. DeviceOwnerReceiver получава `ACTION_PROFILE_PROVISIONING_COMPLETE`
2. DPC приложението стартира
3. Прилагат се политиките
4. Устройството е в Device Owner режим

---

## Тестване и отстраняване на грешки

### Проверка на Device Owner статус чрез ADB

```bash
# Дали е Device Owner?
adb shell cmd device_policy get-device-owner

# Всички активни admins
adb shell dpm list-admins

# Детаилна информация за Device Owner
adb shell dumpsys device_policy
```

### Логви от provisioning процеса

```bash
# Прочети логове на provisioning
adb logcat | grep -i provisioning

# Прочети всички логове
adb logcat | grep -i device

# Запиши логовете в файл
adb logcat > provisioning_logs.txt

# След като приключиш натисни Ctrl+C
```

### Частите грешки и решения

#### Грешка 1: "Контролната сума на APK е невалидна"

```
Error: Package checksum mismatch
```

**Причини:**
- Контролната сума е изчислена неправилно
- APK файлът е променен след генериране на QR
- Използваш package checksum вместо signature checksum

**Решение:**
1. Преизчисли контролната сума
2. Генерирай нов QR код
3. Провери че URL е достъпен и връща същия APK

#### Грешка 2: "APK файлът не може да бъде изтегнен"

```
Error: Unable to download package from URL
```

**Причини:**
- URL е неправилен
- Интернет няма връзка
- HTTP вместо HTTPS
- Файлът не съществува на този адрес

**Решение:**
1. Провери URL от компютър - трябва да работи
2. Убеди се че е HTTPS
3. Провери че файлът може да бъде директно скачан

#### Грешка 3: "QR код не може да бъде разпознат"

```
Cannot scan QR code
```

**Причини:**
- QR кодът е твърде малък
- Лоша осветленост
- JSON има неправилна форматировка

**Решение:**
1. Увеличи размера на QR кода
2. Подобри осветлеността
3. Провери JSON за грешки - никакви преводи, само един ред

#### Грешка 4: "DeviceAdminReceiver не е намерен"

```
Error: Component not found
```

**Причини:**
- Погрешно име на receiver в JSON
- Receiver не е дефиниран в AndroidManifest.xml
- Неправилна пакетна структура

**Решение:**
1. Провери пакетното име и class navn в JSON
2. Убеди се че receiver е деклариран с `android:exported="true"`
3. Преправи APK ако е нужно

#### Грешка 5: "Device Owner не може да бъде постаен - устройството е вече инициализирано"

```
Error: Device is already provisioned, cannot set device owner
```

**Причини:**
- `Settings.Secure.USER_SETUP_COMPLETE` е вече постаен
- Преди това е опит за provisioning
- Не е factory reset

**Решение:**
1. Направи factory reset отново
2. Веднага след стартиране - тапни 6 пъти и сканирай QR

### Отстраняване на грешки с ADB команди

```bash
# Направи factory reset
adb shell wm dismiss-keyguard
adb shell settings delete global setup_wizard_has_run
adb shell settings delete global device_provisioned
adb reboot recovery

# След това в recovery меню избери "Wipe data/factory reset"

# Провери дали provisioning е в прогрес
adb shell dumpsys device_policy | grep -i provisioning

# Виждай текущите policies
adb shell dumpsys device_policy | grep -i policy

# Прочети Wi-Fi конфигурация
adb shell dumpsys wifi

# Виждай изтеглени APK файлове
adb shell pm list packages | grep device
```

---

## Практически примери

### Пример 1: Просто Device Owner приложение (Kotlin)

**Файлова структура:**
```
MyDeviceOwner/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/mydeviceowner/
│   │   │   ├── DeviceOwnerReceiver.kt
│   │   │   └── MainActivity.kt
│   │   └── res/
│   │       ├── xml/device_owner_receiver.xml
│   │       └── layout/activity_main.xml
│   └── build.gradle
└── README.md
```

**AndroidManifest.xml (целия файл):**
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.mydeviceowner"
    android:versionCode="1"
    android:versionName="1.0">

    <uses-permission android:name="android.permission.DOWNLOAD_WITHOUT_NOTIFICATION" />
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />
    <uses-permission android:name="android.permission.MANAGE_ACCOUNTS" />
    <uses-permission android:name="android.permission.WRITE_SYNC_SETTINGS" />
    <uses-permission android:name="com.google.android.providers.gsf.permission.READ_GSERVICES" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:label="MyDeviceOwner"
        android:theme="@style/AppTheme">

        <receiver
            android:name=".DeviceOwnerReceiver"
            android:description="@string/app_name"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_DEVICE_ADMIN"
            android:exported="true">
            
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_owner_receiver" />
            
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
            
            <intent-filter>
                <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
            </intent-filter>
        </receiver>

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

**DeviceOwnerReceiver.kt:**
```kotlin
package com.example.mydeviceowner

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceOwnerReceiver : DeviceAdminReceiver() {
    
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.d("DO", "Provisioning завършено!")
        
        val mainIntent = Intent(context, MainActivity::class.java)
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(mainIntent)
    }
}
```

**MainActivity.kt:**
```kotlin
package com.example.mydeviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isOwner = dpm.isDeviceOwnerApp(packageName)
        
        val textView: TextView = findViewById(R.id.status_text)
        textView.text = if (isOwner) {
            "✓ Device Owner е активен!"
        } else {
            "✗ Няма Device Owner привилегии"
        }
    }
}
```

### Пример 2: Киоск режим със Lock Task

```kotlin
package com.example.mydeviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceOwnerReceiver::class.java)
        
        findViewById<Button>(R.id.enable_kiosk_btn).setOnClickListener {
            enableKioskMode()
        }
        
        findViewById<Button>(R.id.exit_kiosk_btn).setOnClickListener {
            exitKioskMode()
        }
    }
    
    private fun enableKioskMode() {
        if (!dpm.isDeviceOwnerApp(packageName)) {
            Toast.makeText(this, "Няма Device Owner привилегии", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Постави lock task режим
        dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
        startLockTask()
        
        Toast.makeText(this, "Киоск режим активиран", Toast.LENGTH_SHORT).show()
    }
    
    private fun exitKioskMode() {
        stopLockTask()
        dpm.setLockTaskPackages(adminComponent, arrayOf())
        Toast.makeText(this, "Киоск режим деактивиран", Toast.LENGTH_SHORT).show()
    }
}
```

### Пример 3: Команди за изтегляне на приложението

```bash
# Генерирай подписана APK
./gradlew bundleRelease
# или за APK:
./gradlew assembleRelease

# APK ще бъде в: app/build/outputs/apk/release/app-release.apk

# Изчисли контролната сума
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | \
  grep "Signer #1 certificate SHA-256" | \
  sed 's/.*SHA-256 digest: //' | \
  xxd -r -p | openssl base64 | tr -- '+/' '-_' | tr -d '='

# Можеш да виждаш резултата - например:
# I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg

# Качи APK на облак (например Google Drive)
# Направи публичен линк

# Генерирай QR код с параметрите
python3 generate_qr.py \
  --package com.example.mydeviceowner \
  --receiver .DeviceOwnerReceiver \
  --checksum I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg \
  --url https://drive.google.com/uc?export=download&id=YOUR_FILE_ID
```

---

## Резюме на процеса

```
1. РАЗРАБОТКА
   └─ Създай DPC приложение с DeviceAdminReceiver
   
2. ПОДПИСВАНЕ
   └─ Подпиши APK с Gradle/Android Studio
   
3. КАЛКУЛИРАНЕ КОНТРОЛНА СУМА
   └─ Калкулирай SHA-256 signature checksum
   
4. КАЧВАНЕ НА ОБЛАК
   └─ Качи APK на HTTPS адрес
   
5. ГЕНЕРИРАНЕ QR КОД
   └─ Генерирай JSON provisioning payload
   └─ Преобразуй към QR код
   
6. ПОДГОТОВКА НА УСТРОЙСТВО
   └─ Factory reset
   └─ Включи и тапни 6 пъти на Welcome
   
7. PROVISIONING
   └─ Свържи Wyfi
   └─ Сканирай QR код
   
8. ЗАВЪРШВАНЕ
   └─ Системата инсталира DPC
   └─ Приложението е Device Owner
   └─ Можеш да прилагаш политики
```

---

## Допълнителни ресурси

- **Google Android Enterprise**: https://developer.android.com/work
- **Device Policy Controller**: https://developer.android.com/work/dpc/build-dpc
- **Android Management API**: https://developers.google.com/android/management
- **TestDPC (пример)**: https://github.com/googlesamples/android-testdpc
- **Device Admin Guide**: https://developer.android.com/work/device-admin

---

**Версия:** 1.0  
**Последна актуализация:** November 2025  
**За:** GiK986 - Android Developer  
