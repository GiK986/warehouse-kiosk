# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow Requirements
- Update CLAUDE.md before every git commit
- Follow semantic versioning for releases
- Always include release notes in GitHub releases

## Project Overview

Android Kiosk application designed to run as a Device Owner on Android devices, providing a locked-down launcher environment for warehouse operations. The app supports QR code provisioning, manages device policies for kiosk mode, and includes WMS app installation capabilities.

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

### QR Code Provisioning Fields

The QR code can include the following fields:
- `location_name` - Warehouse location name (auto-saved to preferences)
- `location_wifi` - WiFi configuration (SSID, password, security type)
- `wms_apk_url` - URL to WMS app APK (auto-downloaded and installed, URL auto-saved for later use)

## Architecture

### Clean Architecture Layers

The app follows Clean Architecture with clear separation:

```
presentation/     - Jetpack Compose UI and ViewModels
  ├── launcher/        - Main kiosk launcher screen (shows enabled apps)
  ├── admin/           - Admin hub for configuration access
  ├── app_selection/   - Enable/disable apps for kiosk mode
  ├── app_updates/     - Check and install app updates
  ├── kiosk_settings/  - Kiosk mode on/off toggle
  ├── auto_start/      - Configure app auto-launch on kiosk start
  ├── device_info/     - Display device and app information
  ├── password/        - Admin password dialog
  ├── wms_install/     - WMS app installation with saved URLs
  └── navigation/      - NavHost and route definitions

domain/           - Business logic and use cases
  ├── manager/         - DeviceOwnerManager, KioskSetupCoordinator
  ├── model/           - Data models (SavedApkUrl)
  └── usecase/         - Use cases:
      ├── GetInstalledAppsUseCase - Get all launchable apps
      ├── RefreshInstalledAppsUseCase - Add new apps to database
      ├── DownloadAndInstallApkUseCase - Download and install APKs
      └── SetWallpaperUseCase - Set device wallpaper from drawable resource

data/             - Data layer
  ├── model/           - Data transfer objects
  ├── repository/      - Repositories (KioskRepository, ApkRepository, UpdateRepository)
  ├── database/        - Room database (AppEntity, AppDao, KioskDatabase)
  └── preferences/     - DataStore (KioskPreferences for encrypted storage)

services/         - Android system services
  ├── DeviceOwnerReceiver    - Device admin receiver for provisioning
  ├── BootReceiver           - Boot event handler for auto-start
  ├── OverlayService         - Overlay management for blocking UI
  └── InstallStatusReceiver  - APK installation status callback

di/               - Dependency injection modules
  ├── DatabaseModule         - Provides Room database and DAOs
  └── PreferencesModule      - Provides DataStore preferences
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
  - Sets as default launcher
  - Configures WiFi network (if provided)
  - Saves location name
  - Downloads and installs WMS app (if `wms_apk_url` provided)
  - Auto-saves WMS URL for later reinstallation
- `MainActivity` - Sets kiosk policies and manages lock task mode

**Device Owner capabilities:**
- Set persistent preferred launcher (make app the home screen)
- Enable lock task mode (prevent exiting the app)
- Manage lock task features (HOME button, Recent apps, etc.)
- Configure allowed apps in kiosk mode
- Silent APK installation without user interaction

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

**Saved Data in DataStore:**
- `kiosk_mode_active` - Boolean for kiosk mode state
- `initial_setup_completed` - Boolean for first-time setup
- `password_hash` - SHA-256 hashed admin password
- `auto_start_app_package` - Package name of app to auto-launch
- `staff_name` - Staff member name (future use)
- `location_name` - Warehouse location from provisioning
- `saved_apk_urls` - Set of serialized SavedApkUrl objects
- `save_url_enabled` - Boolean toggle for auto-saving URLs

**ViewModels**: Each screen has a ViewModel that observes repository flows and exposes UI state.

### WMS App Installation Feature

The app supports downloading and installing WMS applications via URL with the following features:

**Saved URLs System:**
- Users can save APK URLs for later reuse via toggle switch
- Saved URLs are stored in DataStore with package name and display name
- URLs from QR code provisioning (`wms_apk_url`) are automatically saved after successful installation
- List of saved URLs displayed as clickable cards with package name and delete button
- Click on saved URL card to load it into the input field

**Installation Flow:**
1. User enters APK URL (or selects from saved URLs list)
2. Optional: Enable "Save URL" toggle to save after installation
3. Click "Download and Install" button
4. Show progress: Downloading... X%
5. Extract package name from downloaded APK file
6. Show progress: Installing...
7. Silent installation using Device Owner permissions
8. On success:
   - Save URL to favorites (if toggle enabled or from provisioning)
   - Refresh app list (adds only NEW apps)
   - Clear URL input field
   - Show success message with package name
9. On error: Show error message with details

**Important Behaviors:**
- `RefreshInstalledAppsUseCase` only adds NEW apps to the database to preserve the `isEnabled` state of existing apps
- This prevents resetting previously enabled apps when installing new ones
- Package name extraction happens before installation for accurate tracking
- Success state includes package name for confirmation and saving

### Wallpaper Feature

The app can set `wallpaper.jpg` from drawable resources as the device wallpaper for both Home Screen and Lock Screen.

**Current Implementation:**
- Admin panel button "Задай тапет" for manual wallpaper setting
- Uses `SetWallpaperUseCase` to load and apply the wallpaper
- Sets both Home Screen (`FLAG_SYSTEM`) and Lock Screen (`FLAG_LOCK`) wallpapers
- Snackbar feedback on success or error
- Requires `SET_WALLPAPER` permission (declared in AndroidManifest.xml)

**How it works:**
1. User clicks "Задай тапет" button in Admin screen
2. `SetWallpaperUseCase` loads `R.drawable.wallpaper`
3. Converts drawable to Bitmap
4. Applies to Home Screen via `WallpaperManager.setBitmap()`
5. Applies to Lock Screen (API 24+)
6. Shows success/error Snackbar message

**Future enhancements:**
- Automatic wallpaper setting during QR provisioning
- Custom wallpaper upload via Admin panel
- Wallpaper selection from multiple presets

### Security

- Admin password is hashed using `MessageDigest` (SHA-256)
- Preferences stored using `EncryptedSharedPreferences` (via DataStore)
- Password dialog required to access admin settings
- Password validation before any admin operations

## Key Behaviors

### First Launch & Setup Flow

1. **After QR Provisioning**: `DeviceOwnerReceiver.onProfileProvisioningComplete()` → `ProvisioningCompleteActivity` → `MainActivity` with `from_provisioning=true`
2. **After ADB Setup**: Direct launch to `MainActivity`, detects first-time Device Owner
3. **Normal Restart**: `MainActivity` checks if kiosk mode is active and reapplies policies

### Kiosk Mode Lifecycle

- **Entering Kiosk Mode**: Calls `startLockTask()`, restricts navigation
- **Exiting Kiosk Mode**: Calls `stopLockTask()`, restores normal Android behavior
- **App Selection**: Enabled apps are added to lock task packages dynamically
- **Auto-Start**: If configured, automatically launches selected app on kiosk screen after entering kiosk mode

### Navigation Flow

Single-activity architecture with Jetpack Compose Navigation:

```
LauncherScreen (Kiosk Mode Active)
    ↓ (Exit Kiosk Mode)
LauncherScreen (Normal Mode)
    ↓ (Enter Admin Password)
AdminScreen (Hub)
    ├── App Selection (Enable/Disable apps)
    ├── Kiosk Settings (On/Off toggle)
    ├── Auto-Start (Select default app)
    ├── WMS Install (Download/Install apps)
    ├── App Updates (Check for updates)
    ├── Set Wallpaper (Set device wallpaper)
    └── Device Info (System information)
```

## Device Configuration

### AndroidManifest.xml

- `MainActivity` has `CATEGORY_HOME` intent filter (launcher replacement)
- `DeviceOwnerReceiver` requires `BIND_DEVICE_ADMIN` permission
- Critical permissions:
  - `RECEIVE_BOOT_COMPLETED` - Auto-start on device boot
  - `QUERY_ALL_PACKAGES` - List all installed apps
  - `DISABLE_KEYGUARD` - Disable lock screen in kiosk mode
  - `INTERNET` - Download APKs
  - `SET_WALLPAPER` - Set device wallpaper
  - `SYSTEM_ALERT_WINDOW` - Overlay service

### Required Permissions for Device Owner

Device Owner apps receive special permissions during provisioning:
- `DOWNLOAD_WITHOUT_NOTIFICATION` - Silent APK downloads
- `GET_ACCOUNTS`, `MANAGE_ACCOUNTS` - Account management
- `INSTALL_PACKAGES` - Silent app installation

## Version Information

- **Current Version**: 1.1.3 (versionCode 13)
- **Min SDK**: 31 (Android 12)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 36

### Recent Changes

**v1.1.3** (2025-12-03) - Current
- 🐛 Fixed: App enabled state preserved when refreshing installed apps list
- RefreshInstalledAppsUseCase now only adds NEW apps to prevent resetting enabled states

**v1.1.2** (2025-12-03)
- ✨ Added saved URLs feature for WMS app installation
- Toggle to save APK URLs for later reuse
- Display list of saved URLs with package names and click-to-load
- Auto-save URLs from QR code provisioning (`wms_apk_url`)
- Auto-refresh app list after successful installation
- Auto-clear URL field after successful installation
- Improved success message UI with better layout

**v1.1.1**
- 🐛 Fixed: Made provisioning screen scrollable to show Continue button

**v1.1.0**
- ✨ Added app update checking and installation feature
- Auto-save location name from provisioning QR code

**v1.0.9**
- ✨ Initial app selection and kiosk mode features

## Technology Stack

- **Language**: Kotlin 2.2.21
- **UI**: Jetpack Compose (BOM 2025.10.01)
- **DI**: Hilt 2.57.2
- **Database**: Room with KSP
- **Async**: Kotlin Coroutines + Flow
- **Build**: Gradle 8.13.1 with Kotlin DSL
- **Serialization**: Kotlinx Serialization (for SavedApkUrl storage)

## Common Development Tasks

### Adding a New Screen

1. Create screen composable in `presentation/[feature]/`
2. Create ViewModel extending `ViewModel`
3. Add route constant to `AppDestinations`
4. Add `composable()` block to `AppNavigation`
5. Wire navigation callbacks
6. Add navigation from AdminScreen if needed

### Adding a New Preference

1. Add preference key to `KioskPreferences.PrefKeys`
2. Add Flow property to expose the preference
3. Add suspend function to update the preference
4. Add repository method in `KioskRepository`
5. Use in ViewModel with `stateIn()` for UI observation

### Adding a New Use Case

1. Create use case class in `domain/usecase/`
2. Inject required repositories and dependencies
3. Implement `operator fun invoke()` with suspend if needed
4. Add to ViewModel constructor via Hilt
5. Use in ViewModel with proper coroutine scope

### Modifying Kiosk Policies

Edit `MainActivity.setKioskPolicies()` and ensure policies are set when:
- Coming from provisioning (`from_provisioning=true`)
- Kiosk mode is active (normal restart)
- First time as Device Owner (initial setup)

### Updating Provisioning Configuration

1. Edit JSON files in `provisioning/`:
   - `common_config.json` for base config
   - `locations.json` for location-specific settings
   - `wifi_profiles.json` for WiFi networks
2. Rebuild APK if package changes
3. Regenerate QR codes with `./provisioning/generate_qr_menu.sh`
4. Upload new APK to public URL (if using remote provisioning)
5. Update `common_config.json` with new APK URL and checksum

### Testing Device Owner Features

**Without factory reset:**

```bash
# Remove Device Owner (for testing)
adb shell dpm remove-active-admin com.warehouse.kiosk/.DeviceOwnerReceiver

# Set via ADB (requires no accounts on device)
adb shell dpm set-device-owner com.warehouse.kiosk/.DeviceOwnerReceiver
```

**Note**: Production devices should use QR code provisioning during Android setup.

### Creating a Release

1. Update version in `app/build.gradle.kts`:
   - Increment `versionCode`
   - Update `versionName`
2. Update CLAUDE.md with new version and changes
3. Build release APK: `./gradlew clean assembleRelease`
4. Commit changes:
   ```bash
   git add .
   git commit -m "feat: [description]" # or "fix:", "chore:", etc.
   git commit -m "chore: Bump version to X.X.X"
   ```
5. Create and push tag:
   ```bash
   git tag -a vX.X.X -m "Release vX.X.X\n\n[Release notes]"
   git push origin main
   git push origin vX.X.X
   ```
6. Create GitHub release:
   ```bash
   gh release create vX.X.X \
     app/build/outputs/apk/release/warehouse-kiosk-release.apk \
     --title "Release vX.X.X - [Title]" \
     --notes "[Release notes in Bulgarian]"
   ```

## Troubleshooting

### App List Not Updating After Installation
- Check `RefreshInstalledAppsUseCase` is being called
- Verify it's only adding NEW apps (not replacing all)
- Check Room database for duplicate entries

### Kiosk Mode Not Activating
- Verify Device Owner status: `adb shell dpm list-owners`
- Check `setKioskPolicies()` is being called
- Verify enabled apps list is not empty

### WiFi Not Connecting During Provisioning
- Check WiFi security type matches (WPA2/WPA3)
- Verify SSID and password in `wifi_profiles.json`
- Check Android version supports WPA3 (API 29+)

### Saved URLs Not Persisting
- Check DataStore is properly initialized
- Verify JSON serialization is working
- Check for exceptions in `KioskPreferences`

### APK Installation Failing
- Verify Device Owner status
- Check APK URL is accessible
- Verify network connectivity
- Check logcat for detailed error messages