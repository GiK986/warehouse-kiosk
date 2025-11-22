#!/bin/bash
# Interactive QR Code Generator Menu
# Позволява избор на локация от меню за генериране на QR код

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

# Main function placeholder
main() {
    print_header "QR Code Generator - Interactive Menu"

    # Test location extraction
    print_info "Testing location extraction..."
    locations=($(get_available_locations))

    if [ ${#locations[@]} -eq 0 ]; then
        print_error "Няма намерени локации!"
        exit 1
    fi

    print_success "Намерени ${#locations[@]} локации:"
    for loc in "${locations[@]}"; do
        echo "  - $loc"
    done
}

# Run main
main
