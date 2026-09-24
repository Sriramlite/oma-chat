package com.oma.chat.data.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import com.oma.chat.data.diagnostics.DiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallProximityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: DiagnosticsLogger
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var wakeLock: PowerManager.WakeLock? = null
    private var isSensorRegistered = false

    private val _isNear = MutableStateFlow(false)
    val isNear: StateFlow<Boolean> = _isNear.asStateFlow()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            // Some sensors return binary (0 or maxRange), some return continuous distance in cm
            val near = if (maxRange > 5f) {
                distance < 5f
            } else {
                distance < maxRange || distance == 0f
            }
            logger.info("CallProximityManager", "Proximity sensor: distance=$distance, maxRange=$maxRange -> isNear=$near")
            _isNear.value = near
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    init {
        initWakeLock()
    }

    private fun initWakeLock() {
        try {
            if (powerManager != null && isWakeLockSupported()) {
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

    fun isWakeLockSupported(): Boolean {
        return try {
            powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun acquire() {
        try {
            // 1. Acquire OS WakeLock
            if (wakeLock == null) {
                initWakeLock()
            }
            wakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire()
                    logger.info("CallProximityManager", "Proximity wake lock acquired.")
                }
            }

            // 2. Register Hardware Proximity Sensor Listener
            if (!isSensorRegistered && sensorManager != null && proximitySensor != null) {
                sensorManager.registerListener(
                    sensorListener,
                    proximitySensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                isSensorRegistered = true
                logger.info("CallProximityManager", "Proximity hardware sensor listener registered.")
            }
        } catch (e: Exception) {
            logger.error("CallProximityManager", "Error acquiring proximity manager: ${e.localizedMessage}")
        }
    }

    @Synchronized
    fun release() {
        try {
            // 1. Release OS WakeLock
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                    } else {
                        lock.release()
                    }
                    logger.info("CallProximityManager", "Proximity wake lock released.")
                }
            }

            // 2. Unregister Hardware Proximity Sensor Listener
            if (isSensorRegistered && sensorManager != null) {
                sensorManager.unregisterListener(sensorListener)
                isSensorRegistered = false
                _isNear.value = false
                logger.info("CallProximityManager", "Proximity hardware sensor listener unregistered.")
            }
        } catch (e: Exception) {
            try {
                wakeLock?.let { if (it.isHeld) it.release() }
            } catch (_: Exception) {}
            isSensorRegistered = false
            _isNear.value = false
            logger.error("CallProximityManager", "Error releasing proximity manager: ${e.localizedMessage}")
        }
    }
}
