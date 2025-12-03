# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow Requirements
- Update CLAUDE.md before every git commit

## Project Overview

Android Kiosk application designed to run as a Device Owner on Android devices, providing a locked-down launcher environment for warehouse operations. The app supports QR code provisioning and manages device policies for kiosk mode.

## Build & Development Commands

### Building the APK

```bash
# Clean and build release APK
./gradlew clean assembleRelease

# Build debug version
./gradlew assembleDebug

# The release APK will be output as:
# app/build/outputs/apk/release/warehouse-kiosk-release.apk
```

**Note:** Release builds require `keystore.properties` file with signing credentials.

### Running Tests

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

### Code Quality

```bash
# Run lint checks
./gradlew lint

# Run KSP annotation processing
./gradlew kspDebugKotlin
```

## QR Code Provisioning System

The app uses a modular QR code generation system located in the `provisioning/` directory:

### Generating QR Codes

```bash
# Interactive menu (recommended)
./provisioning/generate_qr_menu.sh

# Single location
python3 provisioning/generate_qr.py --location sofia_central

# With APK checksum calculation
python3 provisioning/generate_qr.py --location sofia_central \
  --apk app/build/outputs/apk/release/warehouse-kiosk-release.apk

# Without WiFi (for mobile units)
python3 provisioning/generate_qr.py --location mobile_unit_01 --no-wifi

# List all locations
python3 provisioning/generate_qr.py --list-locations

# List all WiFi profiles
python3 provisioning/generate_qr.py --list-wifi
```

### Configuration Files

- `provisioning/common_config.json` - Base configuration shared across all locations
- `provisioning/wifi_profiles.json` - WiFi network profiles
- `provisioning/locations.json` - Location-specific settings (warehouse IDs, WiFi mappings)

When adding new locations or updating WiFi credentials, edit the appropriate JSON file and regenerate QR codes.

## Architecture

### Clean Architecture Layers

The app follows Clean Architecture with clear separation:

```
presentation/     - Jetpack Compose UI and ViewModels
  ├── launcher/        - Main kiosk launcher screen
  ├── admin/           - Admin hub for configuration
  ├── app_selection/   - Enable/disable apps for kiosk
  ├── kiosk_settings/  - Kiosk mode on/off
  ├── auto_start/      - Configure app auto-launch
  ├── password/        - Admin password dialog
  ├── wms_install/     - WMS app installation with saved URLs
  └── navigation/      - NavHost and routes

domain/           - Business logic and use cases
  ├── model/           - Data models (SavedApkUrl)
  └── usecase/         - Use cases (GetInstalledAppsUseCase, RefreshInstalledAppsUseCase, DownloadAndInstallApkUseCase)

data/             - Data layer
  ├── repository/      - KioskRepository (single source of truth)
  ├── database/        - Room database (AppEntity, AppDao)
  └── preferences/     - DataStore (encrypted preferences)

services/         - Android system services
  ├── DeviceOwnerReceiver    - Device admin receiver
  ├── BootReceiver           - Boot event handler
  └── OverlayService         - Overlay management

di/               - Dependency injection modules
```

### Dependency Injection

Uses Hilt for DI. Key modules:
- `DatabaseModule` - Provides Room database and DAOs
- `PreferencesModule` - Provides DataStore preferences

The `@HiltAndroidApp` annotation is on `KioskApplication.kt`.

### Device Owner Mode

The app sets itself as Device Owner through:

1. **QR Code Provisioning** (recommended): Scan generated QR code during Android setup
2. **ADB Command** (testing only): `adb shell dpm set-device-owner com.warehouse.kiosk/.DeviceOwnerReceiver`

**Critical Activities:**

- `GetProvisioningModeActivity` - Required for Android 12+ provisioning (handles `GET_PROVISIONING_MODE` action)
- `ProvisioningCompleteActivity` - Handles post-provisioning setup (responds to `ADMIN_POLICY_COMPLIANCE` action)
- `MainActivity` - Sets kiosk policies and manages lock task mode

**Device Owner capabilities:**
- Set persistent preferred launcher (make app the home screen)
- Enable lock task mode (prevent exiting the app)
- Manage lock task features (HOME button, Recent apps, etc.)
- Configure allowed apps in kiosk mode

### Kiosk Policy Management

Policies are set in `MainActivity.setKioskPolicies()`:

1. **Home Launcher**: Sets the app as the default launcher using `addPersistentPreferredActivity()`
2. **Lock Task Features**: Enables HOME, OVERVIEW, SYSTEM_INFO, GLOBAL_ACTIONS
3. **Lock Task Packages**: Restricts which apps can run when kiosk mode is active

The app monitors `KioskRepository.isKioskModeActive` and `getEnabledApps()` to dynamically update lock task packages.

### State Management

**Repository Pattern**: `KioskRepository` provides a single source of truth, combining:
- Room database for app enable/disable state
- DataStore for preferences (kiosk mode, password, auto-start, saved APK URLs)

**ViewModels**: Each screen has a ViewModel that observes repository flows and exposes UI state.

### WMS App Installation Feature

The app supports downloading and installing WMS applications via URL with the following features:

**Saved URLs System:**
- Users can save APK URLs for later reuse via toggle switch
- Saved URLs are stored in DataStore with package name and display name
- URLs from QR code provisioning (`wms_apk_url`) are automatically saved after successful installation
- List of saved URLs displayed as cards with click-to-load and delete functionality

**Installation Flow:**
1. Enter APK URL (or select from saved URLs)
2. Optional: Enable "Save URL" toggle
3. Download APK with progress tracking
4. Extract package name from APK file
5. Silent installation using Device Owner permissions
6. Auto-refresh app list (adds only NEW apps, preserves enabled state)
7. Auto-clear URL field
8. Auto-save URL if toggle enabled

**Important:** `RefreshInstalledAppsUseCase` only adds NEW apps to the database to preserve the `isEnabled` state of existing apps. This prevents resetting previously enabled apps when installing new ones.

### Security

- Admin password is hashed using `MessageDigest` (SHA-256)
- Preferences stored using `EncryptedSharedPreferences` (via DataStore)
- Password dialog required to access admin settings

## Key Behaviors

### First Launch & Setup Flow

1. **After QR Provisioning**: `DeviceOwnerReceiver.onProfileProvisioningComplete()` → `ProvisioningCompleteActivity` → `MainActivity` with `from_provisioning=true`
2. **After ADB Setup**: Direct launch to `MainActivity`, detects first-time Device Owner
3. **Normal Restart**: `MainActivity` checks if kiosk mode is active and reapplies policies

### Kiosk Mode Lifecycle

- **Entering Kiosk Mode**: Calls `startLockTask()`, restricts navigation
- **Exiting Kiosk Mode**: Calls `stopLockTask()`, restores normal Android behavior
- **App Selection**: Enabled apps are added to lock task packages dynamically
- **Auto-Start**: If configured, automatically launches selected app on kiosk screen

### Navigation

Single-activity architecture with Jetpack Compose Navigation:
- Start: `LauncherScreen` (shows enabled apps)
- Password dialog → `AdminScreen` (hub) → sub-screens (AppSelection, KioskSettings, AutoStart)

## Device Configuration

### AndroidManifest.xml

- `MainActivity` has `CATEGORY_HOME` intent filter (launcher replacement)
- `DeviceOwnerReceiver` requires `BIND_DEVICE_ADMIN` permission
- Critical permissions: `RECEIVE_BOOT_COMPLETED`, `QUERY_ALL_PACKAGES`, `DISABLE_KEYGUARD`

### Required Permissions for Device Owner

Device Owner apps need special permissions that are granted during provisioning:
- `DOWNLOAD_WITHOUT_NOTIFICATION`
- `GET_ACCOUNTS`, `MANAGE_ACCOUNTS`
- `INSTALL_PACKAGES` (for app management)

## Version Information

- **Current Version**: 1.1.3 (versionCode 13)
- **Min SDK**: 31 (Android 12)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 36

### Recent Changes

**v1.1.3** (Current)
- Fixed: App enabled state preserved when refreshing installed apps list
- RefreshInstalledAppsUseCase now only adds NEW apps

**v1.1.2**
- Added saved URLs feature for WMS app installation
- Toggle to save APK URLs for later reuse
- Display list of saved URLs with package names
- Auto-save URLs from QR code provisioning
- Auto-refresh app list after successful installation
- Improved success message UI

**v1.1.1**
- Made provisioning screen scrollable to show Continue button

**v1.1.0**
- Added app update checking and installation feature
- Auto-save location name from provisioning QR code

## Technology Stack

- **Language**: Kotlin 2.2.21
- **UI**: Jetpack Compose (BOM 2025.10.01)
- **DI**: Hilt 2.57.2
- **Database**: Room with KSP
- **Async**: Kotlin Coroutines + Flow
- **Build**: Gradle 8.13.1 with Kotlin DSL
- **Serialization**: Kotlinx Serialization

## Common Development Tasks

### Adding a New Screen

1. Create screen composable in `presentation/[feature]/`
2. Create ViewModel extending `ViewModel`
3. Add route constant to `AppDestinations`
4. Add `composable()` block to `AppNavigation`
5. Wire navigation callbacks

### Modifying Kiosk Policies

Edit `MainActivity.setKioskPolicies()` and ensure policies are set when:
- Coming from provisioning (`from_provisioning=true`)
- Kiosk mode is active (normal restart)
- First time as Device Owner (initial setup)

### Updating Provisioning Configuration

1. Edit JSON files in `provisioning/`
2. Rebuild APK if package changes
3. Regenerate QR codes with new configuration
4. Upload new APK to public URL (if using remote provisioning)
5. Update `common_config.json` with new APK URL and checksum

### Testing Device Owner Features

Without factory reset:

```bash
# Remove Device Owner (for testing)
adb shell dpm remove-active-admin com.warehouse.kiosk/.DeviceOwnerReceiver

# Set via ADB (requires no accounts on device)
adb shell dpm set-device-owner com.warehouse.kiosk/.DeviceOwnerReceiver
```

**Note**: Production devices should use QR code provisioning during Android setup.