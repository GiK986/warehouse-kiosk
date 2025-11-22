#!/bin/bash
# Interactive QR Code Generator Menu
# Позволява избор на локация от меню за генериране на QR код
#
# Usage: ./generate_qr_menu.sh
#
# Функционалност:
# - Показва интерактивно меню с всички налични локации
# - Опция за генериране на QR кодове за ВСИЧКИ локации наведнъж
# - Опция за избор на конкретна локация
# - Автоматично връща към менюто след всяко генериране
# - Exit опция за излизане от скрипта
#
# Изисквания:
# - Python 3
# - qrcode library (pip install qrcode[pil])
# - generate_qr.py в същата директория

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GENERATOR_SCRIPT="$SCRIPT_DIR/generate_qr.py"

# Цветове за output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Helper functions
print_header() {
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}${BLUE}  $1${NC}"
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
    echo -e "${CYAN}ℹ️  $1${NC}"
}

show_help() {
    cat << EOF
${BOLD}${BLUE}QR Code Generator - Interactive Menu${NC}

${BOLD}УПОТРЕБА:${NC}
  ./generate_qr_menu.sh

${BOLD}ФУНКЦИОНАЛНОСТ:${NC}
  • Интерактивно меню с всички налични локации
  • Генериране за ВСИЧКИ локации наведнъж
  • Избор на конкретна локация
  • Автоматично връщане към менюто след генериране

${BOLD}ИЗИСКВАНИЯ:${NC}
  • Python 3
  • qrcode library: pip install qrcode[pil]
  • generate_qr.py в същата директория

${BOLD}ПРИМЕРИ:${NC}
  # Стартиране на интерактивното меню
  ./generate_qr_menu.sh

  # След това избери:
  # 1 - за всички локации
  # 2-N - за конкретна локация
  # Exit - за излизане

EOF
}

# Проверява prerequisites (Python, qrcode library)
check_prerequisites() {
    local has_errors=0

    # Проверка за Python 3
    if ! command -v python3 &> /dev/null; then
        print_error "Python 3 не е намерен!"
        has_errors=1
    fi

    # Проверка за qrcode библиотека
    if ! python3 -c "import qrcode" 2>/dev/null; then
        print_error "qrcode библиотеката не е инсталирана"
        echo "Инсталирай с: pip install qrcode[pil]"
        has_errors=1
    fi

    # Проверка за generate_qr.py
    if [ ! -f "$GENERATOR_SCRIPT" ]; then
        print_error "generate_qr.py не е намерен в $SCRIPT_DIR"
        has_errors=1
    fi

    return $has_errors
}

# Извлича location IDs от generate_qr.py --list-locations
get_available_locations() {
    local locations=()

    # Проверка дали generate_qr.py съществува
    if [ ! -f "$GENERATOR_SCRIPT" ]; then
        print_error "generate_qr.py не е намерен в $SCRIPT_DIR"
        exit 1
    fi

    # Проверка за Python 3
    if ! command -v python3 &> /dev/null; then
        print_error "Python 3 не е намерен!"
        exit 1
    fi

    # Извличане на локации чрез parsing на output
    # Output format:
    #   location-id
    #     Име: Location Name
    #     Warehouse ID: WH_ID

    while IFS= read -r line; do
        # Търси редове които започват с whitespace и са на първо ниво (location ID)
        if [[ $line =~ ^[[:space:]]{2}([a-zA-Z0-9_-]+)$ ]]; then
            location_id="${BASH_REMATCH[1]}"
            locations+=("$location_id")
        fi
    done < <(python3 "$GENERATOR_SCRIPT" --list-locations 2>/dev/null)

    # Връща locations като array (печата ги разделени с whitespace)
    echo "${locations[@]}"
}

# Генерира QR код за конкретна локация
generate_single_qr() {
    local location_id="$1"

    print_info "Генериране на QR код за: $location_id"

    if python3 "$GENERATOR_SCRIPT" --location "$location_id"; then
        print_success "QR код генериран успешно!"
        echo ""
        return 0
    else
        print_error "Грешка при генериране на QR код"
        echo ""
        return 1
    fi
}

# Генерира QR кодове за всички локации
generate_all_qr() {
    print_header "Генериране за ВСИЧКИ локации"

    local locations=($(get_available_locations))
    local generated_count=0
    local failed_count=0

    for location in "${locations[@]}"; do
        print_info "Генериране за: $location"

        if python3 "$GENERATOR_SCRIPT" --location "$location" > /dev/null 2>&1; then
            print_success "  ✓ $location"
            ((generated_count++))
        else
            print_error "  ✗ $location"
            ((failed_count++))
        fi
    done

    echo ""
    print_header "Резюме"
    print_success "Успешно: $generated_count QR кода"

    if [ $failed_count -gt 0 ]; then
        print_error "Неуспешни: $failed_count"
    fi

    echo ""
}

# Main menu loop
main() {
    # Проверка на prerequisites
    if ! check_prerequisites; then
        print_error "Моля инсталирай липсващите dependencies"
        exit 1
    fi

    while true; do
        print_header "QR Code Generator - Interactive Menu"

        # Извличане на налични локации
        locations=($(get_available_locations))

        if [ ${#locations[@]} -eq 0 ]; then
            print_error "Няма намерени локации!"
            exit 1
        fi

        # Изграждане на меню опции
        menu_options=("🌍 Генерирай за ВСИЧКИ локации")

        for loc in "${locations[@]}"; do
            menu_options+=("📍 $loc")
        done

        menu_options+=("❌ Exit")

        # Показване на меню
        echo "Моля избери локация:"
        echo ""

        PS3=$'\n'"👉 Избор (номер): "

        select choice in "${menu_options[@]}"; do
            case $REPLY in
                1)
                    # Генериране за всички
                    generate_all_qr
                    break
                    ;;
                $((${#menu_options[@]})))
                    # Exit
                    print_info "Довиждане!"
                    exit 0
                    ;;
                *)
                    # Конкретна локация
                    # Изчисляваме индекс в locations array (REPLY - 2, защото:
                    # 1 = "All", 2-N = locations, N+1 = "Exit")
                    if [ "$REPLY" -ge 2 ] && [ "$REPLY" -lt ${#menu_options[@]} ]; then
                        location_index=$((REPLY - 2))
                        selected_location="${locations[$location_index]}"
                        generate_single_qr "$selected_location"
                        break
                    else
                        print_error "Невалиден избор!"
                        break
                    fi
                    ;;
            esac
        done

        echo ""
    done
}

# Parse command line arguments
if [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    show_help
    exit 0
fi

# Run main
main
