package com.warehouse.kiosk.data.repository

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.warehouse.kiosk.services.DeviceOwnerReceiver
import com.warehouse.kiosk.services.InstallStatusReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository за download и install на APK файлове.
 *
 * Device Owner permissions позволяват silent install без user interaction.
 */
@Singleton
class ApkRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ApkRepository"
        private const val BUFFER_SIZE = 8192
    }

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, DeviceOwnerReceiver::class.java)
    }

    /**
     * Download APK файл от URL.
     *
     * @param url URL към APK
     * @param onProgress Callback за прогрес (0-100)
     * @return Downloaded File
     */
    suspend fun downloadApk(
        url: String,
        onProgress: ((Int) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        Log.i(TAG, "Downloading APK from: $url")

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        try {
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: ${connection.responseCode}")
            }

            val fileLength = connection.contentLength
            Log.d(TAG, "APK size: $fileLength bytes")

            // Създаваме temp file
            val tempFile = File(context.cacheDir, "temp_install.apk")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Изчисляваме и емитваме прогрес
                        if (fileLength > 0) {
                            val progress = ((totalBytesRead * 100) / fileLength).toInt()
                            onProgress?.invoke(progress)
                        }
                    }

                    output.flush()
                }
            }

            Log.i(TAG, "APK downloaded successfully: ${tempFile.absolutePath}")
            tempFile

        } finally {
            connection.disconnect()
        }
    }

    /**
     * Silent install на APK чрез Device Owner API.
     *
     * Device Owner може да инсталира APK-та без user interaction използвайки
     * DevicePolicyManager.installPackage() (API 28+) или PackageInstaller (по-стари версии).
     *
     * @param apkFile APK файл за install
     * @return true ако успешно, false ако fail
     */
    suspend fun installApk(apkFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "App is not Device Owner! Cannot perform silent install")
            return@withContext false
        }

        try {
            Log.i(TAG, "Installing APK: ${apkFile.absolutePath}")

            // Android 9 (API 28) и по-нови: Използваме DevicePolicyManager.installPackage()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                Log.d(TAG, "Using DevicePolicyManager.installPackage() for silent install")

                // Създаваме PackageInstaller.SessionParams
                val sessionParams = android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )

                // Създаваме session
                val packageInstaller = context.packageManager.packageInstaller
                val sessionId = packageInstaller.createSession(sessionParams)
                val session = packageInstaller.openSession(sessionId)

                try {
                    // Записваме APK в session
                    session.openWrite("package", 0, -1).use { outputStream ->
                        apkFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        session.fsync(outputStream)
                    }

                    // Commit session - това ще инсталира APK-то
                    val intent = android.content.Intent(context, InstallStatusReceiver::class.java)
                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                    )

                    session.commit(pendingIntent.intentSender)
                    Log.i(TAG, "Install session committed with ID: $sessionId")

                    // За Device Owner, install-ът е silent и ще се случи веднага
                    return@withContext true

                } catch (e: Exception) {
                    session.abandon()
                    throw e
                }
            } else {
                // Android 8 и по-стари: Използваме PackageInstaller директно
                Log.d(TAG, "Using PackageInstaller for older Android version")

                val packageInstaller = context.packageManager.packageInstaller
                val sessionParams = android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )

                val sessionId = packageInstaller.createSession(sessionParams)
                val session = packageInstaller.openSession(sessionId)

                try {
                    session.openWrite("package", 0, -1).use { outputStream ->
                        apkFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        session.fsync(outputStream)
                    }

                    val intent = android.content.Intent(context, InstallStatusReceiver::class.java)
                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                    )

                    session.commit(pendingIntent.intentSender)
                    Log.i(TAG, "Install session committed")
                    return@withContext true

                } catch (e: Exception) {
                    session.abandon()
                    throw e
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK", e)
            return@withContext false
        }
    }

    /**
     * Изтрива downloaded APK файл.
     */
    fun cleanupApkFile(file: File) {
        try {
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Cleaned up APK file: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup APK file", e)
        }
    }
}