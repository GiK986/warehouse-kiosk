# QR Provisioning - Quick Start Guide 🚀

## 📦 Инсталация (еднократно)

```bash
# Инсталирай Python зависимости
pip install qrcode[pil]
```

## ⚡ Бързи команди

### Преглед на конфигурации

```bash
# Всички локации
python3 provisioning/generate_qr.py --list-locations

# Всички WiFi профили
python3 provisioning/generate_qr.py --list-wifi
```

### Генериране на 1 QR код

```bash
# За конкретна локация
python3 provisioning/generate_qr.py --location sofia_central

# С персонализиран изход
python3 provisioning/generate_qr.py --location plovdiv -o qr_plovdiv.png

# БЕЗ WiFi (мобилна единица)
python3 provisioning/generate_qr.py --location mobile_unit_01 --no-wifi
```

### Генериране на ВСИЧКИ QR кодове

```bash
# Без build (използовай съществуващи configs)
./provisioning/generate_all_qr.sh

# С build на APK
./provisioning/generate_all_qr.sh app/build/outputs/apk/release/warehouse-kiosk-release.apk

# С build + APK URL
./provisioning/generate_all_qr.sh \
  app/build/outputs/apk/release/warehouse-kiosk-release.apk \
  https://your-server.com/warehouse-kiosk-release.apk
```

## 🔄 Production Workflow

### 1. Build APK

```bash
./gradlew clean assembleRelease
```

### 2. Upload APK на сървър

Upload на:
- AWS S3: `s3://your-bucket/warehouse-kiosk-release.apk`
- Firebase Hosting: `https://your-app.web.app/warehouse-kiosk-release.apk`
- GitHub Releases: `https://github.com/user/repo/releases/download/v1.0/warehouse-kiosk-release.apk`

### 3. Обнови URL в common_config.json

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://your-actual-url.com/warehouse-kiosk-release.apk"
}
```

### 4. Генерирай всички QR кодове

```bash
./provisioning/generate_all_qr.sh \
  app/build/outputs/apk/release/warehouse-kiosk-release.apk \
  https://your-actual-url.com/warehouse-kiosk-release.apk
```

### 5. Намери QR кодовете

```bash
ls -lh provisioning/qr_codes/
```

## 🆕 Добавяне на нова локация

### 1. Редактирай `provisioning/locations.json`

```json
{
  "locations": {
    "your_new_location": {
      "name": "Нова локация",
      "warehouse_id": "WH_NEW_01",
      "server_url": "https://api.warehouse.bg",
      "printer_ip": "192.168.1.100",
      "printer_name": "Zebra_ZD421",
      "scanner_type": "honeywell_1900",
      "recommended_wifi": "sofia_warehouse",
      "notes": "Описание"
    }
  }
}
```

### 2. (Опционално) Добави WiFi профил в `wifi_profiles.json`

```json
{
  "profiles": {
    "new_wifi": {
      "name": "Нова WiFi мрежа",
      "android.app.extra.PROVISIONING_WIFI_SSID": "New_SSID",
      "android.app.extra.PROVISIONING_WIFI_PASSWORD": "password",
      "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA"
    }
  }
}
```

### 3. Генерирай QR код

```bash
python3 provisioning/generate_qr.py --location your_new_location
```

## 📝 Чести сценарии

### Промяна на WiFi парола

```bash
# 1. Редактирай wifi_profiles.json
# 2. Регенерирай QR кодовете за засегнатите локации
python3 provisioning/generate_qr.py --location sofia_central -o qr_sofia_new.png
```

### Тестване без реален APK

```bash
# Генерирай QR код без checksum (за тест)
python3 provisioning/generate_qr.py --location sofia_central
# ⚠️ Не забравяй да обновиш checksum-а в common_config.json!
```

### Генериране с различен WiFi от препоръчания

```bash
# София Централен със Guest WiFi
python3 provisioning/generate_qr.py \
  --location sofia_central \
  --wifi guest_network
```

## 🐛 Troubleshooting

### "apksigner не е намерен"

```bash
# Добави Android SDK Build Tools в PATH
export PATH=$PATH:$ANDROID_HOME/build-tools/34.0.0
```

### "qrcode библиотеката не е инсталирана"

```bash
pip install qrcode[pil]
# Или за всички потребители:
pip3 install qrcode[pil]
```

### QR кодът не работи

**Checklist:**
- ✅ APK URL-ът е ли публично достъпен?
- ✅ Checksum-ът съвпада ли с APK-то?
- ✅ WiFi паролата правилна ли е?
- ✅ Component Name правилен ли е?

**Debug:**
```bash
# Виж JSON конфигурацията
python3 provisioning/generate_qr.py --location sofia_central
```

## 📂 Файлова структура

```
provisioning/
├── common_config.json      # Базови настройки
├── wifi_profiles.json      # WiFi мрежи
├── locations.json          # Локации
├── generate_qr.py          # Python генератор
├── generate_all_qr.sh      # Bash wrapper за масово генериране
├── qr_codes/               # Генерирани QR кодове (auto-created)
├── README.md               # Пълна документация
└── QUICK_START.md          # Този файл
```

## 🎯 Често използвани команди

```bash
# Списък с локации
python3 provisioning/generate_qr.py --list-locations

# Списък с WiFi
python3 provisioning/generate_qr.py --list-wifi

# Генериране за София
python3 provisioning/generate_qr.py --location sofia_central

# Генериране БЕЗ WiFi
python3 provisioning/generate_qr.py --location mobile_unit_01 --no-wifi

# Генериране на всички
./provisioning/generate_all_qr.sh

# Help
python3 provisioning/generate_qr.py --help
```

## 🔒 Security Note

**НЕ commit-вай чувствителни данни!**

Ако `wifi_profiles.json` или `locations.json` съдържат production пароли:

```bash
# Добави в .gitignore
echo "wifi_profiles.json" >> provisioning/.gitignore
echo "locations.json" >> provisioning/.gitignore
```

Или използвай template файлове:
- `wifi_profiles.template.json` (commit)
- `wifi_profiles.json` (local, gitignored)

---

**За пълна документация виж:** [README.md](README.md)