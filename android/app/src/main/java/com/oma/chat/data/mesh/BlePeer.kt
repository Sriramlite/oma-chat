package com.oma.chat.data.mesh

/**
 * Represents a nearby peer discovered or connected over Bluetooth LE.
 */
data class BlePeer(
    val deviceAddress: String,
    val deviceName: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false
)
