package com.warehouse.kiosk.domain.usecase

import android.content.Context
import android.util.Log
import com.warehouse.kiosk.data.repository.ApkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * UseCase за download и install на APK файлове.
 *
 * Използва се от:
 * - ProvisioningCompleteActivity (автоматично при provisioning)
 * - WmsInstallScreen (ръчно от Admin panel)
 *
 * Flow на операцията:
 * 1. Download APK от URL
 * 2. Проверка на Device Owner permissions
 * 3. Silent install чрез DevicePolicyManager
 * 4. Cleanup на downloaded file
 */
class DownloadAndInstallApkUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apkRepository: ApkRepository
) {
    companion object {
        private const val TAG = "DownloadInstallUseCase"
    }

    /**
     * Download и install на APK.
     *
     * @param apkUrl URL към APK файла
     * @return Flow<InstallState> емитва прогрес и резултат
     */
    operator fun invoke(apkUrl: String): Flow<InstallState> = flow {
        Log.i(TAG, "Starting APK download and install: $apkUrl")

        try {
            // Валидация на URL
            if (apkUrl.isBlank()) {
                emit(InstallState.Error("APK URL е празен"))
                return@flow
            }

            if (!apkUrl.startsWith("http://") && !apkUrl.startsWith("https://")) {
                emit(InstallState.Error("Невалиден URL формат"))
                return@flow
            }

            // Започваме download
            emit(InstallState.Downloading(0))

            // Download APK
            val apkFile = apkRepository.downloadApk(
                url = apkUrl,
                onProgress = { progress ->
                    // Емитваме прогрес
                    // Note: Този callback се извиква от repository
                }
            )

            Log.i(TAG, "APK downloaded successfully: ${apkFile.absolutePath}")
            emit(InstallState.Installing)

            // Install APK (silent install чрез Device Owner)
            val success = apkRepository.installApk(apkFile)

            if (success) {
                Log.i(TAG, "APK installed successfully")
                emit(InstallState.Success)
            } else {
                Log.e(TAG, "APK installation failed")
                emit(InstallState.Error("Инсталацията се провали"))
            }

            // Cleanup
            apkRepository.cleanupApkFile(apkFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error during APK install", e)
            emit(InstallState.Error(e.message ?: "Неизвестна грешка"))
        }
    }

    /**
     * Състояние на install процеса
     */
    sealed class InstallState {
        data class Downloading(val progress: Int) : InstallState()
        data object Installing : InstallState()
        data object Success : InstallState()
        data class Error(val message: String) : InstallState()
    }
}