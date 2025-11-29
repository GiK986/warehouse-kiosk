package com.warehouse.kiosk.domain.manager

import android.util.Log
import com.warehouse.kiosk.data.repository.KioskRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates kiosk mode setup and initialization logic.
 * Determines when and how to set up kiosk policies based on app state.
 */
@Singleton
class KioskSetupCoordinator @Inject constructor(
    private val repository: KioskRepository,
    private val deviceOwnerManager: DeviceOwnerManager
) {
    companion object {
        private const val TAG = "KioskSetupCoordinator"
    }

    /**
     * Determine if setup should run and execute it if needed.
     *
     * Setup runs when ANY of these conditions is true:
     * 1. Coming from provisioning (QR code setup)
     * 2. Kiosk mode is active (normal restart)
     * 3. First time as Device Owner (ADB setup)
     */
    suspend fun determineAndExecuteSetup(fromProvisioning: Boolean): SetupResult {
        if (!deviceOwnerManager.isDeviceOwner) {
            Log.w(TAG, "App is not Device Owner - skipping setup")
            return SetupResult.NotDeviceOwner
        }

        val kioskModeActive = repository.isKioskModeActive.first()
        val setupCompleted = repository.isInitialSetupCompleted.first()

        Log.i(TAG, "Setup check:")
        Log.i(TAG, "  - From provisioning: $fromProvisioning")
        Log.i(TAG, "  - Kiosk mode active: $kioskModeActive")
        Log.i(TAG, "  - Initial setup completed: $setupCompleted")

        val isFirstTimeSetup = !setupCompleted
        val shouldRunSetup = fromProvisioning || kioskModeActive || isFirstTimeSetup

        if (!shouldRunSetup) {
            Log.i(TAG, "✗ Skipping setup - not needed")
            Log.i(TAG, "  Reason: Setup completed AND kiosk mode not active")
            return SetupResult.Skipped
        }

        return executeSetup(isFirstTimeSetup, fromProvisioning, kioskModeActive)
    }

    /**
     * Execute the kiosk setup process
     */
    private suspend fun executeSetup(
        isFirstTimeSetup: Boolean,
        fromProvisioning: Boolean,
        kioskModeActive: Boolean
    ): SetupResult {
        Log.i(TAG, "✓ Running kiosk setup")

        // Log reason for setup
        when {
            isFirstTimeSetup -> Log.i(TAG, "  Reason: First time Device Owner setup")
            fromProvisioning -> Log.i(TAG, "  Reason: Coming from provisioning")
            kioskModeActive -> Log.i(TAG, "  Reason: Kiosk mode is active (normal restart)")
        }

        // Set device policies
        val policiesResult = setKioskPolicies()

        // Mark setup as completed if first time
        if (isFirstTimeSetup) {
            Log.i(TAG, "  Marking initial setup as completed")
            repository.setInitialSetupCompleted(true)
        }

        // Ensure kiosk mode is marked as active
        if (!kioskModeActive) {
            Log.i(TAG, "  Activating kiosk mode")
            repository.setKioskModeActive(true)
        }

        return if (policiesResult.isSuccess) {
            SetupResult.Success
        } else {
            SetupResult.Failed(policiesResult.exceptionOrNull())
        }
    }

    /**
     * Set all kiosk policies (home launcher and lock task features)
     */
    private suspend fun setKioskPolicies(): Result<Unit> {
        Log.i(TAG, "Setting kiosk policies...")

        // Set as home launcher
        val launcherResult = deviceOwnerManager.setAsHomeLauncher()
        if (launcherResult.isFailure) {
            Log.e(TAG, "✗ Failed to set home launcher", launcherResult.exceptionOrNull())
            return launcherResult
        }

        // Set lock task features
        val featuresResult = deviceOwnerManager.setLockTaskFeatures()
        if (featuresResult.isFailure) {
            Log.e(TAG, "✗ Failed to set lock task features", featuresResult.exceptionOrNull())
            return featuresResult
        }

        Log.i(TAG, "✓ Kiosk policies set successfully")
        return Result.success(Unit)
    }

    /**
     * Result of setup operation
     */
    sealed class SetupResult {
        object Success : SetupResult()
        object Skipped : SetupResult()
        object NotDeviceOwner : SetupResult()
        data class Failed(val error: Throwable?) : SetupResult()
    }
}