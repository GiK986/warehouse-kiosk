# AndroidManifest.xml - ProvisioningCompleteActivity Пълно Обяснение

## 📝 Какво е AndroidManifest.xml?

AndroidManifest.xml е конфигурационния файл на Android приложението. Там задаваме:
- Какви активности има приложението
- Какви permissions трябват
- Какви Intent-и слушаме
- Защитни ограничения

**Местоположение:** `app/src/main/AndroidManifest.xml`

---

## 🔍 Декларацията в детайли

### Цялата декларация:

```xml
<activity
    android:name=".ProvisioningCompleteActivity"
    android:exported="true"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    
    <intent-filter>
        <action android:name="android.app.action.ADMIN_POLICY_COMPLIANCE" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    
</activity>
```

---

## 🎯 Всеки атрибут обяснен

### 1. `android:name=".ProvisioningCompleteActivity"`

```
Какво е: Името на Android Activity класа
Стойност: ".ProvisioningCompleteActivity"

Обяснение:
────────────────────────────────────────────────────────
"." означава че класа е в пакетното пространство на приложението

Ако package е: com.example.mydeviceowner
И използваме: .ProvisioningCompleteActivity

Тогава пълното име е: com.example.mydeviceowner.ProvisioningCompleteActivity
────────────────────────────────────────────────────────

Без ".":
<activity android:name="com.example.mydeviceowner.ProvisioningCompleteActivity">
```

**Пример:**
```
Приложение пакет:           com.example.mydeviceowner
Activity име:               .ProvisioningCompleteActivity
Пълно име:                  com.example.mydeviceowner.ProvisioningCompleteActivity
Java класа местоположение: app/src/main/java/com/example/mydeviceowner/ProvisioningCompleteActivity.kt
```

---

### 2. `android:exported="true"`

```
Какво е: Видимост на активността
Стойност: true (видима) или false (скрита)

ВАЖНО ЗА PROVISIONING!
```

#### Обяснение:

```
exported="true" означава:
├─ Други приложения могат да стартират тази активност
├─ Системата може да праща Intent-и към тази активност
├─ ВАЖНО: Системата може да стартира ProvisioningCompleteActivity
│  чрез Intent със action="android.app.action.ADMIN_POLICY_COMPLIANCE"
└─ Необходимо е за Device Owner provisioning!

exported="false" означава:
├─ САМО нашето приложение може да стартира активността
├─ Други приложения не могат
├─ Системата НЕ ще найде ProvisioningCompleteActivity
└─ Provisioning ще ПРОВАЛИ!
```

#### Визуално:

```
Системата:
  "Нужна ми е активност със action=ADMIN_POLICY_COMPLIANCE"
    ↓
  Търсене в приложенията...
    ↓
  Намиране: ProvisioningCompleteActivity (exported="true") ✓
    ↓
  Праща Intent към активността
    ↓
  onCreate() е извикана в ProvisioningCompleteActivity
```

#### Важни точки:

```
✓ За ProvisioningCompleteActivity: ТРЯБВА true
✓ За MainActivity: Може да е true или false
✗ За някои защитени активности: false е по-безопасно
```

---

### 3. `android:permission="android.permission.BIND_DEVICE_ADMIN"`

```
Какво е: Ограничение на достъп чрез permission
Стойност: android.permission.BIND_DEVICE_ADMIN
```

#### Обяснение:

```
Това атрибутизирана ЗАЩИТА:

"Само приложения които ИМАТ android.permission.BIND_DEVICE_ADMIN
могат да СТАРТИРАТ тази активност"

Кой има този permission?
├─ Android система           ✓ (всички системни приложения)
├─ Нашето DPC приложение    ✓ (защото е Device Admin)
├─ Google Play Services     ✓ (системни услуги)
├─ Други приложения         ✗ (нямат разрешение!)
└─ Браузър                   ✗ (нямат разрешение!)
```

#### Защо е нужна?

```
СЦЕНАРИЙ БЕЗ ЗАЩИТА:
────────────────────

Злобно приложение:
  Intent intent = new Intent("android.app.action.ADMIN_POLICY_COMPLIANCE")
  startActivity(intent)  ← Стартира нашата активност!
  
  Проблем: Може да манипулира provisioning процеса!


СЦЕНАРИЙ С ЗАЩИТА:
──────────────────

Злобно приложение:
  Intent intent = new Intent("android.app.action.ADMIN_POLICY_COMPLIANCE")
  startActivity(intent)  ← Не работи! ❌
  
  Резултат: 
  android.util.AndroidException: 
  Permission Denial: starting Intent ... requires 
  android.permission.BIND_DEVICE_ADMIN
  
  ✓ ЗАЩИТЕНО!
```

---

### 4. `<intent-filter>`

```
Какво е: Дефиниция на Intent действия което активността слушa
Цел: Казва на системата "тази активност интересна е от следните Intent-и"
```

#### Как работи:

```
ПРИМЕР:
───────

1. Системата искает активност със:
   - action = "android.app.action.ADMIN_POLICY_COMPLIANCE"
   - category = "android.intent.category.DEFAULT"

2. Системата търси в всички приложения:
   
   Проверка App 1:
   ├─ <intent-filter>
   │  ├─ action = "android.intent.action.MAIN" → Не съвпада
   │  └─ category = "android.intent.category.LAUNCHER"
   └─ Не е подходяща
   
   Проверка App 2:
   ├─ <intent-filter>
   │  ├─ action = "android.app.action.ADMIN_POLICY_COMPLIANCE" → Съвпада ✓
   │  └─ category = "android.intent.category.DEFAULT" → Съвпада ✓
   └─ НАМЕРЕНА! Тази активност е подходяща!

3. Системата стартира ProvisioningCompleteActivity
```

---

### 5. `<action android:name="android.app.action.ADMIN_POLICY_COMPLIANCE" />`

```
Какво е: Конкретното ДЕЙСТВИЕ което слушаме
Стойност: "android.app.action.ADMIN_POLICY_COMPLIANCE"
```

#### Обяснение на значението:

```
ADMIN_POLICY_COMPLIANCE
│
├─ "ADMIN" → Администраторски / Device Owner
├─ "POLICY" → Политика на устройството
├─ "COMPLIANCE" → Съответствие / Завършване
│
Összesen = "Администраторската политика е завършена"
           или "Provisioning е успешен"
```

#### Кога го праща системата?

```
СЛУЧАЙ 1: След Device Owner provisioning
──────────────────────────────────────────

Потребител сканира QR код
         ↓
Система инсталира DPC APK
         ↓
Система изпълнява DeviceOwnerReceiver.onProfileProvisioningComplete()
         ↓
Система праща Intent: action = "android.app.action.ADMIN_POLICY_COMPLIANCE"
         ↓
ProvisioningCompleteActivity се стартира!


СЛУЧАЙ 2: При промяна на Device Owner политики (редко)
──────────────────────────────────────────────────────

DPC приложение промени политика чрез DevicePolicyManager
         ↓
Система праща ADMIN_POLICY_COMPLIANCE Intent като потвърждение
         ↓
ProvisioningCompleteActivity обработва промяната
```

#### Intent-ът съдържа:

```kotlin
// В ProvisioningCompleteActivity.handleProvisioningIntent()

val intent: Intent = ... // Получаваме от Intent-а

val action = intent.action
// action = "android.app.action.ADMIN_POLICY_COMPLIANCE"

val extras = intent.extras
// extras може да съдържа:
// - "android.intent.extra.STATUS" (дали е успешно)
// - "android.intent.extra.PACKAGE_NAME" (кое приложение)
// - "android.intent.extra.SESSION_ID" (ID на sessio)
```

---

### 6. `<category android:name="android.intent.category.DEFAULT" />`

```
Какво е: КАТЕГОРИЯ на Intent-а
Значение: Тип на Intent (explicit vs implicit)
```

#### Обяснение:

```
DEFAULT категория важна за IMPLICIT Intent-и

ВИДОВЕ INTENT-И:
─────────────────

1. EXPLICIT Intent:
   Intent intent = new Intent(this, ProvisioningCompleteActivity.class)
   ├─ Точно определена целева активност
   ├─ Не се нуждае от intent-filter
   └─ Системата знае точно кой Activity
   
   └─ Може да работи и БЕЗ DEFAULT категория

2. IMPLICIT Intent:
   Intent intent = new Intent("android.app.action.ADMIN_POLICY_COMPLIANCE")
   ├─ НЕ определена целева активност
   ├─ Системата трябва да намери подходящия Activity
   └─ Търси по action + категория
   
   └─ ТРЯБВА DEFAULT категория!
```

#### Защо DEFAULT?

```
Без DEFAULT категория:
───────────────────────

<intent-filter>
    <action android:name="android.app.action.ADMIN_POLICY_COMPLIANCE" />
    <!-- БЕЗ DEFAULT категория! -->
</intent-filter>

Системата праща Implicit Intent:
Intent intent = new Intent("android.app.action.ADMIN_POLICY_COMPLIANCE")
       ↓
Системата търси активност със:
├─ action = "android.app.action.ADMIN_POLICY_COMPLIANCE" ✓
└─ category = android.intent.category.DEFAULT ✗
       ↓
РЕЗУЛТАТ: Не намерена! ❌
         android.content.ActivityNotFoundException


С DEFAULT категория:
────────────────────

<intent-filter>
    <action android:name="android.app.action.ADMIN_POLICY_COMPLIANCE" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>

Системата праща Implicit Intent:
Intent intent = new Intent("android.app.action.ADMIN_POLICY_COMPLIANCE")
       ↓
Системата търси активност със:
├─ action = "android.app.action.ADMIN_POLICY_COMPLIANCE" ✓
└─ category = android.intent.category.DEFAULT ✓
       ↓
РЕЗУЛТАТ: Намерена! ✓ ProvisioningCompleteActivity
```

---

## 📊 Таблица - Всички атрибути

| Атрибут | Стойност | Причина |
|---------|----------|---------|
| `android:name` | `.ProvisioningCompleteActivity` | Идентификация на активност |
| `android:exported` | `true` | Системата трябва да намери активност |
| `android:permission` | `android.permission.BIND_DEVICE_ADMIN` | Защита от други приложения |
| **action** | `android.app.action.ADMIN_POLICY_COMPLIANCE` | Какво действие слушаме |
| **category** | `android.intent.category.DEFAULT` | За implicit Intent-и |

---

## 🔄 Пълния поток на provisioning

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. ПОТРЕБИТЕЛ СКАНИРА QR КОД                                  │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│ 2. СИСТЕМА ПРОЧИТА JSON ДАННИ ОТ QR                            │
│    (пакет, checksum, URL)                                       │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│ 3. СИСТЕМА ИЗТЕГЛЯ APK ОТ URL                                  │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│ 4. СИСТЕМА ВЕРИФИЦИРА КОНТРОЛНА СУМА                           │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│ 5. СИСТЕМА ИНСТАЛИРА APK ФАЙЛ                                  │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│ 6. СИСТЕМА СТАРТИРА DeviceOwnerReceiver                        │
│                                                                 │
│    ├─ onProfileProvisioningComplete() е извикана              │
│    │  (в приложението)                                         │
│    │                                                             │
│    └─ Потребител вижда: Стартирал се е DPC приложение        │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│ 7. СИСТЕМА ПРАЩА INTENT                                         │
│    action = "android.app.action.ADMIN_POLICY_COMPLIANCE"      │
│                                                                 │
│    ├─ Търси в приложенията: кой има intent-filter за това?   │
│    │                                                             │
│    ├─ Проверка: ProvisioningCompleteActivity                   │
│    │  ├─ exported = true ? YES ✓                               │
│    │  ├─ име е дефинирано в manifest ? YES ✓                  │
│    │  ├─ intent-filter има action ? YES ✓                     │
│    │  ├─ intent-filter има DEFAULT категория ? YES ✓          │
│    │  └─ permission проверка ? YES ✓ (система има)            │
│    │                                                             │
│    └─ НАМЕРЕНА!                                                 │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│ 8. СИСТЕМА СТАРТИРА ProvisioningCompleteActivity               │
│                                                                 │
│    ├─ onCreate() е извикана                                    │
│    ├─ handleProvisioningIntent() обработва Intent данни       │
│    ├─ updateUI() показва приветствено съобщение              │
│    ├─ setupButtons() активира бутоните                        │
│    └─ applyInitialPolicies() прилага политики                │
│                                                                 │
│    └─ Потребител вижда приветствено съобщение                │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│ 9. ПОТРЕБИТЕЛ НАТИСКА "CONTINUE" BUTTON                       │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│ 10. СТАРТИРА MainActivity                                      │
│                                                                 │
│     └─ Устройството е готово за използване като Device Owner │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔐 Защита - Защо всички атрибути?

```
Сценарий: Злобно приложение опитва да манипулира provisioning

Чегизо е линия защита:

1. exported="true" ✓ Позволява НАМИРАНЕ на активност
   └─ Но има следващи проверки...

2. android:permission ✓ Системата проверява
   "Има ли този app BIND_DEVICE_ADMIN permission?"
   └─ Злобното app НЕ има → БЛОКИРАНО!

3. intent-filter + action ✓ Точна намеса на活動
   └─ Системата намирах точната активност

4. category DEFAULT ✓ Implicit Intent поддържка
   └─ Гарантира че Intent может да намери активност
```

---

## ❓ Всичко ли е нужно?

| Атрибут | Нужен ли? | Защо? |
|---------|----------|-------|
| `android:name` | ✓ ДА | За идентификация |
| `android:exported` | ✓ ДА | За системата да намери |
| `android:permission` | ✓ ДА | За защита |
| `<intent-filter>` | ✓ ДА | За системата да разбере |
| `<action>` | ✓ ДА | За конкретното действие |
| `<category>` | ✓ ДА | За implicit Intent-и |

**Всичко е ВАЖНО за Device Owner provisioning!**

---

## 📚 Справка

**Пълния manifest с всички активности:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.mydeviceowner">

    <!-- PERMISSIONS -->
    <uses-permission android:name="android.permission.DOWNLOAD_WITHOUT_NOTIFICATION" />
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />
    <uses-permission android:name="android.permission.MANAGE_ACCOUNTS" />
    <uses-permission android:name="android.permission.WRITE_SYNC_SETTINGS" />

    <application>

        <!-- DEVICE OWNER RECEIVER (ОБЯЗАТЕЛНА) -->
        <receiver
            android:name=".DeviceOwnerReceiver"
            android:permission="android.permission.BIND_DEVICE_ADMIN"
            android:exported="true">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_owner_receiver" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
            </intent-filter>
        </receiver>

        <!-- PROVISIONING COMPLETE ACTIVITY (ПРЕПОРЪЧАНА) -->
        <activity
            android:name=".ProvisioningCompleteActivity"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <intent-filter>
                <action android:name="android.app.action.ADMIN_POLICY_COMPLIANCE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- MAIN ACTIVITY -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

