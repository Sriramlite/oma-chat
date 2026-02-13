import { registerPlugin } from './capacitor-core.js';

// Define the plugin key as defined in @capacitor-community/bluetooth-le
// Usually it is 'BluetoothLe'. 
const BluetoothLe = registerPlugin('BluetoothLe', {
    web: {
        initialize: async () => { console.log('Mock BLE Init'); },
        requestLEScan: async () => { console.log('Mock BLE Scan'); },
        stopLEScan: async () => { console.log('Mock BLE Stop'); },
        connect: async () => { console.log('Mock BLE Connect'); },
        disconnect: async () => { console.log('Mock BLE Disconnect'); },
        startNotifications: async () => { console.log('Mock BLE Notify'); },
        write: async () => { console.log('Mock BLE Write'); }
    }
});

// Create a BleClient wrapper that mimics the plugin's API
// The official plugin uses a BleClient class, but for this simple usage
// we can just export an object that proxies to the Native plugin.

export const BleClient = {
    initialize: async () => BluetoothLe.initialize(),
    requestLEScan: async (options, callback) => {
        // Native plugin might return data differently than the BleClient wrapper expects
        // But for "requestLEScan", usually the callback is handled via event listeners in the wrapper.
        // We might need to listen to 'onScanResult' event manually if we bypass BleClient.

        // *Simplified MVP*: Direct call. 
        // Note: The official BleClient processes events 'onScanResult'. 
        // If we use the raw plugin, we usually have to add a listener.

        // For MVP, if we are on Android, `BleClient` logic is complex to replicate manually.
        // STRATEGY: We will assume specific method signatures match. 

        // However, standard use:
        // await BluetoothLe.initialize();
        // await BluetoothLe.requestLEScan(options);
        // BluetoothLe.addListener('onScanResult', callback);

        // Let's implement that pattern here to match nearby.js usage

        await BluetoothLe.initialize();
        if (callback) {
            BluetoothLe.addListener('onScanResult', callback);
        }
        return BluetoothLe.requestLEScan(options);
    },
    stopLEScan: async () => BluetoothLe.stopLEScan(),
    connect: async (deviceId, onDisconnect) => {
        if (onDisconnect) {
            BluetoothLe.addListener('onDisconnect', (data) => {
                if (data.deviceId === deviceId) onDisconnect(data.deviceId);
            });
        }
        return BluetoothLe.connect({ deviceId });
    },
    disconnect: async (deviceId) => BluetoothLe.disconnect({ deviceId }),
    startNotifications: async (deviceId, service, characteristic, callback) => {
        await BluetoothLe.startNotifications({ deviceId, service, characteristic });
        BluetoothLe.addListener('onNotification', (data) => {
            if (data.deviceId === deviceId && data.characteristic === characteristic) {
                // data.value is base64 string usually in raw plugin? OR DataView?
                // We need to assume it returns something we can decode.
                // The built-in BleClient handles DataView conversion. 
                // We might receive a Base64 string here.

                // Let's pass it raw to nearby.js and handle decoding there if needed?
                // nearby.js expects a DataView/ArrayBuffer.
                // If raw plugin sends Base64, we need to convert. 
                // Let's wrap callback.
                if (data.value) {
                    // Check if string (Base64)
                    if (typeof data.value === 'string') {
                        // Convert Base64 to DataView
                        const binaryString = atob(data.value);
                        const bytes = new Uint8Array(binaryString.length);
                        for (let i = 0; i < binaryString.length; i++) {
                            bytes[i] = binaryString.charCodeAt(i);
                        }
                        callback(new DataView(bytes.buffer));
                    } else {
                        // Assume it's already correct (Web usually returns DataView)
                        callback(data.value);
                    }
                }
            }
        });
    },
    write: async (deviceId, service, characteristic, value) => {
        // value is DataView or string? 
        // plugin expects string (Base64) usually for native.

        // Convert value (string or DataView) to Base64
        let base64Val = value;
        if (typeof value !== 'string') {
            // Convert DataView/U8Arr to Base64
            // ... implementation details ...
            // For text "Hello", simpler to just send text? 
            // BleClient expects DataView.
            // We need a helper.
            const bytes = new Uint8Array(value.buffer || value);
            let binary = '';
            for (let i = 0; i < bytes.byteLength; i++) {
                binary += String.fromCharCode(bytes[i]);
            }
            base64Val = btoa(binary);
        }

        return BluetoothLe.write({ deviceId, service, characteristic, value: base64Val });
    }
};

export const numberToUUID = (num) => {
    // Simple helper if needed
    return num.toString(16);
};
