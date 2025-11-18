package com.warehouse.kiosk.services

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.warehouse.kiosk.MainActivity

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    private var tapCount = 0
    private var lastTapTime = 0L

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create a 1x1 pixel invisible view
        overlayView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(100, 100)
            setBackgroundColor(Color.RED)
        }

        val params = WindowManager.LayoutParams(
            100, 100, // 1x1 pixel
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END // Bottom-right corner
            x = 0
            y = 0
        }

        overlayView?.setOnClickListener { handleGestureDetection() }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            // Handle exceptions
        }
    }

    private fun handleGestureDetection() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime > 2000) { // Reset after 2 seconds
            tapCount = 1
        } else {
            tapCount++
        }
        lastTapTime = currentTime

        if (tapCount >= 5) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_SHOW_PASSWORD_DIALOG, true)
            }
            startActivity(intent)
            tapCount = 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            windowManager.removeView(it)
        }
    }
}