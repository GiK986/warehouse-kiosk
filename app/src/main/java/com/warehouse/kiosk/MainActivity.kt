package com.warehouse.kiosk

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.warehouse.kiosk.data.repository.KioskRepository
import com.warehouse.kiosk.domain.manager.DeviceOwnerManager
import com.warehouse.kiosk.domain.manager.KioskSetupCoordinator
import com.warehouse.kiosk.presentation.navigation.AppNavigation
import com.warehouse.kiosk.ui.theme.WarehouseKioskTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Activity for the Warehouse Kiosk application.
 *
 * This activity serves as the entry point and coordinates:
 * - Device Owner setup and policy management
 * - Lock task mode (kiosk mode) activation
 * - Navigation between launcher and admin screens
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: KioskRepository

    @Inject
    lateinit var deviceOwnerManager: DeviceOwnerManager

    @Inject
    lateinit var kioskSetupCoordinator: KioskSetupCoordinator

    // State to trigger navigation reset to launcher when Home button is pressed
    private var shouldResetToLauncher by mutableStateOf(false)

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_FROM_PROVISIONING = "from_provisioning"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = 0x80000000.toInt(), // За светла тема
                darkScrim = 0x80000000.toInt()   // За тъмна тема
            )
        )
        super.onCreate(savedInstanceState)

        Log.i(TAG, "MainActivity onCreate()")


        // Disable back button
        onBackPressedDispatcher.addCallback(this) {}

        // Handle initial intent (might be Home button press)
        handleHomeButtonIntent(intent)

        // Initialize kiosk setup if device owner
        if (deviceOwnerManager.isDeviceOwner) {
            initializeKioskSetup()
        } else {
            Log.w(TAG, "Not Device Owner - kiosk policies will not be set")
        }

        // Set up Compose UI
        setContent {
            WarehouseKioskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        shouldResetToLauncher = shouldResetToLauncher,
                        onResetHandled = { shouldResetToLauncher = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleHomeButtonIntent(intent)
    }

    /**
     * Handle Home button press - triggers navigation reset to launcher screen
     */
    private fun handleHomeButtonIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_MAIN &&
            intent.categories?.contains(Intent.CATEGORY_HOME) == true
        ) {
            Log.i(TAG, "Home button pressed - resetting navigation to launcher")
            shouldResetToLauncher = true
        }
    }

    /**
     * Initialize kiosk setup and start observing kiosk mode changes
     */
    private fun initializeKioskSetup() {
        val fromProvisioning = intent.getBooleanExtra(EXTRA_FROM_PROVISIONING, false)

        lifecycleScope.launch {
            // Execute setup if needed
            val setupResult = kioskSetupCoordinator.determineAndExecuteSetup(fromProvisioning)

            when (setupResult) {
                is KioskSetupCoordinator.SetupResult.Success -> {
                    Log.i(TAG, "Kiosk setup completed successfully")
                }
                is KioskSetupCoordinator.SetupResult.Failed -> {
                    Log.e(TAG, "Kiosk setup failed", setupResult.error)
                }
                KioskSetupCoordinator.SetupResult.Skipped -> {
                    Log.d(TAG, "Kiosk setup skipped - not needed")
                }
                KioskSetupCoordinator.SetupResult.NotDeviceOwner -> {
                    // Should not happen as we check above, but handle it anyway
                    Log.w(TAG, "Cannot setup - not Device Owner")
                }
            }

            // IMPORTANT: Always observe kiosk mode changes
            // This allows kiosk mode to be toggled dynamically without device restart
            observeKioskMode()
        }
    }

    /**
     * Observe kiosk mode state and dynamically update lock task packages
     */
    private fun observeKioskMode() {
        repository.isKioskModeActive
            .combine(repository.getEnabledApps()) { isActive, enabledApps ->
                KioskModeState(isActive, enabledApps.map { it.packageName })
            }
            .onEach { state ->
                updateLockTaskMode(state)
            }
            .launchIn(lifecycleScope)
    }

    /**
     * Update lock task packages and mode based on kiosk state
     */
    private fun updateLockTaskMode(state: KioskModeState) {
        val allowedPackages = buildList {
            add(packageName) // Always include our own package
            if (state.isActive) {
                addAll(state.enabledPackages)
                add("com.android.settings") // Allow settings for admin access
                add("com.android.inputmethod.latin") // Gboard keyboard

            }
        }

        // Update allowed lock task packages
        deviceOwnerManager.setLockTaskPackages(allowedPackages)
            .onFailure { error ->
                Log.e(TAG, "Failed to set lock task packages", error)
            }

        // Start or stop lock task mode
        when {
            state.isActive && !deviceOwnerManager.isInLockTaskMode -> {
                startLockTask()
                Log.i(TAG, "Lock task mode activated")
            }
            !state.isActive && deviceOwnerManager.isInLockTaskMode -> {
                stopLockTask()
                Log.i(TAG, "Lock task mode deactivated")
            }
        }
    }

    /**
     * Data class representing kiosk mode state
     */
    private data class KioskModeState(
        val isActive: Boolean,
        val enabledPackages: List<String>
    )
}