# Добавяне на допълнителни WiFi мрежи по време на Provisioning

## 📡 Проблем / Use Case

**Сценарий:** Искаш да конфигуриш устройство на едно място (напр. офис в София), но да го изпратиш да работи на друго място (напр. склад в Бургас) с различна WiFi мрежа.

**Цел:** Устройството да се свърже автоматично към новата мрежа БЕЗ намеса на служител на локация.

---

## 🔍 Как работи Android Provisioning WiFi?

### Ограничение на QR Code Provisioning

Android QR Code provisioning поддържа само **ЕДНА WiFi мрежа** в JSON payload-а:

```json
{
  "android.app.extra.PROVISIONING_WIFI_SSID": "EnGenius_WPA3",
  "android.app.extra.PROVISIONING_WIFI_PASSWORD": "Auto@2023",
  "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA"
}
```

Тази мрежа се използва **само по време на provisioning** за да:
- Свали APK файла
- Инсталира приложението
- Завърши първоначалната настройка

**НЕ МОЖЕ** да се добавят множество WiFi мрежи директно в QR кода!

---

## ✅ Решение: Програмно добавяне на WiFi след Provisioning

### Как работи решението?

1. **В QR кода:** Добавяш данни за **бъдещата WiFi мрежа** в `PROVISIONING_ADMIN_EXTRAS_BUNDLE`
2. **По време на provisioning:** Устройството се свързва на текущата (provisioning) мрежа
3. **След provisioning:** `ProvisioningCompleteActivity` прочита extras и **добавя втората WiFi мрежа** програмно
4. **На новата локация:** Устройството автоматично се свързва към запазената мрежа

---

## 🛠️ Имплементация

### Стъпка 1: Добави данни в `locations.json` (или `wifi_profiles.json`)

Разшири конфигурацията за всяка локация с допълнителна WiFi мрежа:

#### Опция А: Разширяване на `wifi_profiles.json`

```json
{
  "profiles": {
    "apl-main-wh": {
      "name": "APL Main Warehouse",

      // Текуща мрежа (за provisioning)
      "android.app.extra.PROVISIONING_WIFI_SSID": "EnGenius_WPA3",
      "android.app.extra.PROVISIONING_WIFI_PASSWORD": "Auto@2023",
      "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA",

      // НОВО: Бъдеща мрежа (за на локация)
      "future_wifi_ssid": "EnGenius_VoennaRampa",
      "future_wifi_password": "3myZB7dEg7sE",
      "future_wifi_security": "WPA"
    },

    "voenna_warehouse": {
      "name": "EnGenius Voenna Rampa",

      "android.app.extra.PROVISIONING_WIFI_SSID": "EnGenius_VoennaRampa",
      "android.app.extra.PROVISIONING_WIFI_PASSWORD": "3myZB7dEg7sE",
      "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA",

      // Тук няма future_wifi защото провижаниш НА локация
      "future_wifi_ssid": "",
      "future_wifi_password": "",
      "future_wifi_security": "WPA"
    }
  }
}
```

#### Опция Б: Добавяне на масив от допълнителни мрежи

Ако искаш да добавиш **множество WiFi мрежи** (не само една):

```json
{
  "profiles": {
    "multi_location_profile": {
      "name": "Multi-Location Device",

      // Provisioning мрежа
      "android.app.extra.PROVISIONING_WIFI_SSID": "EnGenius_WPA3",
      "android.app.extra.PROVISIONING_WIFI_PASSWORD": "Auto@2023",
      "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA",

      // МАСИВ от допълнителни мрежи
      "additional_wifi_networks": [
        {
          "ssid": "EnGenius_VoennaRampa",
          "password": "3myZB7dEg7sE",
          "security": "WPA",
          "priority": 40
        },
        {
          "ssid": "Warehouse_Burgas",
          "password": "Burgas2024!Secure",
          "security": "WPA",
          "priority": 30
        },
        {
          "ssid": "WarehouseKiosk_Office",
          "password": "Office2024!Admin",
          "security": "WPA",
          "priority": 20
        }
      ]
    }
  }
}
```

---

### Стъпка 2: Промени в `generate_qr.py`

Промени функцията `generate_qr()` за да добави WiFi данните в admin extras:

```python
# В generate_qr.py, около ред 286-292

# Добавя локационни данни в admin extras
admin_extras = {
    "warehouse_id": location["warehouse_id"],
    "wms_apk_url": location.get("wms_apk_url", ""),
    "location_name": location["name"]
}

# НОВО: Добави бъдещата WiFi мрежа от профила
if wifi_profile_id and wifi_profile_id in self.wifi_profiles["profiles"]:
    wifi_profile = self.wifi_profiles["profiles"][wifi_profile_id]

    # Опция А: Една допълнителна мрежа
    if "future_wifi_ssid" in wifi_profile and wifi_profile["future_wifi_ssid"]:
        admin_extras["add_wifi_ssid"] = wifi_profile["future_wifi_ssid"]
        admin_extras["add_wifi_password"] = wifi_profile["future_wifi_password"]
        admin_extras["add_wifi_security"] = wifi_profile.get("future_wifi_security", "WPA")
        print(f"📡 Additional WiFi: {wifi_profile['future_wifi_ssid']} will be added after provisioning")

    # Опция Б: Множество мрежи (като JSON стринг)
    if "additional_wifi_networks" in wifi_profile:
        import json
        admin_extras["additional_wifi_networks_json"] = json.dumps(wifi_profile["additional_wifi_networks"])
        print(f"📡 {len(wifi_profile['additional_wifi_networks'])} additional WiFi networks will be added")

payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"] = admin_extras
```

---

### Стъпка 3: Промени в `ProvisioningCompleteActivity.kt`

Добави метод за добавяне на WiFi мрежи програмно:

```kotlin
// В ProvisioningCompleteActivity.kt

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // ... existing code ...

    // Прочитане на provisioning extras
    val extras = intent.getBundleExtra("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE")
    if (extras != null) {
        Log.d(TAG, "Provisioning extras received: ${extras.keySet()}")

        // НОВО: Добави допълнителните WiFi мрежи
        addAdditionalWifiNetworks(extras)
    }

    // ... rest of code ...
}

/**
 * Добавя допълнителни WiFi мрежи програмно след provisioning
 *
 * ВАЖНО: Този метод работи само за Device Owner apps!
 * WifiManager.addNetwork() е deprecated в Android 10+, но има
 * специално изключение за Device Owner, Profile Owner и system apps.
 */
private fun addAdditionalWifiNetworks(extras: Bundle) {
    try {
        // Опция А: Една допълнителна мрежа
        val singleSsid = extras.getString("add_wifi_ssid")
        if (!singleSsid.isNullOrEmpty()) {
            val password = extras.getString("add_wifi_password") ?: ""
            val security = extras.getString("add_wifi_security", "WPA")
            addSingleWifiNetwork(singleSsid, password, security)
        }

        // Опция Б: Множество мрежи
        val networksJson = extras.getString("additional_wifi_networks_json")
        if (!networksJson.isNullOrEmpty()) {
            addMultipleWifiNetworks(networksJson)
        }

    } catch (e: Exception) {
        Log.e(TAG, "Failed to add additional WiFi networks", e)
    }
}

/**
 * Добавя една WiFi мрежа
 */
private fun addSingleWifiNetwork(ssid: String, password: String, securityType: String) {
    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val wifiConfig = WifiConfiguration().apply {
        SSID = "\"$ssid\""  // ВАЖНО: Трябва да е в кавички!
        preSharedKey = "\"$password\""  // ВАЖНО: Трябва да е в кавички!

        // КРИТИЧНО: Изчистваме и задаваме правилно allowedKeyManagement
        allowedKeyManagement.clear()

        when (securityType.uppercase()) {
            "WPA", "WPA2", "WPA3" -> {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)

                // Допълнителни настройки за WPA/WPA2
                allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
            }
            "WEP" -> {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                wexKeys[0] = "\"$password\""
                wepTxKeyIndex = 0
            }
            "NONE", "OPEN" -> {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                preSharedKey = null
            }
            "EAP" -> {
                // За EAP мрежи (корпоративен WiFi)
                allowedKeyManagement.clear()
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X)
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)

                // ВАЖНО: За EAP трябва допълнителна конфигурация:
                // - Certificates
                // - Domain
                // - Identity
                // - Phase2 authentication
                // Виж: docs/provisioning/EAP_WIFI_CONFIGURATION.md
            }
        }

        // Приоритет (по-високо = по-висок приоритет)
        priority = 40

        // ВАЖНО: Разреши автоматично свързване
        status = WifiConfiguration.Status.ENABLED
    }

    // Добавяме мрежата
    val networkId = wifiManager.addNetwork(wifiConfig)

    if (networkId >= 0) {
        // Запазваме конфигурацията
        wifiManager.saveConfiguration()

        // Не се свързваме СЕГА, но я активираме за автоматично свързване
        // false = не disconnect-ваме от текущата мрежа
        wifiManager.enableNetwork(networkId, false)

        Log.i(TAG, "✅ WiFi network added successfully: $ssid (ID: $networkId)")
    } else {
        Log.e(TAG, "❌ Failed to add WiFi network: $ssid (returned -1)")
    }
}

/**
 * Добавя множество WiFi мрежи от JSON масив
 */
private fun addMultipleWifiNetworks(networksJson: String) {
    try {
        val jsonArray = JSONArray(networksJson)
        var addedCount = 0

        for (i in 0 until jsonArray.length()) {
            val network = jsonArray.getJSONObject(i)
            val ssid = network.getString("ssid")
            val password = network.getString("password")
            val security = network.optString("security", "WPA")
            val priority = network.optInt("priority", 40)

            // Добави мрежата
            addSingleWifiNetwork(ssid, password, security)
            addedCount++
        }

        Log.i(TAG, "✅ Added $addedCount additional WiFi networks")

    } catch (e: JSONException) {
        Log.e(TAG, "Failed to parse additional WiFi networks JSON", e)
    }
}
```

---

## 🔐 Важни детайли

### 1. Device Owner Привилегия

`WifiManager.addNetwork()` е **deprecated в Android 10+**, НО има специално изключение:

> "For applications targeting Android 10 (Q) or above, this API will always fail and return -1, **but there are deprecation exemptions for Device Owner (DO), Profile Owner (PO) and system apps.**"

Това означава, че **само Device Owner apps** могат да го използват!

### 2. Правилно форматиране на SSID и Password

```kotlin
// ГРЕШНО ❌
SSID = ssid
preSharedKey = password

// ПРАВИЛНО ✅
SSID = "\"$ssid\""          // С кавички!
preSharedKey = "\"$password\""  // С кавички!
```

### 3. KeyManagement конфигурация

**КРИТИЧНО:** Трябва да изчистиш и зададеш правилно `allowedKeyManagement`:

```kotlin
allowedKeyManagement.clear()  // Изчисти всичко първо!
allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
```

Без `.clear()` мрежата може да не се запази правилно!

### 4. Автоматично свързване

```kotlin
// Задай status = ENABLED
status = WifiConfiguration.Status.ENABLED

// След addNetwork():
wifiManager.enableNetwork(networkId, false)  // false = не disconnect-вай от текущата
wifiManager.saveConfiguration()
```

### 5. Приоритет

```kotlin
priority = 40  // По-висок приоритет = предпочита се тази мрежа
```

Типични стойности:
- 40-50: Високо приоритетни мрежи (office, main warehouse)
- 20-30: Средно приоритетни
- 0-10: Ниско приоритетни (guest networks)

---

## 📊 Примерен Work Flow

### Сценарий: Provisioning в София, изпращане в Бургас

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Генериране на QR код в София                            │
│    - Provisioning WiFi: "EnGenius_WPA3" (София офис)       │
│    - Future WiFi: "Warehouse_Burgas" (Бургас склад)        │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. QR Code Provisioning (София)                            │
│    ├─ Scan QR код                                          │
│    ├─ Свързване към "EnGenius_WPA3"                        │
│    ├─ Download APK                                         │
│    ├─ Инсталация                                           │
│    └─ Device Owner set успешно                             │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. ProvisioningCompleteActivity стартира                   │
│    ├─ Прочита ADMIN_EXTRAS_BUNDLE                          │
│    ├─ Намира "add_wifi_ssid": "Warehouse_Burgas"           │
│    ├─ Извиква addSingleWifiNetwork()                       │
│    └─ Добавя "Warehouse_Burgas" като saved network         │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Устройството е готово                                   │
│    Запазени WiFi мрежи:                                    │
│    ✓ "EnGenius_WPA3" (connected)                           │
│    ✓ "Warehouse_Burgas" (saved, not connected)             │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Изпращане на устройството в Бургас                      │
│    - Физически транспорт от София до Бургас                │
│    - Устройството е в Kiosk Mode                           │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. На локация в Бургас (автоматично!)                      │
│    ├─ Устройството вижда "Warehouse_Burgas"                │
│    ├─ Автоматично се свързва (saved network)               │
│    └─ Без намеса на служител! ✅                           │
└─────────────────────────────────────────────────────────────┘
```

---

## ⚠️ Известни проблеми и ограничения

### 1. Android 11+ Auto-Connect Issue

На някои устройства с Android 11+ има бъг:
- Мрежата се запазва успешно
- Появява се в Settings като "saved by device owner"
- НО не се свързва автоматично!

**Workaround:** Добави periodic WiFi check:

```kotlin
// В MainActivity или background service
class WifiAutoConnectWorker(context: Context, params: WorkerParameters)
    : Worker(context, params) {

    override fun doWork(): Result {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Ако не сме свързани, опитай saved networks
        if (!wifiManager.isWifiEnabled || !isConnectedToWifi()) {
            reconnectToSavedNetworks()
        }

        return Result.success()
    }

    private fun isConnectedToWifi(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun reconnectToSavedNetworks() {
        // Logic to manually reconnect to saved networks
        Log.d("WiFi", "Attempting to reconnect to saved networks")
    }
}

// Schedule periodic checks (every 30 min)
val workRequest = PeriodicWorkRequestBuilder<WifiAutoConnectWorker>(30, TimeUnit.MINUTES).build()
WorkManager.getInstance(context).enqueue(workRequest)
```

### 2. EAP WiFi Complexity

За корпоративен WiFi (EAP) е по-сложно:
- Трябват certificates
- Domain configuration
- Phase 2 authentication
- Device security (PIN lock задължителен!)

Виж: `docs/provisioning/EAP_WIFI_CONFIGURATION.md` (за бъдеща имплементация)

### 3. WPA3 Compatibility

Някои стари устройства не поддържат WPA3. Използовай WPA2 за compatibility:

```json
"security": "WPA"  // Covers WPA, WPA2, WPA3 automatically
```

---

## 📚 Референции

### Android Documentation
- [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- [WifiManager](https://developer.android.com/reference/android/net/wifi/WifiManager)
- [WifiConfiguration](https://developer.android.com/reference/android/net/wifi/WifiConfiguration)
- [Device Owner Provisioning](https://source.android.com/docs/devices/admin/provision)

### Stack Overflow Discussions
- [How to save EAP wifi network in Android 10 after provisioning](https://stackoverflow.com/questions/64280397)
- [WiFi saved programmatically by device owner doesn't auto-connect](https://stackoverflow.com/questions/71673107)
- [Android 10 WifiManager.addNetwork deprecated alternatives](https://stackoverflow.com/questions/58769623)

### Blog Posts
- [Connecting to WiFi in Android 10](https://blog.ostebaronen.dk/2019/11/android-10-wifi.html)

---

## 🎯 Заключение

**Преимущества:**
✅ Автоматично добавяне на WiFi мрежи при provisioning
✅ Без намеса на служител на локация
✅ Подходящо за fleet deployment
✅ Работи на Android 10+ с Device Owner privileges

**Ограничения:**
⚠️ Работи САМО за Device Owner apps
⚠️ Deprecated API (но с изключение за DO)
⚠️ Auto-connect може да не работи на Android 11+ (needs workaround)
⚠️ EAP networks са по-сложни

**Алтернативи:**
- Използовай EMM/MDM solution (Intune, VMware Workspace ONE, etc.)
- WifiNetworkSuggestion API (но не persists)
- Manual WiFi configuration on-site (не е автоматично)

---

**Created:** 2025-01-22
**Last Updated:** 2025-01-22
**Status:** 📝 Documentation (Not Implemented)