package com.oma.chat.data.call

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.oma.chat.data.diagnostics.DiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallProximityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: DiagnosticsLogger
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    init {
        initWakeLock()
    }

    private fun initWakeLock() {
        try {
            if (powerManager != null && isSupported()) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "oma:call_proximity_wake_lock"
                ).apply {
                    setReferenceCounted(false)
                }
                logger.info("CallProximityManager", "Proximity screen-off wake lock initialized.")
            } else {
                logger.warn("CallProximityManager", "PROXIMITY_SCREEN_OFF_WAKE_LOCK not supported on this device.")
            }
        } catch (e: Exception) {
            logger.error("CallProximityManager", "Failed to initialize proximity wake lock: ${e.localizedMessage}")
        }
    }

    fun isSupported(): Boolean {
        return try {
            powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun acquire() {
        try {
            if (wakeLock == null) {
                initWakeLock()
            }
            wakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire()
                    logger.info("CallProximityManager", "Proximity wake lock acquired -> Screen will turn off near ear.")
                }
            }
        } catch (e: Exception) {
            logger.error("CallProximityManager", "Error acquiring proximity wake lock: ${e.localizedMessage}")
        }
    }

    @Synchronized
    fun release() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                    } else {
                        lock.release()
                    }
                    logger.info("CallProximityManager", "Proximity wake lock released -> Screen restored.")
                }
            }
        } catch (e: Exception) {
            try {
                wakeLock?.let { if (it.isHeld) it.release() }
            } catch (_: Exception) {}
            logger.error("CallProximityManager", "Error releasing proximity wake lock: ${e.localizedMessage}")
        }
    }
}
