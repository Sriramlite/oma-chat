
import { registerPlugin, WebPlugin } from '../capacitor-core.js';

class DeviceWeb extends WebPlugin {
    async getInfo() {
        return {
            model: 'web',
            platform: 'web',
            osVersion: 'unknown',
            appVersion: '1.0.0',
            memUsed: 0,
            diskFree: 0,
            diskTotal: 0,
            manufacturer: 'unknown',
            isVirtual: false,
            webViewVersion: 'unknown'
        };
    }
    async getBatteryInfo() {
        if (typeof navigator.getBattery === 'function') {
            const b = await navigator.getBattery();
            return {
                batteryLevel: b.level,
                isCharging: b.charging
            };
        }
        return {
            batteryLevel: 1,
            isCharging: true
        };
    }
    async getLanguageCode() {
        return { value: navigator.language };
    }
    async getId() {
        return { uuid: 'web-uuid' };
    }
}

const Device = registerPlugin('Device', {
    web: () => new DeviceWeb()
});

export { Device };
