package com.warehouse.kiosk.domain.usecase

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import com.warehouse.kiosk.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UseCase за задаване на wallpaper на устройството.
 *
 * Използва се от:
 * - AdminScreen (ръчно от Admin panel)
 * - (Бъдещо) ProvisioningCompleteActivity (автоматично при provisioning)
 *
 * Функционалност:
 * - Зарежда wallpaper.jpg от drawable ресурсите
 * - Задава тапет за Home Screen (FLAG_SYSTEM)
 * - Задава тапет за Lock Screen (FLAG_LOCK) на API 24+
 */
class SetWallpaperUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SetWallpaperUseCase"
    }

    /**
     * Задава wallpaper.jpg като тапет на устройството.
     *
     * @return Result<Unit> с успех или грешка
     */
    suspend operator fun invoke(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Setting device wallpaper from drawable resource")

            val wallpaperManager = WallpaperManager.getInstance(context)

            // Зареждаме drawable ресурса
            val drawable = context.getDrawable(R.drawable.wallpaper)
                ?: return@withContext Result.failure(Exception("Wallpaper drawable not found"))

            // Конвертираме към Bitmap
            val bitmap = (drawable as BitmapDrawable).bitmap

            // Задаваме Home Screen wallpaper
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            Log.i(TAG, "Home Screen wallpaper set successfully")

            // Задаваме Lock Screen wallpaper (само на API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                Log.i(TAG, "Lock Screen wallpaper set successfully")
            } else {
                Log.i(TAG, "Lock Screen wallpaper not supported on API < 24")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set wallpaper", e)
            Result.failure(e)
        }
    }
}
