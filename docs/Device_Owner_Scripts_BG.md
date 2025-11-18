# Практически скриптове за Device Owner Provisioning

## 1. Скрипт за калкулиране на checksum (Python)

**Файл: calculate_checksum.py**

```python
#!/usr/bin/env python3
"""
Калкулира SHA-256 контролна сума на APK файла
Поддържа както Package Checksum, така и Signature Checksum
"""

import subprocess
import sys
import os
import base64
import re

def get_signature_checksum(apk_path):
    """
    Калкулира signature checksum на APK файла
    Използва apksigner за надеждност
    """
    try:
        # Разбери където е apksigner
        result = subprocess.run(
            ['which', 'apksigner'],
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            print("❌ apksigner не е намерен. Инсталирай Android SDK Build Tools.")
            print("   Местоположение: <Android SDK>/build-tools/*/apksigner")
            sys.exit(1)
        
        apksigner_path = result.stdout.strip()
        print(f"ℹ️  Намерен apksigner: {apksigner_path}")
        
        # Извърши apksigner команда
        result = subprocess.run(
            [apksigner_path, 'verify', '--print-certs', apk_path],
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            print("❌ Грешка при запуск на apksigner")
            print(result.stderr)
            return None
        
        # Намери SHA-256 строката
        for line in result.stdout.split('\n'):
            if 'Signer #1 certificate SHA-256' in line:
                # Извлеки хеш от линията
                hash_hex = re.search(r'SHA-256 digest: ([a-f0-9 ]+)', line)
                if hash_hex:
                    hash_str = hash_hex.group(1).replace(' ', '')
                    
                    # Преобразуй hex към binary
                    hash_bytes = bytes.fromhex(hash_str)
                    
                    # Кодирай като base64
                    b64 = base64.b64encode(hash_bytes).decode('ascii')
                    
                    # Направи URL-safe (замени + с -, / с _, премахни =)
                    url_safe = b64.replace('+', '-').replace('/', '_').rstrip('=')
                    
                    return url_safe
        
        print("❌ Не съм намерил SHA-256 в apksigner резултата")
        return None
        
    except Exception as e:
        print(f"❌ Грешка: {e}")
        return None

def get_package_checksum(apk_path):
    """
    Калкулира package checksum (SHA-256 на целия APK файл)
    """
    try:
        # Провери дали файлът съществува
        if not os.path.exists(apk_path):
            print(f"❌ Файлът не съществува: {apk_path}")
            return None
        
        # Калкулирай SHA-256
        result = subprocess.run(
            ['sha256sum', apk_path],
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            print("❌ Грешка при калкулиране на SHA-256")
            return None
        
        # Извлеки хеш
        hash_hex = result.stdout.split()[0]
        
        # Преобразуй към binary и base64
        hash_bytes = bytes.fromhex(hash_hex)
        b64 = base64.b64encode(hash_bytes).decode('ascii')
        
        # Направи URL-safe
        url_safe = b64.replace('+', '-').replace('/', '_').rstrip('=')
        
        return url_safe
        
    except Exception as e:
        print(f"❌ Грешка: {e}")
        return None

def main():
    if len(sys.argv) < 2:
        print("Употреба: python calculate_checksum.py <path_to_apk> [--package]")
        print("")
        print("Примери:")
        print("  # Signature checksum (препоръчано за всички версии):")
        print("  python calculate_checksum.py app-release.apk")
        print("")
        print("  # Package checksum (само за конкретна версия):")
        print("  python calculate_checksum.py app-release.apk --package")
        sys.exit(1)
    
    apk_path = sys.argv[1]
    use_package = '--package' in sys.argv
    
    if use_package:
        print("📦 Калкулиране на Package Checksum...")
        checksum = get_package_checksum(apk_path)
        if checksum:
            print("\n✅ Package Checksum:")
            print(f"   {checksum}")
            print("\nПараметър за JSON:")
            print(f"   \"android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM\": \"{checksum}\"")
    else:
        print("🔐 Калкулиране на Signature Checksum...")
        checksum = get_signature_checksum(apk_path)
        if checksum:
            print("\n✅ Signature Checksum:")
            print(f"   {checksum}")
            print("\nПараметър за JSON:")
            print(f"   \"android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM\": \"{checksum}\"")

if __name__ == "__main__":
    main()
```

**Употреба:**
```bash
python calculate_checksum.py app/build/outputs/apk/release/app-release.apk
```

---

## 2. Генератор на QR код (Python)

**Файл: generate_qr_code.py**

```python
#!/usr/bin/env python3
"""
Генерира QR код за Device Owner provisioning
"""

import json
import qrcode
import sys
import argparse
from pathlib import Path

def create_provisioning_json(
    package_name,
    receiver_class,
    checksum,
    apk_url,
    skip_encryption=False,
    leave_all_apps=False,
    wifi_ssid=None,
    wifi_password=None,
    wifi_security=None,
    admin_extras=None
):
    """
    Създава provisioning JSON обект
    """
    
    payload = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": 
            f"{package_name}/{receiver_class}",
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": 
            checksum,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": 
            apk_url,
        "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": 
            skip_encryption,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": 
            leave_all_apps,
    }
    
    # Добави Wyfi ако е конфигурирана
    if wifi_ssid:
        payload["android.app.extra.PROVISIONING_WIFI_SSID"] = wifi_ssid
        if wifi_password:
            payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = wifi_password
        if wifi_security:
            payload["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] = wifi_security
    
    # Добави допълнителни параметри ако има
    if admin_extras:
        payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"] = admin_extras
    
    return payload

def generate_qr(data, output_file="qr_code.png", size=10):
    """
    Генерира QR код от JSON данни
    """
    
    # Преобразуй JSON в string без преводи
    json_string = json.dumps(data, separators=(',', ':'))
    
    print(f"📝 JSON данни ({len(json_string)} символа):")
    print(json_string[:100] + "..." if len(json_string) > 100 else json_string)
    print()
    
    # Генерирай QR код
    try:
        qr = qrcode.QRCode(
            version=None,  # Auto-detect size
            error_correction=qrcode.constants.ERROR_CORRECT_H,  # High correction
            box_size=size,
            border=4,
        )
        qr.add_data(json_string)
        qr.make(fit=True)
        
        # Създай изображение
        img = qr.make_image(fill_color="black", back_color="white")
        img.save(output_file)
        
        print(f"✅ QR код генериран успешно!")
        print(f"   Файл: {output_file}")
        print(f"   Размер: {img.size}")
        print(f"   JSON дължина: {len(json_string)} символа")
        
        return True
        
    except Exception as e:
        print(f"❌ Грешка при генериране на QR: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(
        description="Генерира QR код за Android Device Owner provisioning"
    )
    
    # Задължителни параметри
    parser.add_argument('--package', required=True,
                        help='Пакетно име (com.example.app)')
    parser.add_argument('--receiver', required=True,
                        help='DeviceAdminReceiver клас (.MyReceiver)')
    parser.add_argument('--checksum', required=True,
                        help='SHA-256 signature checksum')
    parser.add_argument('--url', required=True,
                        help='HTTPS URL към APK файла')
    
    # Допълнителни параметри
    parser.add_argument('--output', default='qr_code.png',
                        help='Файл за QR код (default: qr_code.png)')
    parser.add_argument('--wifi-ssid',
                        help='Wyfi име (opcional)')
    parser.add_argument('--wifi-password',
                        help='Wyfi парола (opcional)')
    parser.add_argument('--wifi-security', default='WPA',
                        help='Wyfi сигурност: WPA, WEP, OPEN (default: WPA)')
    parser.add_argument('--skip-encryption', action='store_true',
                        help='Пропусни криптография (не препоръчвам)')
    parser.add_argument('--leave-all-apps', action='store_true',
                        help='Остави всички system apps активни')
    parser.add_argument('--size', type=int, default=10,
                        help='Размер на QR кода в пиксели (default: 10)')
    
    args = parser.parse_args()
    
    print("🚀 Генератор на QR код за Device Owner Provisioning\n")
    print("=" * 60)
    
    # Проверки
    if not args.url.startswith('https://'):
        print("⚠️  Внимание: URL не е HTTPS. Препоръчвам HTTPS за сигурност.")
    
    # Създай provisioning JSON
    payload = create_provisioning_json(
        package_name=args.package,
        receiver_class=args.receiver,
        checksum=args.checksum,
        apk_url=args.url,
        skip_encryption=args.skip_encryption,
        leave_all_apps=args.leave_all_apps,
        wifi_ssid=args.wifi_ssid,
        wifi_password=args.wifi_password,
        wifi_security=args.wifi_security if args.wifi_ssid else None,
    )
    
    print("📋 Конфигурация:")
    print(f"   Пакет: {args.package}")
    print(f"   Receiver: {args.receiver}")
    print(f"   APK URL: {args.url}")
    if args.wifi_ssid:
        print(f"   Wyfi: {args.wifi_ssid}")
    print()
    
    # Генерирай QR код
    if generate_qr(payload, args.output, args.size):
        print("\n" + "=" * 60)
        print("✨ Готов! QR кодът е готов за використане.")
        print("\nБяхш:")
        print("1. Направи factory reset на устройството")
        print("2. На Welcome екран тапни 6 пъти")
        print("3. Сканирай този QR код с камерата")
    else:
        sys.exit(1)

if __name__ == "__main__":
    main()
```

**Употреба:**
```bash
python generate_qr_code.py \
  --package com.example.mydeviceowner \
  --receiver .DeviceOwnerReceiver \
  --checksum I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg \
  --url https://example.com/app.apk \
  --output device_owner_qr.png

# С Wyfi:
python generate_qr_code.py \
  --package com.example.mydeviceowner \
  --receiver .DeviceOwnerReceiver \
  --checksum I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg \
  --url https://example.com/app.apk \
  --wifi-ssid "MyNetwork" \
  --wifi-password "MyPassword" \
  --output device_owner_qr.png
```

---

## 3. ADB комендосен помощник (Bash)

**Файл: device_owner_adb.sh**

```bash
#!/bin/bash
# Комендосен помощник за Device Owner управление през ADB

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

check_adb() {
    if ! command -v adb &> /dev/null; then
        print_error "ADB не е намерен. Инсталирай Android SDK Platform Tools."
        exit 1
    fi
    print_success "ADB е намерен"
}

check_device() {
    if [ -z "$(adb devices | grep -v 'List of' | grep 'device$')" ]; then
        print_error "Няма подключено устройство през ADB"
        exit 1
    fi
    print_success "Устройството е подключено"
}

show_device_owner() {
    print_header "Статус на Device Owner"
    
    echo -e "${BLUE}Текущ Device Owner:${NC}"
    adb shell cmd device_policy get-device-owner || echo "Няма Device Owner"
    
    echo -e "\n${BLUE}Всички активни админи:${NC}"
    adb shell dpm list-admins || echo "Няма активни админи"
}

show_provisioning_logs() {
    print_header "Логове на Provisioning"
    
    echo -e "${YELLOW}Записвам логове... (натисни Ctrl+C за спиране)${NC}"
    echo ""
    
    adb logcat | grep -i "provisioning\|device_policy\|dpm"
}

show_detailed_info() {
    print_header "Детайлна информация"
    
    echo -e "${BLUE}Device Policy Manager információ:${NC}"
    adb shell dumpsys device_policy | head -50
}

enable_dev_mode() {
    print_header "Активиране на Developer Mode"
    
    print_warning "Това ще активира USB Debug режим"
    
    adb shell settings put global development_settings_enabled 1
    adb shell settings put secure usb_debug 1
    
    print_success "Developer Mode активиран"
}

clear_setup_complete() {
    print_header "Отнулиране на Setup Status"
    
    print_warning "Това ще маркира устройството като нестартирано"
    print_warning "Понякога помага за повторен provisioning"
    
    adb shell settings delete global setup_wizard_has_run
    adb shell settings delete global device_provisioned
    adb shell pm install-existing --user 0 com.android.systemui
    
    print_success "Setup статус отнулен"
}

view_wifi_config() {
    print_header "Конфигурация на Wyfi"
    
    echo -e "${BLUE}Запазена Wyfi мрежа:${NC}"
    adb shell dumpsys wifi | grep -i ssid || echo "Няма запазена мрежа"
}

check_package() {
    if [ -z "$1" ]; then
        print_error "Необходимо е пакетно име"
        exit 1
    fi
    
    print_header "Информация за пакет: $1"
    
    if adb shell pm list packages | grep -q "$1"; then
        print_success "Пакетът е инсталиран"
        adb shell pm dump "$1" | head -20
    else
        print_error "Пакетът не е инсталиран"
    fi
}

watch_logs() {
    print_header "Реални логове (Tail Mode)"
    
    echo -e "${YELLOW}Показвам последните 100 логове и наблюдавам...${NC}"
    echo ""
    
    adb logcat -T 100 -v threadtime
}

main() {
    check_adb
    check_device
    
    if [ $# -eq 0 ]; then
        echo "Употреба: $0 <команда> [аргументи]"
        echo ""
        echo "Команди:"
        echo "  status              - Покази Device Owner статус"
        echo "  logs                - Логове на provisioning (реално време)"
        echo "  info                - Детайлна информация"
        echo "  dev-mode            - Активирай Developer Mode"
        echo "  clear-setup         - Отнулирай setup статус"
        echo "  wifi                - Wyfi конфигурация"
        echo "  package <name>      - Информация за пакет"
        echo "  watch               - Наблюдавай логове в реално време"
        echo ""
        exit 0
    fi
    
    case $1 in
        status)
            show_device_owner
            ;;
        logs)
            show_provisioning_logs
            ;;
        info)
            show_detailed_info
            ;;
        dev-mode)
            enable_dev_mode
            ;;
        clear-setup)
            clear_setup_complete
            ;;
        wifi)
            view_wifi_config
            ;;
        package)
            check_package "$2"
            ;;
        watch)
            watch_logs
            ;;
        *)
            print_error "Неизвестна команда: $1"
            exit 1
            ;;
    esac
}

main "$@"
```

**Използване:**
```bash
chmod +x device_owner_adb.sh
./device_owner_adb.sh status        # Види Device Owner статус
./device_owner_adb.sh logs          # Види provisioning логове
./device_owner_adb.sh watch         # Наблюдавай логовете
./device_owner_adb.sh package com.example.app  # Информация за пакет
```

---

## 4. Комплетен workflow скрипт (Bash)

**Файл: build_and_provision.sh**

```bash
#!/bin/bash
# Комплетен workflow: Build -> Sign -> Calculate -> Generate QR

set -e

PROJECT_DIR="."
PACKAGE_NAME=""
RECEIVER_CLASS=""
APK_URL=""

print_step() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "▶ $1"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

# Конфигурация
read -p "📦 Пакетно име (com.example.app): " PACKAGE_NAME
read -p "🎯 DeviceAdminReceiver клас (.MyReceiver): " RECEIVER_CLASS
read -p "🌐 HTTPS URL към APK: " APK_URL
read -p "📁 Път до Android проект (./): " PROJECT_DIR

PROJECT_DIR=${PROJECT_DIR:-.}

print_step "1. Градиране на проекта"
cd "$PROJECT_DIR"
if [ -f "gradlew" ]; then
    ./gradlew clean assembleRelease
else
    gradle clean assembleRelease
fi

# Намери APK файла
APK_FILE=$(find . -name "*release.apk" -type f | head -1)
if [ -z "$APK_FILE" ]; then
    echo "❌ APK файл не намерен!"
    exit 1
fi

echo "✅ APK градиран: $APK_FILE"

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
    "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": False,
    "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": False,
}

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
```

**Употреба:**
```bash
chmod +x build_and_provision.sh
./build_and_provision.sh
```

---

## 5. JSON Configuration Builder (онлайн)

За те, които предпочитат интерфейс можеш да използваш този HTML:

**Файл: qr_builder.html**

```html
<!DOCTYPE html>
<html lang="bg">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Device Owner QR Builder</title>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        
        h1 {
            color: white;
            text-align: center;
            margin-bottom: 30px;
            font-size: 2.5em;
        }
        
        .grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 30px;
            margin-bottom: 30px;
        }
        
        .card {
            background: white;
            border-radius: 12px;
            padding: 30px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.2);
        }
        
        .form-group {
            margin-bottom: 20px;
        }
        
        label {
            display: block;
            font-weight: 600;
            margin-bottom: 8px;
            color: #333;
            font-size: 0.95em;
        }
        
        input[type="text"],
        input[type="url"],
        select {
            width: 100%;
            padding: 12px;
            border: 2px solid #e0e0e0;
            border-radius: 6px;
            font-size: 1em;
            transition: border-color 0.3s;
        }
        
        input:focus,
        select:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
        }
        
        .hint {
            font-size: 0.85em;
            color: #666;
            margin-top: 6px;
        }
        
        button {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 6px;
            font-weight: 600;
            cursor: pointer;
            transition: transform 0.2s;
            font-size: 1em;
            width: 100%;
            margin-top: 10px;
        }
        
        button:hover {
            transform: translateY(-2px);
        }
        
        button:active {
            transform: translateY(0);
        }
        
        #qrCode {
            text-align: center;
            padding: 20px;
            background: #f5f5f5;
            border-radius: 8px;
            min-height: 300px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        
        .json-display {
            background: #2d3748;
            color: #68d391;
            padding: 15px;
            border-radius: 6px;
            font-family: 'Courier New', monospace;
            font-size: 0.85em;
            overflow-x: auto;
            margin-bottom: 15px;
            word-break: break-all;
        }
        
        .copy-btn {
            background: #4299e1;
            padding: 8px 16px;
            font-size: 0.9em;
            margin: 10px 0;
        }
        
        @media (max-width: 768px) {
            .grid {
                grid-template-columns: 1fr;
            }
            
            h1 {
                font-size: 1.8em;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>📱 Device Owner QR Builder</h1>
        
        <div class="grid">
            <!-- Input Form -->
            <div class="card">
                <h2 style="margin-bottom: 20px;">⚙️ Конфигурация</h2>
                
                <div class="form-group">
                    <label for="packageName">📦 Пакетно име *</label>
                    <input type="text" id="packageName" placeholder="com.example.myapp">
                    <div class="hint">Пример: com.example.mydeviceowner</div>
                </div>
                
                <div class="form-group">
                    <label for="receiverClass">🎯 DeviceAdminReceiver клас *</label>
                    <input type="text" id="receiverClass" placeholder=".DeviceOwnerReceiver">
                    <div class="hint">Включи точката, например: .DeviceOwnerReceiver</div>
                </div>
                
                <div class="form-group">
                    <label for="checksum">🔐 SHA-256 Signature Checksum *</label>
                    <input type="text" id="checksum" placeholder="I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg">
                    <div class="hint">Base64 URL-safe формат</div>
                </div>
                
                <div class="form-group">
                    <label for="apkUrl">🌐 APK Download URL *</label>
                    <input type="url" id="apkUrl" placeholder="https://example.com/app.apk">
                    <div class="hint">Трябва да завършва с .apk</div>
                </div>
                
                <div class="form-group">
                    <label for="wifiSSID">📡 Wyfi SSID (опционално)</label>
                    <input type="text" id="wifiSSID" placeholder="MyNetwork">
                </div>
                
                <div class="form-group">
                    <label for="wifiPassword">🔑 Wyfi Пароля (опционално)</label>
                    <input type="text" id="wifiPassword" placeholder="Password123">
                </div>
                
                <div class="form-group">
                    <label for="skipEncryption">
                        <input type="checkbox" id="skipEncryption" style="width: auto; margin-right: 8px;">
                        Пропусни криптография
                    </label>
                </div>
                
                <div class="form-group">
                    <label for="leaveAllApps">
                        <input type="checkbox" id="leaveAllApps" style="width: auto; margin-right: 8px;">
                        Остави всички system apps активни
                    </label>
                </div>
                
                <button onclick="generateQR()">🚀 Генерирай QR Код</button>
            </div>
            
            <!-- QR Display -->
            <div class="card">
                <h2 style="margin-bottom: 20px;">📊 QR Код</h2>
                
                <div id="qrCode" style="min-height: 300px;"></div>
                
                <button onclick="downloadQR()" style="margin-top: 15px;">⬇️ Свали QR Код (PNG)</button>
                
                <h3 style="margin-top: 30px; margin-bottom: 15px;">📋 JSON Данни:</h3>
                <div id="jsonDisplay" class="json-display"></div>
                
                <button onclick="copyToClipboard()" class="copy-btn">📋 Копирай JSON</button>
            </div>
        </div>
    </div>
    
    <script>
        let qrInstance = null;
        let lastJsonString = '';
        
        function generateQR() {
            const packageName = document.getElementById('packageName').value;
            const receiverClass = document.getElementById('receiverClass').value;
            const checksum = document.getElementById('checksum').value;
            const apkUrl = document.getElementById('apkUrl').value;
            const wifiSSID = document.getElementById('wifiSSID').value;
            const wifiPassword = document.getElementById('wifiPassword').value;
            const skipEncryption = document.getElementById('skipEncryption').checked;
            const leaveAllApps = document.getElementById('leaveAllApps').checked;
            
            // Валидация
            if (!packageName || !receiverClass || !checksum || !apkUrl) {
                alert('⚠️ Попълни всички задължителни полета!');
                return;
            }
            
            // Изграждане на JSON
            const payload = {
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": 
                    packageName + '/' + receiverClass,
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": checksum,
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": apkUrl,
                "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": skipEncryption,
                "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": leaveAllApps,
            };
            
            if (wifiSSID) {
                payload["android.app.extra.PROVISIONING_WIFI_SSID"] = wifiSSID;
                if (wifiPassword) {
                    payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = wifiPassword;
                }
            }
            
            lastJsonString = JSON.stringify(payload, null, 2);
            const jsonCompact = JSON.stringify(payload);
            
            // Покази JSON
            document.getElementById('jsonDisplay').textContent = lastJsonString;
            
            // Очисти стар QR
            document.getElementById('qrCode').innerHTML = '';
            
            // Генерирай нов QR
            qrInstance = new QRCode(document.getElementById('qrCode'), {
                text: jsonCompact,
                width: 300,
                height: 300,
                colorDark: "#000000",
                colorLight: "#ffffff",
                correctLevel: QRCode.CorrectLevel.H
            });
            
            console.log('✅ QR код генериран');
        }
        
        function copyToClipboard() {
            if (!lastJsonString) {
                alert('Първо генерирай QR код!');
                return;
            }
            
            navigator.clipboard.writeText(lastJsonString).then(() => {
                alert('✅ JSON копиран в буфер!');
            });
        }
        
        function downloadQR() {
            if (!qrInstance) {
                alert('Първо генерирай QR код!');
                return;
            }
            
            const canvas = document.querySelector('#qrCode canvas');
            if (canvas) {
                const link = document.createElement('a');
                link.href = canvas.toDataURL('image/png');
                link.download = 'device_owner_qr.png';
                link.click();
            }
        }
        
        // Генерирай QR при Enter
        document.querySelectorAll('input').forEach(input => {
            input.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') generateQR();
            });
        });
    </script>
</body>
</html>
```

Просто отвори этот файл в браузер!

---

## Резюме на скриптовете

| Скрипт | Функция | Платформа |
|--------|---------|-----------|
| `calculate_checksum.py` | Калкулира SHA-256 checksum | Linux/Mac/Windows |
| `generate_qr_code.py` | Генерира QR код от JSON | Linux/Mac/Windows |
| `device_owner_adb.sh` | ADB управление | Linux/Mac |
| `build_and_provision.sh` | Комплетен workflow | Linux/Mac |
| `qr_builder.html` | Интерактивен QR builder | Всички браузери |

---

**Версия:** 1.0  
**Последна актуализация:** November 2025
