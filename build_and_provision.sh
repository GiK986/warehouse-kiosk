#!/bin/bash
# Комплетен workflow: Build -> Sign -> Calculate -> Generate QR

set -e

PROJECT_DIR="./"
PACKAGE_NAME="com.warehouse.kiosk"
RECEIVER_CLASS=".services.DeviceOwnerReceiver"
APK_URL="https://github.com/GiK986/warehouse-kiosk/releases/download/v1.0.0/warehouse-kiosk-release.apk"
WIFI_SSID="GiKHome"
WIFI_PASSWORD="qwer@1234"

print_step() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "▶ $1"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

# Конфигурация
#read -p "📦 Пакетно име (com.warehouse.kiosk): " PACKAGE_NAME
#read -p "🎯 DeviceAdminReceiver клас (.MyReceiver): " RECEIVER_CLASS
#read -p "🌐 HTTPS URL към APK: " APK_URL
#read -p "📁 Път до Android проект (./): " PROJECT_DIR

PACKAGE_NAME=${PACKAGE_NAME:-"com.warehouse.kiosk"}
RECEIVER_CLASS=${RECEIVER_CLASS:-".services.DeviceOwnerReceiver"}
PROJECT_DIR=${PROJECT_DIR:-.}

print_step "1. Градиране на проекта"
cd "$PROJECT_DIR"
if [ -f "gradlew" ]; then
    ./gradlew clean assembleRelease
else
    gradle clean assembleRelease
fi

# Намери APK файла
APK_FILE=$(find app/build/outputs/apk/release -name "*-release.apk" -type f | head -1)
if [ -z "$APK_FILE" ]; then
    echo "❌ APK файл не намерен!"
    exit 1
fi

echo "✅ APK gradlew: $APK_FILE"


print_step "2. Калкулиране на контролната сума"
CHECKSUM=$(python3 - "$APK_FILE" << 'PYTHON_EOF'
import subprocess
import sys
import re
import base64

apk_path = sys.argv[1]
result = subprocess.run(
    ['apksigner', 'verify', '--print-certs', apk_path],
    capture_output=True,
    text=True
)

for line in result.stdout.split('\n'):
    if 'Signer #1 certificate SHA-256' in line:
        hash_hex = re.search(r'SHA-256 digest: ([a-f0-9 ]+)', line)
        if hash_hex:
            hash_str = hash_hex.group(1).replace(' ', '')
            hash_bytes = bytes.fromhex(hash_str)
            b64 = base64.b64encode(hash_bytes).decode('ascii')
            url_safe = b64.replace('+', '-').replace('/', '_').rstrip('=')
            print(url_safe)
            break
PYTHON_EOF
)

if [ -z "$CHECKSUM" ]; then
    echo "❌ Не съм могла да калкулирам контролната сума!"
    exit 1
fi

echo "✅ Checksum: $CHECKSUM"

print_step "3. Генериране на QR код"
python3 - <<EOF
import json
import qrcode

payload = {
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "$PACKAGE_NAME/$RECEIVER_CLASS",
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "$CHECKSUM",
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "$APK_URL",
#    "android.app.extra.PROVISIONING_WIFI_SSID": "$WIFI_SSID",
#    "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA", # Може да е WEP, EAP или OPEN
#    "android.app.extra.PROVISIONING_WIFI_PASSWORD": "$WIFI_PASSWORD",
    "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": False,
    "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": False,
}
print(payload)
json_string = json.dumps(payload, separators=(',', ':'))
qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_H, box_size=10, border=4)
qr.add_data(json_string)
qr.make(fit=True)
img = qr.make_image(fill_color="black", back_color="white")
img.save('device_owner_qr.png')
print("✅ QR код генериран: device_owner_qr.png")
EOF

print_step "Резюме"
echo "✅ Всички стъпки са завършени успешно!"
echo ""
echo "📋 Информация за provisioning:"
echo "   Пакет: $PACKAGE_NAME"
echo "   Receiver: $RECEIVER_CLASS"
echo "   Checksum: $CHECKSUM"
echo "   APK URL: $APK_URL"
echo "   QR код: device_owner_qr.png"
echo ""
echo "Бяхш:"
echo "1. Направи factory reset на устройството"
echo "2. На Welcome екран тапни 6 пъти"
echo "3. Сканирай QR код (device_owner_qr.png)"