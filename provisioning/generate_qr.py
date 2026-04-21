#!/usr/bin/env python3
"""
QR Code Generator за Android Device Owner Provisioning
Модулна система с поддръжка на множество локации и WiFi профили
"""

import json
import argparse
import subprocess
import base64
import re
import os
import sys
from pathlib import Path

try:
    import qrcode
except ImportError:
    print("❌ Грешка: qrcode библиотеката не е инсталирана")
    print("Инсталирай с: pip install qrcode[pil]")
    sys.exit(1)


class QRGenerator:
    def __init__(self, config_dir="provisioning"):
        self.base_dir = Path(config_dir)
        self.config_dir = self.base_dir / "configs"
        self.common_config = self.load_json("common_config.json")
        self.wifi_profiles = self.load_json("wifi_profiles.json")
        self.locations = self.load_json("locations.json")

    def load_json(self, filename):
        """Зарежда JSON файл от конфигурационната директория"""
        filepath = self.config_dir / filename
        if not filepath.exists():
            print(f"❌ Файлът {filepath} не съществува!")
            sys.exit(1)

        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)

        # Премахваме коментари (_comment и _instructions), но запазваме _github_repo
        return {k: v for k, v in data.items()
                if not k.startswith('_') or k == '_github_repo'}

    def get_github_latest_release_url(self, repo):
        """Взима URL на APK от последния GitHub release"""
        try:
            import urllib.request
            api_url = f"https://api.github.com/repos/{repo}/releases/latest"

            print(f"🔍 Проверка за нов release в {repo}...")

            with urllib.request.urlopen(api_url) as response:
                import json
                data = json.loads(response.read().decode())

                # Търси APK файл в assets
                for asset in data.get('assets', []):
                    if asset['name'].endswith('.apk'):
                        url = asset['browser_download_url']
                        print(f"✅ Намерен release: {data['tag_name']} - {asset['name']}")
                        return url

                print("❌ Няма APK файл в latest release")
                return None

        except Exception as e:
            print(f"⚠️  Грешка при проверка на GitHub: {e}")
            return None

    def check_and_update_common_config(self):
        """
        Автоматично обновява common_config.json от GitHub latest release
        - Проверява дали има нов release URL
        - Изтегля APK и изчислява checksum
        - Обновява файла само ако е променен
        """
        github_repo = self.common_config.get('_github_repo')
        if not github_repo:
            print("⚠️  Няма _github_repo в common_config.json, skip auto-update")
            return

        current_url = self.common_config.get(
            'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION', ''
        )

        # Взима latest release URL
        latest_url = self.get_github_latest_release_url(github_repo)

        if not latest_url:
            print("⚠️  Не може да се провери GitHub, използвам текущата конфигурация")
            return

        # Сравнява URLs
        if latest_url == current_url:
            print(f"✅ URL е актуален: {current_url}")
            return

        print(f"🔄 Намерен нов release!")
        print(f"   Стар: {current_url}")
        print(f"   Нов: {latest_url}")

        # Изтегля APK временно
        temp_apk = Path("/tmp/warehouse-kiosk-temp.apk")
        try:
            print(f"⬇️  Изтегляне на APK...")
            import urllib.request
            urllib.request.urlretrieve(latest_url, temp_apk)

            # Изчислява checksum
            checksum = self.calculate_checksum(str(temp_apk))

            # Обновява common_config.json
            self.update_common_config_file(latest_url, checksum)

            # Cleanup
            temp_apk.unlink()

            print("✅ common_config.json обновен успешно!")

            # Презарежда конфигурацията
            self.common_config = self.load_json("common_config.json")

        except Exception as e:
            print(f"❌ Грешка при обновяване: {e}")
            if temp_apk.exists():
                temp_apk.unlink()

    def update_common_config_file(self, new_url, new_checksum):
        """Обновява common_config.json с нов URL и checksum"""
        config_path = self.config_dir / "common_config.json"

        with open(config_path, 'r', encoding='utf-8') as f:
            config = json.load(f)

        config['android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION'] = new_url
        config['android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM'] = new_checksum

        with open(config_path, 'w', encoding='utf-8') as f:
            json.dump(config, f, indent=2, ensure_ascii=False)

        print(f"📝 Обновен common_config.json:")
        print(f"   URL: {new_url}")
        print(f"   Checksum: {new_checksum}")

    def calculate_checksum(self, apk_path):
        """Калкулира SHA-256 checksum от APK сигнатурата"""
        print(f"📊 Калкулиране на checksum за: {apk_path}")

        if not Path(apk_path).exists():
            print(f"❌ APK файлът не съществува: {apk_path}")
            sys.exit(1)

        try:
            result = subprocess.run(
                ['apksigner', 'verify', '--print-certs', apk_path],
                capture_output=True,
                text=True,
                check=True
            )

            for line in result.stdout.split('\n'):
                if 'Signer #1 certificate SHA-256' in line:
                    hash_match = re.search(r'SHA-256 digest: ([a-f0-9 ]+)', line)
                    if hash_match:
                        hash_str = hash_match.group(1).replace(' ', '')
                        hash_bytes = bytes.fromhex(hash_str)
                        b64 = base64.b64encode(hash_bytes).decode('ascii')
                        url_safe = b64.replace('+', '-').replace('/', '_').rstrip('=')
                        print(f"✅ Checksum: {url_safe}")
                        return url_safe

            print("❌ Не можах да намеря SHA-256 digest в apksigner output")
            sys.exit(1)

        except FileNotFoundError:
            print("❌ apksigner не е намерен. Инсталирай Android SDK Build Tools")
            sys.exit(1)
        except subprocess.CalledProcessError as e:
            print(f"❌ Грешка при проверка на APK: {e.stderr}")
            sys.exit(1)

    def list_locations(self):
        """Показва всички налични локации"""
        print("\n📍 Налични локации:")
        print("=" * 80)

        for loc_id, loc_data in self.locations["locations"].items():
            wifi_info = loc_data.get("recommended_wifi", "none")
            wifi_name = "БЕЗ WiFi" if wifi_info is None else wifi_info

            print(f"\n  {loc_id}")
            print(f"    Име: {loc_data['name']}")
            print(f"    Warehouse ID: {loc_data['warehouse_id']}")
            print(f"    WiFi: {wifi_name}")
            if loc_data.get('notes'):
                print(f"    Бележка: {loc_data['notes']}")

        print("\n" + "=" * 80)

    def list_wifi_profiles(self):
        """Показва всички налични WiFi профили"""
        print("\n📶 Налични WiFi профили:")
        print("=" * 80)

        for profile_id, profile_data in self.wifi_profiles["profiles"].items():
            print(f"\n  {profile_id}")
            print(f"    Име: {profile_data['name']}")
            print(f"    SSID: {profile_data['android.app.extra.PROVISIONING_WIFI_SSID']}")
            print(f"    Password: {profile_data['android.app.extra.PROVISIONING_WIFI_PASSWORD']}")
            print(f"    Security: {profile_data['android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE']}")

        print("\n" + "=" * 80)

    def generate_qr(self, location_id, wifi_profile_id=None, apk_path=None,
                   apk_url=None, output=None, no_wifi=False,
                   box_size=5):
        """Генерира QR код с избраната конфигурация"""

        # Автоматично проверява и обновява common_config от GitHub
        self.check_and_update_common_config()

        # Създава qr_codes директория ако не съществува (в base_dir, не в config_dir)
        qr_codes_dir = self.base_dir / "qr_codes"
        qr_codes_dir.mkdir(exist_ok=True)

        # Ако няма зададено име, използвай location_id
        if output is None:
            output = location_id

        # Добавя .png разширение ако липсва
        if not output.endswith('.png'):
            output = f"{output}.png"

        # Пълен път към изходния файл (в qr_codes/)
        output_path = qr_codes_dir / output

        # Зарежда базовата конфигурация (без метаданни като _github_repo)
        payload = {k: v for k, v in self.common_config.items()
                   if not k.startswith('_')}

        # Добавя локацията
        if location_id not in self.locations["locations"]:
            print(f"❌ Локация '{location_id}' не съществува!")
            self.list_locations()
            sys.exit(1)

        location = self.locations["locations"][location_id]
        print(f"\n🏢 Локация: {location['name']}")

        # Добавя WiFi ако е нужно
        if not no_wifi:
            # Ако няма избран WiFi профил, използовай препоръчания
            if wifi_profile_id is None:
                wifi_profile_id = location.get("recommended_wifi")

            if wifi_profile_id:
                if wifi_profile_id not in self.wifi_profiles["profiles"]:
                    print(f"❌ WiFi профил '{wifi_profile_id}' не съществува!")
                    self.list_wifi_profiles()
                    sys.exit(1)

                wifi = self.wifi_profiles["profiles"][wifi_profile_id]
                payload.update({
                    k: v for k, v in wifi.items()
                    if k.startswith("android.app.extra.PROVISIONING_WIFI_")
                })
                print(f"📶 WiFi: {wifi['name']} ({wifi['android.app.extra.PROVISIONING_WIFI_SSID']})")
            else:
                print("📶 WiFi: БЕЗ WiFi (мобилна връзка)")
        else:
            print("📶 WiFi: БЕЗ WiFi (изрично изключен)")

        # Калкулира checksum ако е даден APK път
        if apk_path:
            checksum = self.calculate_checksum(apk_path)
            payload["android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"] = checksum

        # Обновява APK URL ако е даден
        if apk_url:
            payload["android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"] = apk_url
            print(f"🌐 APK URL: {apk_url}")

        # добавя location_wifi за локацията ако е зададено
        location_wifi = ""
        location_wifi_id = location.get("location_wifi", None)
        if location_wifi_id:
            if location_wifi_id not in self.wifi_profiles["profiles"]:
                print(f"❌ WiFi профил '{location_wifi_id}' не съществува!")
                self.list_wifi_profiles()
                sys.exit(1)

            location_wifi_data = self.wifi_profiles["profiles"][location_wifi_id]
            location_wifi_json = {
                "wifi_ssid": location_wifi_data["android.app.extra.PROVISIONING_WIFI_SSID"],
                "wifi_password": location_wifi_data["android.app.extra.PROVISIONING_WIFI_PASSWORD"],
                "wifi_security_type": location_wifi_data["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"]
            }
            location_wifi = json.dumps(location_wifi_json)


        # Добавя локационни данни в admin extras
        admin_extras = {
            "warehouse_id": location["warehouse_id"],
            "wms_apk_url": location.get("wms_apk_url", ""),
            "location_name": location["name"],
            "location_wifi": location_wifi

        }

        payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"] = admin_extras

        # Генерира QR код
        print(f"\n🔄 Генериране на QR код...")
        json_string = json.dumps(payload, separators=(',', ':'))

        qr = qrcode.QRCode(
            version=None,
            error_correction=qrcode.constants.ERROR_CORRECT_H,
            box_size=box_size,
            border=4
        )
        qr.add_data(json_string)
        qr.make(fit=True)

        qr_img = qr.make_image(fill_color="black", back_color="white")

        # Добавя заглавие над QR кода
        try:
            from PIL import Image, ImageDraw, ImageFont

            title = location["name"]
            qr_width, qr_height = qr_img.size
            padding = 20
            font_size = max(24, qr_width // 12)

            # Зарежда шрифт
            font = None
            font_paths = [
                "/System/Library/Fonts/Helvetica.ttc",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
            ]
            for fp in font_paths:
                if Path(fp).exists():
                    try:
                        font = ImageFont.truetype(fp, font_size)
                        break
                    except Exception:
                        pass
            if font is None:
                font = ImageFont.load_default()

            # Измерва размера на текста
            dummy = Image.new("RGB", (1, 1))
            draw_dummy = ImageDraw.Draw(dummy)
            bbox = draw_dummy.textbbox((0, 0), title, font=font)
            text_w = bbox[2] - bbox[0]
            text_h = bbox[3] - bbox[1]
            title_block_h = text_h + padding * 2

            # Нова снимка: заглавие отгоре + QR код отдолу
            total_width = max(qr_width, text_w + padding * 2)
            total_height = title_block_h + qr_height
            final_img = Image.new("RGB", (total_width, total_height), "white")

            draw = ImageDraw.Draw(final_img)
            text_x = (total_width - text_w) // 2
            text_y = padding
            draw.text((text_x, text_y), title, fill="black", font=font)

            qr_x = (total_width - qr_width) // 2
            final_img.paste(qr_img.convert("RGB"), (qr_x, title_block_h))
            final_img.save(output_path)
            img = final_img
        except Exception as e:
            print(f"⚠️  Не може да се добави заглавие: {e}")
            qr_img.save(output_path)
            img = qr_img

        # Информация за размера
        width, height = img.size
        file_size = output_path.stat().st_size / 1024  # KB

        print(f"✅ QR код генериран: {output_path}")
        print(f"   Размер: {width}x{height} пиксела (~{file_size:.1f} KB)")
        print(f"\n📋 Конфигурация:")
        print(json.dumps(payload, indent=2, ensure_ascii=False))

        return str(output_path)


def main():
    parser = argparse.ArgumentParser(
        description="QR Code Generator за Android Device Owner Provisioning",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Примери за употреба:

  1. Показване на всички локации:
     python generate_qr.py --list-locations

  2. Показване на всички WiFi профили:
     python generate_qr.py --list-wifi

  3. Генериране за София Централен склад:
     python generate_qr.py --location sofia_central

  4. Генериране за Пловдив с различен WiFi:
     python generate_qr.py --location plovdiv --wifi office_network

  5. Генериране БЕЗ WiFi (мобилна единица):
     python generate_qr.py --location mobile_unit_01 --no-wifi

  6. Генериране с автоматичен checksum:
     python generate_qr.py --location sofia_central --apk ../app/build/outputs/apk/release/warehouse-kiosk-release.apk

  7. Генериране с APK URL:
     python generate_qr.py --location varna --apk-url https://github.com/user/repo/releases/download/v1.0/app.apk

  8. Генериране с персонализиран изход:
     python generate_qr.py --location burgas --output qr_burgas.png
        """
    )

    parser.add_argument('--list-locations', action='store_true',
                       help='Показва всички налични локации')
    parser.add_argument('--list-wifi', action='store_true',
                       help='Показва всички налични WiFi профили')
    parser.add_argument('--location', '-l', type=str,
                       help='Избор на локация (задължително за генериране)')
    parser.add_argument('--wifi', '-w', type=str,
                       help='Избор на WiFi профил (по подразбиране: препоръчания за локацията)')
    parser.add_argument('--no-wifi', action='store_true',
                       help='Генериране БЕЗ WiFi конфигурация')
    parser.add_argument('--apk', type=str,
                       help='Път до APK файл (за автоматичен checksum)')
    parser.add_argument('--apk-url', type=str,
                       help='URL към APK файла за download')
    parser.add_argument('--output', '-o', type=str, default=None,
                       help='Име на изходния QR код файл без разширение (автоматично се добавя .png). По подразбиране: използва името на локацията')
    parser.add_argument('--size', '-s', type=int, default=5,
                       help='Размер на всеки квадратче (box_size). По-малко = по-малък файл. (по подразбиране: 5, препоръчва се: 3-8)')
    parser.add_argument('--config-dir', type=str, default='provisioning',
                       help='Директория с конфигурационни файлове')

    args = parser.parse_args()

    # Инициализира генератора
    generator = QRGenerator(config_dir=args.config_dir)

    # Показва листинги
    if args.list_locations:
        generator.list_locations()
        return

    if args.list_wifi:
        generator.list_wifi_profiles()
        return

    # Валидация за генериране
    if not args.location:
        print("❌ Грешка: --location е задължителен параметър за генериране на QR код")
        print("Използовай --list-locations за да видиш наличните локации")
        parser.print_help()
        sys.exit(1)

    # Генерира QR код
    generator.generate_qr(
        location_id=args.location,
        wifi_profile_id=args.wifi,
        apk_path=args.apk,
        apk_url=args.apk_url,
        output=args.output,
        no_wifi=args.no_wifi,
        box_size=args.size
    )

    print("\n✨ Готово! Използовай QR кода при factory reset на устройството.")
    print("   1. Factory reset")
    print("   2. На Welcome екран тапни 6 пъти")
    print("   3. Сканирай QR кода")


if __name__ == "__main__":
    main()