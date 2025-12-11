# Wallpaper Feature Design

**Date:** 2025-12-11
**Feature:** Set device wallpaper from drawable resource

## Overview

Add functionality to set `wallpaper.jpg` as both Home Screen and Lock Screen wallpaper on kiosk devices. Initial implementation will be through Admin panel, with future expansion to automatic provisioning.

## User Flow

1. User navigates to Admin screen (after password entry)
2. User clicks "Задай тапет" button
3. App sets `app/src/main/res/drawable/wallpaper.jpg` as wallpaper
4. Snackbar shows "Тапетът е зададен успешно"
5. Wallpaper is applied to both Home and Lock screens

## Architecture

### UI Layer (Presentation)

**File:** `app/src/main/java/com/warehouse/kiosk/presentation/admin/AdminScreen.kt`
- Add new `AdminOptionCard` with:
  - Title: "Задай тапет"
  - Description: "Задай wallpaper.jpg като тапет на устройството"
  - Icon: `Icons.Default.Wallpaper` (or similar)
  - onClick: calls `viewModel.setWallpaper()`
- Add `SnackbarHost` with `snackbarHostState`
- Add `LaunchedEffect` to observe `wallpaperState` and show Snackbar messages

**File:** `app/src/main/java/com/warehouse/kiosk/presentation/admin/AdminViewModel.kt`
- Inject `SetWallpaperUseCase`
- Add `wallpaperState: StateFlow<WallpaperState>`
- Add `setWallpaper()` method that:
  - Sets state to `Loading`
  - Calls `setWallpaperUseCase()`
  - Sets state to `Success` or `Error(message)`
- Add `resetWallpaperState()` to reset to `Idle` after showing Snackbar

**State Model:**
```kotlin
sealed class WallpaperState {
    object Idle : WallpaperState()
    object Loading : WallpaperState()
    object Success : WallpaperState()
    data class Error(val message: String) : WallpaperState()
}
```

### Domain Layer

**File:** `app/src/main/java/com/warehouse/kiosk/domain/usecase/SetWallpaperUseCase.kt`

```kotlin
class SetWallpaperUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun invoke(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val drawable = context.getDrawable(R.drawable.wallpaper)
            val bitmap = (drawable as BitmapDrawable).bitmap

            // Set Home Screen wallpaper
            wallpaperManager.setBitmap(bitmap, null, true, FLAG_SYSTEM)

            // Set Lock Screen wallpaper (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, true, FLAG_LOCK)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Key implementation details:**
- Uses `Dispatchers.IO` for background processing
- Loads `R.drawable.wallpaper` as `Bitmap`
- Sets both `FLAG_SYSTEM` (Home) and `FLAG_LOCK` (Lock Screen)
- Lock Screen requires Android 7.0+ (API 24)
- Returns `Result<Unit>` for clean error handling

## Permissions

No additional permissions needed in `AndroidManifest.xml`:
- `SET_WALLPAPER` permission is automatically granted to Device Owner apps
- The app is already a Device Owner, so this capability is available

## Error Handling

Potential errors and handling:
- **Missing/corrupt wallpaper.jpg** → Caught by try-catch, shows error Snackbar
- **Insufficient permissions** → Should not occur (Device Owner), but caught by try-catch
- **OutOfMemoryError** → If image is too large, caught by try-catch
- **API < 24 for Lock Screen** → Version check prevents crash, only Home Screen set

Error messages displayed via Snackbar in Bulgarian.

## UI/UX Design

**Button location:** Admin screen, alongside existing options:
- Избор на приложения
- Настройки на киоск режим
- Автоматично стартиране
- WMS инсталация
- Проверка за актуализации
- Информация за устройството
- **Задай тапет** ← NEW

**Interaction:**
- Single tap → Direct execution (no confirmation dialog)
- Success → Snackbar "Тапетът е зададен успешно" (3 seconds)
- Error → Snackbar "Грешка: [error message]" (3 seconds)

## Future Enhancements

### Phase 2: Provisioning Integration

When ready to add automatic wallpaper setting during QR provisioning:

**File:** `app/src/main/java/com/warehouse/kiosk/services/ProvisioningCompleteActivity.kt`
- Inject `SetWallpaperUseCase` via Hilt
- Call after WiFi setup and before WMS installation
- No changes needed to use case implementation

**QR Code Configuration (optional):**
- Add `set_wallpaper: true/false` flag in provisioning JSON
- Control whether wallpaper is set automatically

### Phase 3: Custom Wallpapers (potential)

If needed in future:
- Allow uploading custom wallpaper via Admin panel
- Store in internal storage
- Update use case to accept file path parameter

## Testing Strategy

**Manual Testing:**
1. Build and install app on Device Owner device
2. Navigate to Admin screen
3. Click "Задай тапет"
4. Verify Home Screen wallpaper changes
5. Lock device and verify Lock Screen wallpaper changes
6. Test error case: temporarily rename wallpaper.jpg and verify error Snackbar

**Edge Cases:**
- Test on Android API 24+ (Lock Screen support)
- Test on Android API 23 (Home Screen only)
- Test with very large image (memory handling)
- Test with missing drawable resource

## File Structure

```
app/src/main/java/com/warehouse/kiosk/
├── domain/usecase/
│   └── SetWallpaperUseCase.kt          (NEW)
└── presentation/admin/
    ├── AdminScreen.kt                   (MODIFIED - add button)
    └── AdminViewModel.kt                (MODIFIED - add state/method)

app/src/main/res/drawable/
└── wallpaper.jpg                        (EXISTS)
```

## Implementation Checklist

- [ ] Create `SetWallpaperUseCase.kt`
- [ ] Update `AdminViewModel.kt` (inject use case, add state, add method)
- [ ] Update `AdminScreen.kt` (add button, add Snackbar handling)
- [ ] Manual testing on device
- [ ] Update CLAUDE.md with new feature
- [ ] Git commit and version bump (if releasing)

## Notes

- Minimum SDK is 31, so Lock Screen support (API 24+) is always available
- Device Owner permissions eliminate permission runtime checks
- Simple one-button UX appropriate for kiosk environment
- Design allows easy extension to provisioning without refactoring