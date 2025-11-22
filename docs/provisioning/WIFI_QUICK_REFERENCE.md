# 📡 WiFi Multi-Network - Бърза Референция

> **Кратка версия на ADDITIONAL_WIFI_NETWORKS.md**
> За пълна документация виж: `ADDITIONAL_WIFI_NETWORKS.md`

---

## ❓ Проблем

Искаш да направиш provisioning устройство на локация А (София), но да го изпратиш на локация Б (Бургас) с различна WiFi мрежа, **без** служител да конфигурира WiFi на локация.

---

## ✅ Решение

### Концепция
1. QR кодът съдържа provisioning WiFi (София) + data за future WiFi (Бургас)
2. При provisioning се свързва на София WiFi
3. След provisioning, Device Owner app **програмно добавя** Бургас WiFi
4. На локация Б, устройството автоматично се свързва към Бургас WiFi ✨

---

## 📝 Имплементация (Кратко)

### 1. Добави в `wifi_profiles.json`:

``` json
"apl-main-wh": {
  "android.app.extra.PROVISIONING_WIFI_SSID": "EnGenius_WPA3",
  "android.app.extra.PROVISIONING_WIFI_PASSWORD": "Auto@2023",
  "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA",

  "future_wifi_ssid": "Warehouse_Burgas",
  "future_wifi_password": "Burgas2024!Secure",
  "future_wifi_security": "WPA"
}
```

### 2. Промени `generate_qr.py` (ред ~286):

``` python
admin_extras = {
    "warehouse_id": location["warehouse_id"],
    "wms_apk_url": location.get("wms_apk_url", ""),
    "location_name": location["name"],

    # НОВО
    "add_wifi_ssid": wifi_profile.get("future_wifi_ssid", ""),
    "add_wifi_password": wifi_profile.get("future_wifi_password", ""),
    "add_wifi_security": wifi_profile.get("future_wifi_security", "WPA")
}
```

### 3. Добави в `ProvisioningCompleteActivity.kt`:

``` kotlin
// В onCreate() след ред 74
val extras = intent.getBundleExtra("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE")
if (extras != null) {
    addAdditionalWifiNetwork(extras)
}

// Нов метод
private fun addAdditionalWifiNetwork(extras: Bundle) {
    val ssid = extras.getString("add_wifi_ssid") ?: return
    val password = extras.getString("add_wifi_password") ?: return

    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val wifiConfig = WifiConfiguration().apply {
        SSID = "\"$ssid\""  // ВАЖНО: С кавички!
        preSharedKey = "\"$password\""

        allowedKeyManagement.clear()
        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)

        priority = 40
        status = WifiConfiguration.Status.ENABLED
    }

    val networkId = wifiManager.addNetwork(wifiConfig)
    if (networkId >= 0) {
        wifiManager.saveConfiguration()
        wifiManager.enableNetwork(networkId, false)
        Log.i(TAG, "✅ WiFi added: $ssid")
    }
}
```

---

## ⚠️ Важно!

### Device Owner Привилегия
`WifiManager.addNetwork()` е **deprecated** в Android 10+, НО работи за Device Owner apps!

### SSID и Password Форматиране
```kotlin
SSID = "\"$ssid\""          // ✅ ПРАВИЛНО - с кавички
SSID = ssid                 // ❌ ГРЕШНО
```

### KeyManagement
```kotlin
allowedKeyManagement.clear()  // Винаги clear първо!
allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
```

---

## 📊 Work Flow

```
[София офис]
   ├─ QR код: provisioning_wifi=EnGenius_WPA3
   │           future_wifi=Warehouse_Burgas
   │
   ├─ Scan QR → Provisioning
   │
   ├─ ProvisioningCompleteActivity.addAdditionalWifiNetwork()
   │  └─ Добавя "Warehouse_Burgas" като saved network
   │
   └─ Устройство готово с 2 WiFi мрежи:
       ✓ EnGenius_WPA3 (connected)
       ✓ Warehouse_Burgas (saved)

[Транспорт до Бургас]

[Бургас склад]
   └─ Устройство вижда "Warehouse_Burgas"
      └─ Автоматично се свързва! ✅
```

---

## 🐛 Known Issues

### Android 11+ Auto-Connect
Понякога мрежата се запазва, но не се свързва автоматично.

**Workaround:** Periodic WiFi check worker (виж ADDITIONAL_WIFI_NETWORKS.md)

### EAP WiFi
За корпоративен WiFi (EAP) е сложно:
- Нужни certificates
- Domain config
- PIN lock задължителен

---

## 📚 Пълна Документация

За детайли виж:
- `docs/provisioning/ADDITIONAL_WIFI_NETWORKS.md` - Пълно ръководство
- `docs/provisioning/ADMIN_EXTRAS_USAGE.md` - Admin extras примери

---

**Status:** 📝 Документация (Not Implemented)
**Създадено:** 2025-01-22