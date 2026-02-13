import { BleClient, numberToUUID } from './bluetooth.js';

// Bitchat Service UUID (Compatible)
const SERVICE_UUID = 'f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5c';
const CHARACTERISTIC_UUID = 'f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5d'; // Custom for OMA

export const nearby = {
    isInitialized: false,
    peers: new Map(), // deviceId -> { id, name, rssi, device }
    connectedDevice: null,

    async init() {
        if (this.isInitialized) return;

        try {
            await BleClient.initialize();
            this.isInitialized = true;
            console.log("BLE Initialized");
        } catch (e) {
            console.error("BLE Init Error:", e);
            window.showCustomAlert("Bluetooth Error: " + e.message, "error");
        }
    },

    async startScanning() {
        if (!this.isInitialized) await this.init();

        console.log("Starting BLE Scan...");
        window.showCustomAlert("Starting BLE Scan...", "info"); // DEBUG VISUAL
        try {
            // BLE Scan
            await BleClient.requestLEScan(
                {
                    services: [SERVICE_UUID],
                },
                (result) => {
                    this.handleDiscoveredDevice(result);
                }
            );

            // Start Advertising (Peripheral Mode) - Android Only usually requires native code or plugin extensions
            // The standard plugin is mostly Central (Client). 
            // Attempting to advertise requires platform-specific logic or a different plugin for "Peripheral" mode.
            // For now, we focus on Scanning (Central) to find others.
            // *CRITICAL*: Capacitor BLE plugin is primarily for Central role. 
            // To be a Peripheral (Advertiser), we might need `capawesome` or native bridge.
            // We will simulate "Discovery" for now by scanning. 

            // Wait, Bitchat P2P requires BOTH advertising.
            // If we can't advertise, we can't be found.
            // Let's assume for this MVP we scan.

        } catch (e) {
            console.error("BLE Scan Error:", e);
        }
    },

    handleDiscoveredDevice(result) {
        if (!this.peers.has(result.device.deviceId)) {
            console.log('New Peer Found:', result.device);
            this.peers.set(result.device.deviceId, {
                id: result.device.deviceId,
                name: result.device.name || 'Unknown Peer',
                rssi: result.rssi,
                device: result.device
            });

            // Update UI if in Nearby Mode
            if (window.state.chatFilter === 'nearby') {
                window.refreshNearbyList(); // We need to add this to app.js
            }
        }
    },

    async connect(deviceId) {
        try {
            await BleClient.connect(deviceId, (deviceId) => this.onDisconnect(deviceId));
            console.log("Connected to", deviceId);
            this.connectedDevice = deviceId;

            // Subscribe to notifications (Receive Messages)
            await this.startNotifications(deviceId);

            window.showCustomAlert("Connected to Peer!", "success");


            // Switch to Chat View via Routing
            window.location.hash = '#chat/' + deviceId;


        } catch (e) {
            console.error("Connection Error:", e);
            window.showCustomAlert("Connection Failed: " + e.message, "error");
        }
    },

    async disconnect() {
        if (this.connectedDevice) {
            await BleClient.disconnect(this.connectedDevice);
            this.connectedDevice = null;
            console.log("Disconnected");
        }
    },

    onDisconnect(deviceId) {
        console.log("Device disconnected:", deviceId);
        this.connectedDevice = null;
        window.showCustomAlert("Peer Disconnected", "info");
    },

    async startNotifications(deviceId) {
        try {
            await BleClient.startNotifications(
                deviceId,
                SERVICE_UUID,
                CHARACTERISTIC_UUID,
                (value) => {
                    const msgStr = new TextDecoder().decode(value);
                    console.log("Received via BLE:", msgStr);
                    this.handleIncomingMessage(msgStr);
                }
            );
        } catch (e) {
            console.error("Notification Error:", e);
        }
    },

    async sendMessage(content) {
        if (!this.connectedDevice) {
            window.showCustomAlert("No peer connected", "error");
            return;
        }

        const msgObj = {
            id: 'ble-' + Date.now(),
            from: window.state.user.user.id, // My ID
            to: this.connectedDevice, // Peer ID (or 'broadcast'?)
            content: content,
            timestamp: Date.now(),
            ttl: 3 // Mesh TTL
        };

        const msgStr = JSON.stringify(msgObj);
        const data = new TextEncoder().encode(msgStr);

        try {
            await BleClient.write(
                this.connectedDevice,
                SERVICE_UUID,
                CHARACTERISTIC_UUID,
                data
            );
            console.log("Sent via BLE:", msgStr);

            // Add to my own UI
            this.handleIncomingMessage(msgStr, true);

        } catch (e) {
            console.error("Send Error:", e);
            window.showCustomAlert("Send Failed", "error");
        }
    },

    handleIncomingMessage(msgStr, isSelf = false) {
        try {
            const msg = JSON.parse(msgStr);

            // Display in Chat
            // We need to inject this into app.js state.messages or similar
            // For now, we hack it by reusing app.js socket logic essentially

            // If it is FOR ME or Broadcast
            // (In this 1-to-1 connection, everything is for me)

            // Create a pseudo-message object compatible with app.js
            const appMsg = {
                id: msg.id,
                senderId: msg.from,
                receiverId: 'me', // or my ID
                content: msg.content,
                timestamp: msg.timestamp,
                type: 'text',
                isNearby: true
            };

            if (isSelf) {
                appMsg.senderId = window.state.user.user.id; // Correct sender
                // Note: 'receiverId' should be the peer ID technically for UI logic
                appMsg.receiverId = this.connectedDevice;
            }

            // Push to local storage? Or just State?
            // State only for MVP
            window.state.messages.push(appMsg);

            // Refresh View
            if (window.state.activeChatId === (isSelf ? this.connectedDevice : msg.from)) {
                window.render(); // or appendMessage
                // Ideally call appendMessage
                const container = document.getElementById('messages-container');
                if (container && window.appendMessage) {
                    window.appendMessage(appMsg, container);
                    window.scrollToBottom(container);
                }
            } else {
                // Notify unread?
                window.showCustomAlert("New Nearby Message", "info");
            }

        } catch (e) {
            console.error("Parse Message Error:", e);
        }
    }
};

// Expose to window
window.nearby = nearby;
