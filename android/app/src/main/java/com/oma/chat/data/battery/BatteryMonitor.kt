package com.oma.chat.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.socket.SocketManager
import com.oma.chat.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val socketManager: SocketManager,
    private val userRepository: UserRepository,
    private val authPreferences: AuthPreferences
) {
    private var lastLevel: Int = -1
    private var lastCharging: Boolean = false
    private var receiver: BroadcastReceiver? = null
    private var pollingJob: Job? = null

    fun startMonitoring(scope: CoroutineScope) {
        if (receiver != null) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    processBatteryIntent(intent, scope)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)

        // Read initial state immediately
        val initialIntent = context.registerReceiver(null, filter)
        if (initialIntent != null) {
            processBatteryIntent(initialIntent, scope, force = true)
        }

        // Periodic heartbeat every 60s
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000L)
                checkAndSend(scope, force = false)
            }
        }
    }

    private fun processBatteryIntent(intent: Intent, scope: CoroutineScope, force: Boolean = false) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level == -1 || scale <= 0) return

        val batteryPct = (level * 100f / scale).toInt()
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        if (force || batteryPct != lastLevel || isCharging != lastCharging) {
            lastLevel = batteryPct
            lastCharging = isCharging
            dispatchBatteryUpdate(batteryPct, isCharging, scope)
        }
    }

    private fun checkAndSend(scope: CoroutineScope, force: Boolean = false) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (bm != null) {
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = bm.isCharging
            if (force || level != lastLevel || isCharging != lastCharging) {
                lastLevel = level
                lastCharging = isCharging
                dispatchBatteryUpdate(level, isCharging, scope)
            }
        }
    }

    private fun dispatchBatteryUpdate(level: Int, isCharging: Boolean, scope: CoroutineScope) {
        val user = authPreferences.getUser() ?: return
        val shareBattery = user.settings.shareBattery

        if (!shareBattery) return

        scope.launch(Dispatchers.IO) {
            try {
                // Emit via socket for instant live updates to active chats
                socketManager.emitBattery(level, isCharging)
                // Persist to backend DB so last seen / offline users retain it
                userRepository.updateBattery(level, isCharging)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopMonitoring() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignored if already unregistered
            }
            receiver = null
        }
        pollingJob?.cancel()
        pollingJob = null
    }
}
