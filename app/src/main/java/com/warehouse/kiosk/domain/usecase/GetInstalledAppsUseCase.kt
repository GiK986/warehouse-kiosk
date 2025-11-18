package com.warehouse.kiosk.domain.usecase

import android.content.Context
import android.content.Intent
import com.warehouse.kiosk.data.database.AppEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): List<AppEntity> {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(mainIntent, 0)

        return apps.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            // Filter out our own app
            if (packageName == context.packageName || packageName == "com.android.settings") {
                return@mapNotNull null
            }

            AppEntity(
                packageName = packageName,
                appName = resolveInfo.loadLabel(packageManager).toString(),
                isEnabled = false // Disabled by default
            )
        }
    }
}
