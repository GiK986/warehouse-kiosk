# Interactive QR Generator Menu Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Създаване на интерактивен shell script с меню за генериране на QR кодове - позволява избор на конкретна локация или генериране за всички локации наведнъж.

**Architecture:** Bash script с цикличен меню използващ `select` built-in. Динамично извлича локациите от `generate_qr.py --list-locations`, показва ги в меню и извиква `generate_qr.py` с избраната локация. При избор "All locations" изпълнява масово генериране като `generate_all_qr.sh`.

**Tech Stack:** Bash script, Python 3, qrcode library, existing generate_qr.py

---

## Task 1: Create Basic Script Structure

**Files:**
- Create: `provisioning/generate_qr_menu.sh`

**Step 1: Write basic script skeleton**

Create the file with executable permissions and basic structure:

```bash
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

# Main function placeholder
main() {
    print_header "QR Code Generator - Interactive Menu"
    # TODO: Implementation in next steps
}

# Run main
main
```

**Step 2: Make script executable**

Run: `chmod +x provisioning/generate_qr_menu.sh`
Expected: File permissions changed to executable

**Step 3: Test basic script runs**

Run: `./provisioning/generate_qr_menu.sh`
Expected: Header prints without errors

**Step 4: Commit**

```bash
git add provisioning/generate_qr_menu.sh
git commit -m "feat: add interactive QR menu script skeleton"
```

---

## Task 2: Add Location Extraction Function

**Files:**
- Modify: `provisioning/generate_qr_menu.sh`

**Step 1: Add function to extract locations from generate_qr.py**

Add this function before `main()`:

```bash
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
        if [[ $line =~ ^[[:space:]]{2}([a-z0-9_-]+)$ ]]; then
            location_id="${BASH_REMATCH[1]}"
            locations+=("$location_id")
        fi
    done < <(python3 "$GENERATOR_SCRIPT" --list-locations 2>/dev/null)

    # Връща locations като array (печата ги разделени с whitespace)
    echo "${locations[@]}"
}
```

**Step 2: Test location extraction function**

Add temporary test code to `main()`:

```bash
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
```

**Step 3: Run test**

Run: `./provisioning/generate_qr_menu.sh`
Expected: Prints list of all location IDs (apl-main-wh, voenna-rampa, etc.)

**Step 4: Commit**

```bash
git add provisioning/generate_qr_menu.sh
git commit -m "feat: add location extraction from generate_qr.py"
```

---

## Task 3: Implement Interactive Menu

**Files:**
- Modify: `provisioning/generate_qr_menu.sh`

**Step 1: Replace main() with interactive menu loop**

Replace the entire `main()` function with:

```bash
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
```

**Step 2: Test the interactive menu**

Run: `./provisioning/generate_qr_menu.sh`

Expected output:
- Shows header
- Lists menu options: "🌍 Генерирай за ВСИЧКИ", then all locations, then "❌ Exit"
- Waits for input

Test cases:
1. Enter `1` - should generate all QR codes
2. Enter `2` - should generate QR for first location
3. Enter last number - should exit
4. Enter `999` - should show error

**Step 3: Manual testing checklist**

- [ ] Menu displays correctly with all locations
- [ ] Option 1 generates all QR codes
- [ ] Individual location selection works
- [ ] After generating, menu shows again (loop)
- [ ] Exit option terminates script
- [ ] Invalid input shows error and redisplays menu

**Step 4: Commit**

```bash
git add provisioning/generate_qr_menu.sh
git commit -m "feat: implement interactive menu with location selection"
```

---

## Task 4: Add Prerequisites Validation

**Files:**
- Modify: `provisioning/generate_qr_menu.sh`

**Step 1: Add validation function**

Add this function before `get_available_locations()`:

```bash
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
```

**Step 2: Call validation in main() before loop**

Add at the beginning of `main()`, before `while true; do`:

```bash
main() {
    # Проверка на prerequisites
    if ! check_prerequisites; then
        print_error "Моля инсталирай липсващите dependencies"
        exit 1
    fi

    while true; do
        # ... existing code
```

**Step 3: Test validation**

Test case 1 - Normal case:
Run: `./provisioning/generate_qr_menu.sh`
Expected: Menu shows normally (all prerequisites met)

Test case 2 - Simulate missing qrcode:
```bash
# Временно преименувай generate_qr.py
mv provisioning/generate_qr.py provisioning/generate_qr.py.bak
./provisioning/generate_qr_menu.sh
# Очаквай error message за липсващ generate_qr.py
mv provisioning/generate_qr.py.bak provisioning/generate_qr.py
```

**Step 4: Commit**

```bash
git add provisioning/generate_qr_menu.sh
git commit -m "feat: add prerequisites validation"
```

---

## Task 5: Add Usage Instructions and Documentation

**Files:**
- Modify: `provisioning/generate_qr_menu.sh`

**Step 1: Add help function and usage comments**

Add at the top of the file, after the shebang and description:

```bash
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
```

**Step 2: Add help text display function**

Add after the helper functions:

```bash
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
```

**Step 3: Add --help argument support**

Add before the `main()` function call at the end:

```bash
# Parse command line arguments
if [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    show_help
    exit 0
fi

# Run main
main
```

**Step 4: Test help**

Run: `./provisioning/generate_qr_menu.sh --help`
Expected: Prints help text and exits

Run: `./provisioning/generate_qr_menu.sh -h`
Expected: Same as above

**Step 5: Create README documentation**

Create: `provisioning/README_MENU.md`

```markdown
# Interactive QR Code Generator Menu

Интерактивен shell script за генериране на QR кодове с меню навигация.

## Употреба

```bash
cd provisioning
./generate_qr_menu.sh
```

## Функции

### 1. Генериране за ВСИЧКИ локации
Автоматично генерира QR кодове за всички налични локации от `locations.json`.

### 2. Генериране за конкретна локация
Избираш конкретна локация от списък и генерираш само нейния QR код.

### 3. Цикличен режим
След всяко генериране, скриптът връща към менюто за нов избор.

## Изисквания

- Python 3
- qrcode library: `pip install qrcode[pil]`
- `generate_qr.py` в същата директория
- Конфигурационни файлове в `configs/`:
  - `common_config.json`
  - `locations.json`
  - `wifi_profiles.json`

## Структура на менюто

```
QR Code Generator - Interactive Menu
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Моля избери локация:

1) 🌍 Генерирай за ВСИЧКИ локации
2) 📍 apl-main-wh
3) 📍 apl-main-wh-home
4) 📍 voenna-rampa
5) 📍 lulin
6) 📍 ruse
7) ❌ Exit

👉 Избор (номер):
```

## Изходни файлове

Всички генерирани QR кодове се запазват в:
```
provisioning/qr_codes/<location_id>.png
```

Например:
- `provisioning/qr_codes/apl-main-wh.png`
- `provisioning/qr_codes/voenna-rampa.png`

## Сравнение със съществуващи скриптове

| Feature | generate_qr.py | generate_all_qr.sh | generate_qr_menu.sh |
|---------|---------------|-------------------|-------------------|
| CLI аргументи | ✅ | ✅ | ❌ |
| Интерактивно меню | ❌ | ❌ | ✅ |
| Един QR код | ✅ | ❌ | ✅ |
| Всички QR кодове | ❌ | ✅ | ✅ |
| Цикличен режим | ❌ | ❌ | ✅ |

## Примери

### Генериране за една локация
```
👉 Избор (номер): 2
ℹ️  Генериране на QR код за: apl-main-wh
✅ QR код генериран успешно!
```

### Генериране за всички
```
👉 Избор (номер): 1
Генериране за ВСИЧКИ локации
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ℹ️  Генериране за: apl-main-wh
✅   ✓ apl-main-wh
...
Резюме
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Успешно: 5 QR кода
```
```

**Step 6: Test README renders correctly**

Run: `cat provisioning/README_MENU.md`
Expected: Markdown displays correctly

**Step 7: Commit**

```bash
git add provisioning/generate_qr_menu.sh provisioning/README_MENU.md
git commit -m "docs: add usage instructions and documentation"
```

---

## Task 6: Final Integration Testing

**Files:**
- Test: `provisioning/generate_qr_menu.sh`

**Step 1: Clean QR codes directory**

Run: `rm -f provisioning/qr_codes/*.png`
Expected: All old QR codes deleted

**Step 2: Full workflow test - Generate single location**

Run: `./provisioning/generate_qr_menu.sh`

Test steps:
1. Menu displays
2. Select option `2` (first location)
3. Verify QR code generated in `provisioning/qr_codes/`
4. Verify menu displays again
5. Select "Exit"

Expected:
- QR code file exists
- Script exits cleanly

**Step 3: Full workflow test - Generate all locations**

Run: `./provisioning/generate_qr_menu.sh`

Test steps:
1. Select option `1` (all locations)
2. Verify all QR codes generated

Run: `ls -la provisioning/qr_codes/`
Expected: One PNG file per location (5 files)

**Step 4: Test error handling**

Run: `./provisioning/generate_qr_menu.sh`

Test steps:
1. Enter invalid number `999`
2. Verify error message shows
3. Verify menu redisplays

**Step 5: Final checklist**

Verify all requirements:
- [ ] Menu shows "Всички локации" as first option
- [ ] All locations from `--list-locations` appear in menu
- [ ] Generating single location works and returns to menu
- [ ] Generating all locations works
- [ ] Exit option works
- [ ] Invalid input handled gracefully
- [ ] Help text displays with `--help`
- [ ] Script is executable
- [ ] QR codes saved with correct naming: `<location_id>.png`
- [ ] Documentation complete and accurate

**Step 6: Commit**

```bash
git add -A
git commit -m "test: verify complete QR menu functionality"
```

---

## Task 7: Add to Main Provisioning Documentation

**Files:**
- Modify: `provisioning/README.md` (if exists)

**Step 1: Check if main README exists**

Run: `ls -la provisioning/README.md`

If exists, add section:

```markdown
## QR Code Generation Methods

### Method 1: Interactive Menu (Recommended for manual use)

```bash
./generate_qr_menu.sh
```

Интерактивно меню с избор на локация.

### Method 2: Command Line (Single location)

```bash
python3 generate_qr.py --location <location_id>
```

### Method 3: Batch Generation (All locations)

```bash
./generate_all_qr.sh
```

See [README_MENU.md](./README_MENU.md) for detailed menu documentation.
```

**Step 2: If README.md doesn't exist, skip**

Run: `[ -f provisioning/README.md ] && echo "exists" || echo "skip this task"`

**Step 3: Commit if modified**

```bash
git add provisioning/README.md
git commit -m "docs: add QR menu to main provisioning README"
```

---

## Verification Checklist

Use @superpowers:verification-before-completion before claiming complete.

Run these commands to verify everything works:

```bash
# 1. Script is executable
test -x provisioning/generate_qr_menu.sh && echo "✅ Executable" || echo "❌ Not executable"

# 2. Help works
./provisioning/generate_qr_menu.sh --help | grep -q "УПОТРЕБА" && echo "✅ Help works" || echo "❌ Help broken"

# 3. Location extraction works
./provisioning/generate_qr_menu.sh <<< "7" | grep -q "apl-main-wh" && echo "✅ Locations found" || echo "❌ No locations"

# 4. Prerequisites check works
./provisioning/generate_qr_menu.sh <<< "7" > /dev/null 2>&1 && echo "✅ Prerequisites OK" || echo "❌ Prerequisites fail"

# 5. QR generation works
rm -f provisioning/qr_codes/test-location.png
./provisioning/generate_qr_menu.sh <<< $'2\n7' > /dev/null 2>&1
test -f provisioning/qr_codes/*.png && echo "✅ QR generated" || echo "❌ No QR file"

# 6. Documentation exists
test -f provisioning/README_MENU.md && echo "✅ Documentation exists" || echo "❌ No docs"
```

All checks must pass before considering the task complete.

---

## Notes

- **DRY Principle**: Reuses existing `generate_qr.py` - no duplicate logic
- **YAGNI**: Simple menu implementation, no unnecessary features
- **Error Handling**: Validates prerequisites before running
- **User Experience**: Colorful output, clear menu structure, loop for multiple operations
- **Documentation**: Clear README and inline help

## Related Files

- `provisioning/generate_qr.py` - Core QR generation logic
- `provisioning/generate_all_qr.sh` - Batch generation reference
- `provisioning/configs/locations.json` - Location definitions
