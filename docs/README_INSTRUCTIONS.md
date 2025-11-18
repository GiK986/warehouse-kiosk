# Provisioning Complete Activity - Инструкции за интеграция

## 📋 Съдържание на Template-а

1. **ProvisioningCompleteActivity.kt** - Главният activity
2. **DeviceAdminReceiver.kt** - Device Admin receiver
3. **device_admin.xml** - XML политика за device admin
4. **AndroidManifest_complete.xml** - Пълен snippet за manifest
5. **provisioning_qr_template.json** - Template за QR provisioning
6. **build_gradle_dependencies.txt** - Нужни dependencies

---

## 🚀 Стъпка 1: Копиране на файловете

### 1.1 Копирайте Kotlin класовете

```
src/main/java/com/autoplus/kiosklauncher/
├── ProvisioningCompleteActivity.kt
├── DeviceAdminReceiver.kt
└── MainActivity.kt (вашият съществуващ)
```

**ВАЖНО:** Заменете `com.autoplus.kiosklauncher` с вашия реален package name!

### 1.2 Копирайте XML ресурсите

```
src/main/res/xml/
└── device_admin.xml
```

Ако папката `res/xml/` не съществува, създайте я.

---

## 🔧 Стъпка 2: Конфигуриране на ProvisioningCompleteActivity.kt

Отворете **ProvisioningCompleteActivity.kt** и променете следните константи:

### 2.1 Основна конфигурация

```kotlin
// TODO 1: Вашият package name
package com.autoplus.kiosklauncher // ← ПРОМЕНЕТЕ

// TODO 2: Device Admin Receiver клас
private const val ADMIN_RECEIVER_CLASS = "com.autoplus.kiosklauncher.DeviceAdminReceiver"

// TODO 3: Главна Activity
private const val MAIN_LAUNCHER_CLASS = "com.autoplus.kiosklauncher.MainActivity"
```

### 2.2 Клавиатура

```kotlin
// TODO 4: Изберете клавиатура (uncomment една линия)
private const val DEFAULT_KEYBOARD = "com.google.android.inputmethod.latin/.LatinIME" // Gboard
// private const val DEFAULT_KEYBOARD = "com.android.inputmethod.latin/.LatinIME" // AOSP
```

### 2.3 Допълнителни приложения (опционално)

```kotlin
// TODO 5: Добавете APK-та за инсталиране
private val ADDITIONAL_APPS = listOf(
    AppToInstall(
        name = "Barcode Scanner",
        url = "https://your-server.com/apps/scanner.apk", // ← ПРОМЕНЕТЕ
        packageName = "com.example.scanner"
    ),
    // Добавете още...
)
```

Ако не искате да инсталирате допълнителни приложения:
```kotlin
private val ADDITIONAL_APPS = emptyList<AppToInstall>()
```

### 2.4 Kiosk Mode настройки

```kotlin
// TODO 6: Kiosk конфигурация
private const val ENABLE_FULL_KIOSK = true // true = пълен lock, false = multi-app

private val ALLOWED_APPS = listOf(
    "com.autoplus.kiosklauncher", // ← ПРОМЕНЕТЕ с вашия package
    // "com.android.settings", // Uncomment за достъп до Settings
)
```

### 2.5 Екран настройки

```kotlin
// TODO 7: Екран
private const val SCREEN_TIMEOUT_MS = 600000 // 10 мин (0 = never timeout)
private const val SCREEN_BRIGHTNESS = 200 // 0-255, или -1 за auto
private const val STAY_AWAKE_WHILE_CHARGING = true
```

### 2.6 Звук настройки

```kotlin
// TODO 8: Звук
private const val SOUND_ENABLED = false // Touch sounds
private const val NOTIFICATION_SOUND_ENABLED = false
```

---

## 📱 Стъпка 3: Конфигуриране на AndroidManifest.xml

Отворете **AndroidManifest.xml** и добавете:

### 3.1 Permissions (извън <application> тага)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
<uses-permission android:name="android.permission.CHANGE_CONFIGURATION" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.MANAGE_DEVICE_ADMINS" />
```

### 3.2 Activities и Receiver (вътре в <application> тага)

```xml
<!-- ProvisioningCompleteActivity -->
<activity
    android:name=".ProvisioningCompleteActivity"
    android:exported="true"
    android:launchMode="singleTop">
    <intent-filter>
        <action android:name="android.app.action.PROVISIONING_SUCCESSFUL" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>

<!-- DeviceAdminReceiver -->
<receiver
    android:name=".DeviceAdminReceiver"
    android:exported="true"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
        <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
    </intent-filter>
</receiver>
```

---

## 📦 Стъпка 4: build.gradle.kts (Module: app)

Добавете dependencies за Compose:

```kotlin
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

android {
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
```

**След това:** Sync Gradle

---

## 🔐 Стъпка 5: Генериране на QR код за provisioning

### 5.1 Редактирайте provisioning_qr_template.json

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.autoplus.kiosklauncher/.DeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://your-server.com/kiosk.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "SHA256_HASH",
  "android.app.extra.PROVISIONING_WIFI_SSID": "AutoPlus_Warehouse",
  "android.app.extra.PROVISIONING_WIFI_PASSWORD": "your_password",
  "android.app.extra.PROVISIONING_LOCALE": "bg_BG",
  "android.app.extra.PROVISIONING_TIME_ZONE": "Europe/Sofia",
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
    "warehouse_id": "WH_SOFIA_01",
    "server_url": "https://api.autoplus.bg"
  }
}
```

### 5.2 Генерирайте SHA-256 hash на APK

**Windows PowerShell:**
```powershell
Get-FileHash -Path "app-release.apk" -Algorithm SHA256
```

**Linux/Mac:**
```bash
shasum -a 256 app-release.apk
```

Копирайте hash-а и го поставете в JSON-а.

### 5.3 Генерирайте QR код

**Вариант 1: Online генератор**
- Отидете на: https://www.qr-code-generator.com/
- Изберете "Text"
- Поставете целия JSON
- Генерирайте и свалете

**Вариант 2: Python скрипт**
```python
pip install qrcode[pil]

import qrcode
import json

with open('provisioning_qr_template.json', 'r') as f:
    data = json.load(f)

qr = qrcode.QRCode(version=10, box_size=10, border=5)
qr.add_data(json.dumps(data))
qr.make(fit=True)

img = qr.make_image(fill_color="black", back_color="white")
img.save("provisioning_qr.png")
```

---

## 🧪 Стъпка 6: Тестване

### 6.1 Компилиране

```bash
./gradlew assembleRelease
```

APK файлът ще е в: `app/build/outputs/apk/release/app-release.apk`

### 6.2 Качване на сървър

Качете APK-то на вашия сървър на URL-а от JSON-а.

### 6.3 Factory Reset на тестово устройство

1. Settings → System → Reset options → Factory reset
2. Потвърдете reset-а

### 6.4 Provisioning

1. При welcome екрана, tap 6 пъти на екрана
2. Появява се опция за QR code scanning
3. Сканирайте вашия QR код
4. Устройството ще:
   - Свърже се към WiFi
   - Изтегли APK-то
   - Инсталира го като Device Owner
   - Стартира `ProvisioningCompleteActivity`
   - Приложи всички настройки
   - Стартира `MainActivity` в Kiosk Mode

---

## 🐛 Troubleshooting

### Проблем: "App is not a Device Owner"

**Решение:** Устройството не е factory reset преди provisioning
```bash
# Решение 1: Factory reset
# Решение 2: Ръчно чрез ADB (само за тестване!)
adb shell dpm set-device-owner com.autoplus.kiosklauncher/.DeviceAdminReceiver
```

### Проблем: Клавиатурата не се променя

**Решение:** Добавете permission в Manifest:
```xml
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"
    tools:ignore="ProtectedPermissions" />
```

### Проблем: Screen brightness не работи

**Решение:** Уверете се че приложението има WRITE_SETTINGS разрешение.

### Проблем: APK не се изтегля

**Решение:** 
- Проверете URL-а и SHA-256 hash-а
- Уверете се че сървърът е достъпен от устройството
- Проверете WiFi настройките в QR кода

---

## 📝 Допълнителни настройки (Опционално)

### Disable камера

```kotlin
// В enableKioskMode()
dpm.setCameraDisabled(adminComponent, true)
```

### User restrictions

```kotlin
import android.os.UserManager

dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
```

### Rotation lock (Portrait само)

```kotlin
import android.view.Surface

Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
```

---

## ✅ Checklist преди production

- [ ] Package name заменен навсякъде
- [ ] DeviceAdminReceiver правилно конфигуриран
- [ ] Клавиатура избрана
- [ ] Screen settings настроени
- [ ] Kiosk mode enable/disable правилно зададен
- [ ] QR код генериран с правилни данни
- [ ] APK качен на сървър
- [ ] SHA-256 hash проверен
- [ ] Тествано на реално устройство
- [ ] AndroidManifest.xml пълен и коректен
- [ ] build.gradle dependencies добавени

---

## 📞 Следващи стъпки

След като всичко работи:

1. **Масово разгръщане:**
   - Разпечатайте QR кода
   - Прикрепете го на warehouse-а
   - Factory reset всички устройства
   - Сканирайте QR кода

2. **Поддръжка:**
   - При промяна на настройки → нов QR код
   - При update на приложението → нов APK + SHA-256
   - Водете log на provisioned устройства

3. **Мониторинг:**
   - Проверявайте дали устройствата са online
   - Проверявайте версиите на APK-тата
   - Събирайте crash logs

---

Успех! 🚀
