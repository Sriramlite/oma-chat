package com.oma.chat.data.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleMeshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val authPreferences: AuthPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val meshScope = CoroutineScope(ioDispatcher)

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    // Active GATT server & advertiser (Peripheral Role)
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    // Scanner & connected GATT clients (Central Role)
    private var scanner: BluetoothLeScanner? = null
    private val connectedGatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val peerMap = ConcurrentHashMap<String, BlePeer>()

    // Message De-duplication Cache (packetId -> timestamp)
    private val seenPacketIds = ConcurrentHashMap<String, Long>()

    // Observed UI States
    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    private val _peers = MutableStateFlow<List<BlePeer>>(emptyList())
    val peers: StateFlow<List<BlePeer>> = _peers.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    companion object {
        private const val TAG = "BleMeshManager"
        val SERVICE_UUID: UUID = UUID.fromString("f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5c")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5d")
        private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Checks if all required Bluetooth permissions are granted.
     */
    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Starts the BLE Mesh subsystem (Advertiser + GATT Server + Scanner).
     */
    fun startMesh() {
        if (_isMeshActive.value) return
        if (!hasPermissions()) {
            Log.w(TAG, "Cannot start BLE Mesh: Missing Bluetooth permissions")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Cannot start BLE Mesh: Bluetooth is not enabled")
            return
        }

        try {
            startGattServer()
            startAdvertising()
            startScanning()
            _isMeshActive.value = true
            Log.i(TAG, "BLE Mesh started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE Mesh", e)
        }
    }

    /**
     * Stops the BLE Mesh subsystem and disconnects all nodes.
     */
    fun stopMesh() {
        if (!_isMeshActive.value) return

        try {
            stopScanning()
            stopAdvertising()
            stopGattServer()

            connectedGatts.values.forEach { gatt ->
                try { gatt.close() } catch (ignored: Exception) {}
            }
            connectedGatts.clear()
            peerMap.clear()
            _peers.value = emptyList()
            _isMeshActive.value = false
            Log.i(TAG, "BLE Mesh stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE Mesh", e)
        }
    }

    /**
     * Restarts the BLE Mesh subsystem.
     */
    fun restartMesh() {
        stopMesh()
        startMesh()
    }

    /**
     * Dispatches a message packet into the mesh.
     */
    fun sendPacket(packet: MeshPacket): Boolean {
        // Mark as seen locally to prevent reflecting back
        seenPacketIds[packet.packetId] = System.currentTimeMillis()
        val json = gson.toJson(packet)
        val data = json.toByteArray(StandardCharsets.UTF_8)

        Log.d(TAG, "Broadcasting packet ${packet.packetId} (${data.size} bytes) to ${connectedGatts.size} connected peers")

        var sentCount = 0
        connectedGatts.values.forEach { gatt ->
            try {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = data
                        @Suppress("DEPRECATION")
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(characteristic)
                    }
                    sentCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write packet to peer ${gatt.device.address}", e)
            }
        }
        return sentCount > 0
    }

    // ==========================================
    // 1. GATT Server (Peripheral Mode - Inbound)
    // ==========================================

    private fun startGattServer() {
        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

                if (responseNeeded && device != null) {
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    } catch (ignored: SecurityException) {}
                }

                if (value != null && value.isNotEmpty()) {
                    try {
                        val json = String(value, StandardCharsets.UTF_8)
                        val packet = gson.fromJson(json, MeshPacket::class.java)
                        if (packet != null) {
                            processReceivedPacket(packet, sourceAddress = device?.address)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing incoming packet from ${device?.address}", e)
                    }
                }
            }
        }

        try {
            gattServer = bluetoothManager?.openGattServer(context, serverCallback)
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val char = BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(char)
            gattServer?.addService(service)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting GATT server", e)
        }
    }

    private fun stopGattServer() {
        try {
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
        } catch (ignored: Exception) {}
    }

    // ==========================================
    // 2. BLE Advertising (Broadcast presence)
    // ==========================================

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE Mesh Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Mesh Advertising failed with code: $errorCode")
        }
    }

    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BluetoothLeAdvertiser not available on this device")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Keep small (<=31 bytes)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting advertising", e)
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            advertiser = null
        } catch (ignored: Exception) {}
    }

    // ==========================================
    // 3. BLE Scanning & Connecting (Central Mode)
    // ==========================================

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val device = result?.device ?: return
            val address = device.address ?: return
            val rssi = result.rssi

            var name: String? = null
            try {
                name = device.name
            } catch (ignored: SecurityException) {}

            val peer = BlePeer(
                deviceAddress = address,
                deviceName = name ?: "OMA Peer (${address.takeLast(5)})",
                rssi = rssi,
                lastSeen = System.currentTimeMillis(),
                isConnected = connectedGatts.containsKey(address)
            )

            peerMap[address] = peer
            _peers.value = peerMap.values.toList()

            // Connect to discovered peer if not already connected
            if (!connectedGatts.containsKey(address)) {
                connectToPeer(device)
            }
        }
    }

    private fun startScanning() {
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            Log.d(TAG, "BLE Mesh Scanner started")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting scanner", e)
        }
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
            scanner = null
        } catch (ignored: Exception) {}
    }

    private fun connectToPeer(device: BluetoothDevice) {
        val address = device.address
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothProfile.STATE_CONNECTED && gatt != null) {
                    Log.d(TAG, "Connected to GATT peer: $address")
                    try {
                        gatt.requestMtu(512) // Request larger packet MTU
                    } catch (ignored: SecurityException) {}
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED && gatt != null) {
                    Log.d(TAG, "Disconnected from GATT peer: $address")
                    connectedGatts.remove(address)
                    try { gatt.close() } catch (ignored: Exception) {}

                    val existing = peerMap[address]
                    if (existing != null) {
                        peerMap[address] = existing.copy(isConnected = false)
                        _peers.value = peerMap.values.toList()
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                Log.d(TAG, "MTU changed to $mtu for $address")
                try {
                    gatt?.discoverServices()
                } catch (ignored: SecurityException) {}
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service != null && service.getCharacteristic(CHARACTERISTIC_UUID) != null) {
                        connectedGatts[address] = gatt
                        val existing = peerMap[address]
                        if (existing != null) {
                            peerMap[address] = existing.copy(isConnected = true)
                            _peers.value = peerMap.values.toList()
                        }
                        Log.i(TAG, "Peer $address ready for Mesh communication! Total active peers: ${connectedGatts.size}")
                    }
                }
            }
        }

        try {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connecting to peer $address", e)
        }
    }

    // ==========================================
    // 4. Mesh Routing & Multi-Hop Relay Engine
    // ==========================================

    private fun processReceivedPacket(packet: MeshPacket, sourceAddress: String?) {
        cleanupOldCache()

        // 1. De-duplication check: if we already saw this packet, ignore to prevent infinite loops
        if (seenPacketIds.putIfAbsent(packet.packetId, System.currentTimeMillis()) != null) {
            return
        }

        val myUserId = authPreferences.getUserId() ?: ""

        // 2. Is this packet intended for me or is it a broadcast?
        val isForMe = (packet.receiverId == myUserId || packet.receiverId == "*")
        if (isForMe) {
            Log.d(TAG, "Received message packet ${packet.packetId} from ${packet.senderName} (${packet.senderId}) via ${packet.hopCount} hops")
            meshScope.launch {
                _incomingPackets.emit(packet)
            }
        }

        // 3. Multi-Hop Relay: If TTL > 1, decrement TTL, increment hops, and relay to other peers
        if (packet.ttl > 1) {
            val relayPacket = packet.copy(
                ttl = packet.ttl - 1,
                hopCount = packet.hopCount + 1
            )
            Log.d(TAG, "Relaying packet ${packet.packetId} (Hop ${relayPacket.hopCount}, Remaining TTL: ${relayPacket.ttl})")
            relayToConnectedPeers(relayPacket, excludeAddress = sourceAddress)
        }
    }

    private fun relayToConnectedPeers(packet: MeshPacket, excludeAddress: String?) {
        val json = gson.toJson(packet)
        val data = json.toByteArray(StandardCharsets.UTF_8)

        connectedGatts.entries.forEach { (address, gatt) ->
            if (address != excludeAddress) {
                try {
                    val service = gatt.getService(SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                        } else {
                            @Suppress("DEPRECATION")
                            characteristic.value = data
                            @Suppress("DEPRECATION")
                            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            @Suppress("DEPRECATION")
                            gatt.writeCharacteristic(characteristic)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to relay packet to $address", e)
                }
            }
        }
    }

    private fun cleanupOldCache() {
        val now = System.currentTimeMillis()
        if (seenPacketIds.size > 200) {
            seenPacketIds.entries.removeIf { now - it.value > CACHE_EXPIRY_MS }
        }
    }
}
