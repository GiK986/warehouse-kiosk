# QR Code Provisioning System

Модулна система за генериране на QR кодове за Android Device Owner provisioning с поддръжка на множество локации и WiFi профили.

## 📁 Структура

```
provisioning/
├── common_config.json    # Базова конфигурация (общо за всички локации)
├── wifi_profiles.json    # WiFi профили за различни мрежи
├── locations.json        # Локации и техните специфични настройки
├── generate_qr.py        # Python скрипт за генериране на QR кодове
└── README.md            # Тази документация
```

## 🚀 Бърз старт

### 1. Инсталация на зависимости

```bash
pip install qrcode[pil]
```

### 2. Преглед на налични локации

```bash
python provisioning/generate_qr.py --list-locations
```

### 3. Генериране на QR код

```bash
# За София Централен склад
python provisioning/generate_qr.py --location sofia_central

# За Пловдив с персонализиран WiFi
python provisioning/generate_qr.py --location plovdiv --wifi office_network

# За мобилна единица БЕЗ WiFi
python provisioning/generate_qr.py --location mobile_unit_01 --no-wifi
```

## 📋 Детайлни примери

### Показване на информация

```bash
# Всички локации
python provisioning/generate_qr.py --list-locations

# Всички WiFi профили
python provisioning/generate_qr.py --list-wifi
```

### Генериране с автоматичен checksum

```bash
python provisioning/generate_qr.py \
  --location sofia_central \
  --apk app/build/outputs/apk/release/warehouse-kiosk-release.apk
```

### Генериране с APK URL

```bash
python provisioning/generate_qr.py \
  --location varna \
  --apk-url https://github.com/user/repo/releases/download/v1.0.3/warehouse-kiosk-release.apk
```

### Генериране с персонализиран изход

```bash
python provisioning/generate_qr.py \
  --location burgas \
  --wifi burgas_warehouse \
  --output qr_burgas_warehouse.png
```

### Комбинирано използване

```bash
# Build APK + Генериране на QR код
./gradlew clean assembleRelease && \
python provisioning/generate_qr.py \
  --location sofia_west \
  --apk app/build/outputs/apk/release/warehouse-kiosk-release.apk \
  --apk-url https://your-server.com/warehouse-kiosk-release.apk \
  --output qr_sofia_west.png
```

## ⚙️ Конфигурация

### 1. common_config.json

Базови настройки, общи за всички локации:

- Device Admin Component Name
- APK Download Location
- Locale и Time Zone
- System Apps Settings

**Редактирай:**
``` json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.warehouse.kiosk/.DeviceOwnerReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://your-server.com/warehouse-kiosk-release.apk",
  ...
}
```

### 2. wifi_profiles.json

WiFi мрежи за различни обекти:

**Добавяне на нов WiFi профил:**
```json
{
  "profiles": {
    "your_new_wifi": {
      "name": "Описание на мрежата",
      "android.app.extra.PROVISIONING_WIFI_SSID": "WiFi_SSID",
      "android.app.extra.PROVISIONING_WIFI_PASSWORD": "password123",
      "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA"
    }
  }
}
```

**Security Types:** `WPA`, `WEP`, `NONE`

### 3. locations.json

Специфични настройки за всяка локация:

**Добавяне на нова локация:**
```json
{
  "locations": {
    "your_new_location": {
      "name": "Име на локацията",
      "warehouse_id": "WH_CODE_01",
      "recommended_wifi": "your_wifi_profile_id",
      "notes": "Допълнителни бележки"
    }
  }
}
```

**За мобилни единици БЕЗ WiFi:**
```json
{
  "recommended_wifi": null
}
```

## 🔧 CLI Параметри

| Параметър          | Кратка форма | Описание                            | Пример                      |
|--------------------|--------------|-------------------------------------|-----------------------------|
| `--list-locations` | -            | Показва всички локации              | `--list-locations`          |
| `--list-wifi`      | -            | Показва всички WiFi профили         | `--list-wifi`               |
| `--location`       | `-l`         | Избор на локация **(задължително)** | `--location sofia_central`  |
| `--wifi`           | `-w`         | Избор на WiFi профил                | `--wifi office_network`     |
| `--no-wifi`        | -            | БЕЗ WiFi конфигурация               | `--no-wifi`                 |
| `--apk`            | -            | Път до APK (автоматичен checksum)   | `--apk path/to/app.apk`     |
| `--apk-url`        | -            | URL към APK файла                   | `--apk-url https://...`     |
| `--output`         | `-o`         | Име на изходния файл                | `--output my_qr.png`        |
| `--config-dir`     | -            | Директория с configs                | `--config-dir provisioning` |

## 📝 Workflow за production

### Стъпка 1: Build на APK

```bash
./gradlew clean assembleRelease
```

### Стъпка 2: Upload на APK

Качи APK-то на публичен сървър:
- AWS S3
- Firebase Hosting
- GitHub Releases
- Твой собствен сървър

### Стъпка 3: Обнови common_config.json

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://your-server.com/warehouse-kiosk-release.apk"
}
```

### Стъпка 4: Генерирай QR код за всяка локация

```bash
# София
python provisioning/generate_qr.py \
  --location sofia_central \
  --apk app/build/outputs/apk/release/warehouse-kiosk-release.apk \
  --output qr_sofia.png

# Пловдив
python provisioning/generate_qr.py \
  --location plovdiv \
  --apk app/build/outputs/apk/release/warehouse-kiosk-release.apk \
  --output qr_plovdiv.png

# Мобилна единица (БЕЗ WiFi)
python provisioning/generate_qr.py \
  --location mobile_unit_01 \
  --no-wifi \
  --apk app/build/outputs/apk/release/warehouse-kiosk-release.apk \
  --output qr_mobile.png
```

### Стъпка 5: Разпечатай QR кодовете

Разпечатай QR кодовете и постави ги на всяка локация за бърз provisioning.

## 🔐 Checksum калкулиране

Скриптът автоматично калкулира checksum при използване на `--apk`:

```bash
python provisioning/generate_qr.py \
  --location sofia_central \
  --apk app/build/outputs/apk/release/warehouse-kiosk-release.apk
```

**Ръчно калкулиране (ако е нужно):**

```bash
# Чрез apksigner (препоръчително)
apksigner verify --print-certs warehouse-kiosk-release.apk

# Чрез keytool
keytool -printcert -jarfile warehouse-kiosk-release.apk
```

## 🎯 Use Cases

### 1. Нова локация

```bash
# 1. Добави WiFi профил в wifi_profiles.json
# 2. Добави локация в locations.json
# 3. Генерирай QR код
python provisioning/generate_qr.py --location new_location --output qr_new.png
```

### 2. Промяна на WiFi парола

```bash
# 1. Обнови паролата в wifi_profiles.json
# 2. Регенерирай QR кодовете за засегнатите локации
python provisioning/generate_qr.py --location sofia_central --output qr_sofia_new.png
```

### 3. Обновяване на APK

```bash
# 1. Build нова версия
./gradlew clean assembleRelease

# 2. Upload на сървъра
# 3. Обнови URL в common_config.json
# 4. Регенерирай всички QR кодове
for location in sofia_central plovdiv varna burgas; do
  python provisioning/generate_qr.py \
    --location $location \
    --apk app/build/outputs/apk/release/warehouse-kiosk-release.apk \
    --output qr_${location}_v2.png
done
```

### 4. Тестова среда

```bash
# Създай test_config.json с test сървър
# Генерирай тестови QR кодове
python provisioning/generate_qr.py \
  --location sofia_central \
  --config-dir provisioning/test \
  --output qr_test.png
```

## 🐛 Troubleshooting

### Грешка: "apksigner не е намерен"

**Решение:**
```bash
# Инсталирай Android SDK Build Tools
# Или добави в PATH:
export PATH=$PATH:$ANDROID_HOME/build-tools/34.0.0
```

### Грешка: "qrcode библиотеката не е инсталирана"

**Решение:**
```bash
pip install qrcode[pil]
```

### QR кодът не работи при сканиране

**Проверка:**
1. APK URL-ът е ли достъпен публично?
2. Checksum съвпада ли с APK-то?
3. WiFi паролата правилна ли е?
4. Device Admin Component Name правилен ли е?

**Debug:**
```bash
# Генерирай QR код и виж конфигурацията
python provisioning/generate_qr.py --location sofia_central
# Провери JSON output-а в терминала
```

## 📚 Допълнителни ресурси

- [Android Device Owner Provisioning Documentation](https://developer.android.com/work/dpc/dedicated-devices/provisioning)
- [QR Code Generator Details](https://developer.android.com/work/dpc/dedicated-devices/qr-code-provisioning)
- [Device Policy Manager API](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)

## 🤝 Принос

При добавяне на нова локация или WiFi профил:
1. Редактирай съответния JSON файл
2. Тествай генерирането на QR код
3. Документирай промените

---

**Версия:** 1.0
**Последна актуализация:** 2025-01-20