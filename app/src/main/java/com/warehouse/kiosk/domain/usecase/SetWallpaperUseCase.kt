package com.warehouse.kiosk.domain.usecase

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.warehouse.kiosk.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max

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
            val originalBitmap = (drawable as BitmapDrawable).bitmap

            // Вземаме размерите на екрана
            val screenSize = getScreenSize()
            Log.i(TAG, "Screen size: ${screenSize.widthPixels}x${screenSize.heightPixels}")
            Log.i(TAG, "Original wallpaper size: ${originalBitmap.width}x${originalBitmap.height}")

            // Center crop bitmap-а за да покрие целия екран
            val scaledBitmap = centerCropBitmap(
                originalBitmap,
                screenSize.widthPixels,
                screenSize.heightPixels
            )
            Log.i(TAG, "Scaled wallpaper size: ${scaledBitmap.width}x${scaledBitmap.height}")

            // Задаваме Home Screen wallpaper
            wallpaperManager.setBitmap(scaledBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            Log.i(TAG, "Home Screen wallpaper set successfully")

            // Задаваме Lock Screen wallpaper (само на API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(scaledBitmap, null, true, WallpaperManager.FLAG_LOCK)
                Log.i(TAG, "Lock Screen wallpaper set successfully")
            } else {
                Log.i(TAG, "Lock Screen wallpaper not supported on API < 24")
            }

            // Cleanup scaled bitmap ако е различен от оригиналния
            if (scaledBitmap != originalBitmap) {
                // Оригиналният bitmap ще се recycled автоматично от GC
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set wallpaper", e)
            Result.failure(e)
        }
    }

    /**
     * Вземa размерите на екрана на устройството.
     */
    private fun getScreenSize(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            val windowMetrics = windowManager.maximumWindowMetrics
            val bounds = windowMetrics.bounds
            displayMetrics.widthPixels = bounds.width()
            displayMetrics.heightPixels = bounds.height()
            // Задаваме density от resources
            displayMetrics.density = context.resources.displayMetrics.density
            displayMetrics.densityDpi = context.resources.displayMetrics.densityDpi
        } else {
            // Android 10 и по-стари
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        }

        return displayMetrics
    }

    /**
     * Center crop на bitmap за да покрие целия екран.
     *
     * Запазва aspect ratio на оригиналния bitmap и го scale-ва така че
     * да покрие целия екран, след което crop-ва излишната част.
     *
     * @param source Оригиналният bitmap
     * @param targetWidth Ширина на екрана
     * @param targetHeight Височина на екрана
     * @return Scaled и cropped bitmap
     */
    private fun centerCropBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height

        // Изчисляваме scale factor за да покрие целия екран
        val scaleX = targetWidth.toFloat() / sourceWidth
        val scaleY = targetHeight.toFloat() / sourceHeight
        val scale = max(scaleX, scaleY) // Използваме max за да покрие целия екран

        // Изчисляваме новите размери след scaling
        val scaledWidth = (sourceWidth * scale).toInt()
        val scaledHeight = (sourceHeight * scale).toInt()

        // Изчисляваме offset за центриране
        val left = (scaledWidth - targetWidth) / 2
        val top = (scaledHeight - targetHeight) / 2

        // Създаваме matrix за scaling и cropping
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(-left.toFloat(), -top.toFloat())
        }

        // Създаваме нов bitmap с точните размери на екрана
        return Bitmap.createBitmap(
            source,
            0,
            0,
            sourceWidth,
            sourceHeight,
            matrix,
            true // filter за по-добро качество при scaling
        )
    }
}
