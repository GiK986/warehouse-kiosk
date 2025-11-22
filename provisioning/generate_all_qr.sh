#!/bin/bash
# Генерира QR кодове за всички локации
# Usage: ./generate_all_qr.sh [apk_path] [apk_url]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Цветове за output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# Параметри
APK_PATH="${1:-}"
APK_URL="${2:-}"
OUTPUT_DIR="${SCRIPT_DIR}/qr_codes"

print_header "QR Code Mass Generator"

# Проверка за Python
if ! command -v python3 &> /dev/null; then
    print_error "Python 3 не е намерен!"
    exit 1
fi

# Проверка за qrcode библиотека
python3 -c "import qrcode" 2>/dev/null || {
    print_error "qrcode библиотеката не е инсталирана"
    echo "Инсталирай с: pip install qrcode[pil]"
    exit 1
}

# Създаване на output директория
mkdir -p "$OUTPUT_DIR"
print_success "Output директория: $OUTPUT_DIR"

# Дефиниране на локации
LOCATIONS=(
    "sofia_central"
    "sofia_west"
    "plovdiv"
    "varna"
    "burgas"
    "mobile_unit_01"
)

# Build APK ако не е подаден път
if [ -z "$APK_PATH" ]; then
    print_info "APK път не е подаден. Искаш ли да build-нем проекта? (y/n)"
    read -r response
    if [ "$response" = "y" ] || [ "$response" = "Y" ]; then
        print_header "Building APK"
        cd "$PROJECT_DIR"
        ./gradlew clean assembleRelease

        # Намиране на APK
        APK_PATH=$(find app/build/outputs/apk/release -name "*-release.apk" -type f | head -1)

        if [ -z "$APK_PATH" ]; then
            print_error "APK не е намерен след build!"
            exit 1
        fi

        print_success "APK намерен: $APK_PATH"
    else
        print_info "Продължаваме БЕЗ checksum калкулиране..."
    fi
fi

# APK URL
if [ -z "$APK_URL" ]; then
    print_info "APK URL не е подаден. Ще използваме placeholder от common_config.json"
fi

# Генериране на QR кодове
print_header "Генериране на QR кодове"

GENERATED_COUNT=0
FAILED_COUNT=0

for location in "${LOCATIONS[@]}"; do
    echo ""
    print_info "Генериране за: $location"

    # Подготовка на команда
    CMD="python3 $SCRIPT_DIR/generate_qr.py --location $location --output $OUTPUT_DIR/qr_${location}.png"

    # Добавяне на APK path ако има
    if [ -n "$APK_PATH" ]; then
        CMD="$CMD --apk $APK_PATH"
    fi

    # Добавяне на APK URL ако има
    if [ -n "$APK_URL" ]; then
        CMD="$CMD --apk-url $APK_URL"
    fi

    # Изпълнение
    if eval "$CMD" > /dev/null 2>&1; then
        print_success "  Генериран: qr_${location}.png"
        ((GENERATED_COUNT++))
    else
        print_error "  Грешка при генериране за $location"
        ((FAILED_COUNT++))
    fi
done

# Резюме
echo ""
print_header "Резюме"
print_success "Успешно генерирани: $GENERATED_COUNT QR кода"

if [ $FAILED_COUNT -gt 0 ]; then
    print_error "Неуспешни: $FAILED_COUNT"
fi

echo ""
print_info "QR кодовете са в: $OUTPUT_DIR"
echo ""
print_info "За да видиш генерираните файлове:"
echo "  ls -lh $OUTPUT_DIR"
echo ""

# Списък на генерираните файлове
if [ -d "$OUTPUT_DIR" ] && [ "$(ls -A $OUTPUT_DIR)" ]; then
    echo "Генерирани файлове:"
    ls -1 "$OUTPUT_DIR" | while read -r file; do
        echo "  - $file"
    done
    echo ""
fi

print_success "Готово!"