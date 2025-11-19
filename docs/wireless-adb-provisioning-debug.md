# Wireless ADB за Provisioning Debugging

## Проблем
При factory reset губим USB ADB връзка и не можем да вземем логове по време на provisioning.

## Решение: Wireless ADB

### Стъпка 1: Setup Wireless ADB (ПРЕДИ factory reset)

```bash
# 1. Свържи устройството с USB
adb devices

# 2. Стартирай ADB на TCP port 5555
adb tcpip 5555

# 3. Виж IP адреса на устройството
adb shell ip addr show wlan0 | grep inet

# Или по-просто:
adb shell ip -f inet addr show wlan0

# EXPECTED OUTPUT:
# inet 192.168.1.XXX/24 brd 192.168.1.255 scope global wlan0

# 4. Запиши IP адреса (например 192.168.1.100)
```

### Стъпка 2: Свържи се Wireless

```bash
# 5. Изключи USB кабела
# 6. Свържи се wireless (замени IP-то с твоето)
adb connect 192.168.1.100:5555

# EXPECTED OUTPUT:
# connected to 192.168.1.100:5555

# 7. Провери връзката
adb devices

# EXPECTED OUTPUT:
# 192.168.1.100:5555    device
```

### Стъпка 3: Започни Logging ПРЕДИ Factory Reset

```bash
# 8. Стартирай logging във файл
adb logcat -v time > provisioning_debug_full.log &

# 9. Сега направи Factory Reset от Settings
# (Wireless ADB ще остане активен ако устройството се свърже към същата WiFi)

# 10. По време на provisioning, logcat ще записва във файл
```

### Стъпка 4: Factory Reset + Provisioning

```
1. Settings → System → Reset → Factory data reset
2. На Welcome screen → 6 тапа
3. Свържи се към СЪЩАТА WiFi мрежа (важно!)
4. Сканирай QR код
5. Изчакай provisioning...
```

### Стъпка 5: Анализ на Логове

```bash
# 11. След provisioning (успешен или не), спри logging
# Ctrl+C или:
pkill -f "adb logcat"

# 12. Анализирай логовете
cat provisioning_debug_full.log | grep -E "DeviceOwnerReceiver|ProvisioningComplete|ManagedProvisioning|ERROR"

# 13. Търси за specific грешки
grep -i "exception\|error\|failed\|crash" provisioning_debug_full.log
```

---

## Алтернатива: Persistent Logging (ако Wireless не работи)

### Метод 1: Logcat Buffer

```bash
# ПРЕДИ factory reset, увеличи logcat buffer
adb root  # Ако имаш root
adb shell setprop persist.logd.size 16M

# След provisioning, извлечи логовете
adb logcat -d > after_provisioning.log
```

### Метод 2: External Storage Logging

Ако нищо друго не работи, може да добавим logging към external storage в кода.

---

## Troubleshooting

**Q: Wireless ADB се disconnects след factory reset**
A: Това е нормално. Устройството трябва да се свърже към същата WiFi за да се възстанови връзката.

**Q: Не мога да видя IP адреса**
A: Отвори Settings → About Phone → Status → IP address

**Q: `adb tcpip 5555` не работи**
A: Уверете се че Developer Options са enabled и USB Debugging е разрешен.
