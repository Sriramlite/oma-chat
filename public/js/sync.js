import { db } from './db.js';
import { api } from './api.js';

export const sync = {
    isOnline: navigator.onLine,

    init() {
        window.addEventListener('online', () => {
            this.isOnline = true;
            console.log("App is online. Processing queue...");
            this.processQueue();
            // Trigger a UI update event?
            window.dispatchEvent(new CustomEvent('network-status', { detail: { online: true } }));
        });

        window.addEventListener('offline', () => {
            this.isOnline = false;
            console.log("App is offline.");
            window.dispatchEvent(new CustomEvent('network-status', { detail: { online: false } }));
        });

        // Try processing on start
        this.processQueue();
    },

    async addToQueue(endpoint, method, data) {
        console.log("Adding request to offline queue:", endpoint);
        return db.addToQueue({ endpoint, method, data });
    },

    async processQueue() {
        if (!this.isOnline) return;

        const queue = await db.getQueue();
        if (queue.length === 0) return;

        console.log(`Processing ${queue.length} offline requests...`);

        for (const req of queue) {
            try {
                // We use api.request but we need to bypass the "add to queue on fail" logic
                // to avoid infinite loops if it fails again.
                // Actually, api.request handles the fetch. If we call it properly, it should be fine.
                // But we need a way to tell api.request "don't queue this if it fails, just throw".

                // Since api.request imports sync, we should avoid circular dependency issues or logic loops.
                // We'll call a raw fetch or a special method in api? 
                // Alternatively, we just try/catch here and if it fails, we leave it in queue.

                // Let's use the low-level logic from api.request essentially re-implemented or exposed.
                // For simplicity, let's assume `api.request` has a flag or we can just import `request` logic.
                // But `api.js` exports `api` object.

                // To avoid circular refs effectively, we'll pass the functionality or just use fetch directly 
                // reconstructing headers exactly like api.js does.

                const userStr = localStorage.getItem('oma_user');
                const headers = { 'Content-Type': 'application/json' };
                if (userStr) {
                    const user = JSON.parse(userStr);
                    if (user.token) headers['Authorization'] = `Bearer ${user.token}`;
                }

                const config = {
                    method: req.method,
                    headers,
                    body: req.data ? JSON.stringify(req.data) : null
                };

                // Helper to get base url
                // Note: We need to match what's in api.js. 
                // Ideally this should be shared constant.
                const API_BASE = 'https://oma-chat-app-pho0.onrender.com/api';

                const res = await fetch(`${API_BASE}${req.endpoint}`, config);

                if (res.ok) {
                    // Success! Remove from queue
                    await db.removeFromQueue(req.id);
                    console.log("Processed queue item:", req.id);
                } else {
                    console.warn("Retrying queue item failed:", res.status);
                    // Leave in queue to try again later? 
                    // Or if 4xx error (client error), maybe remove it because it will never succeed?
                    if (res.status >= 400 && res.status < 500) {
                        console.error("Removing failed client-side request from queue:", req);
                        await db.removeFromQueue(req.id);
                    }
                }

            } catch (e) {
                console.error("Queue processing error:", e);
                // Likely still offline or network unstable. Stop processing.
                return;
            }
        }
    },

    // Helper to enrich messages with chatId before saving if needed
    async saveMessages(messages, chatId = null) {
        // If chatId is provided, ensure all messages have it.
        // If not, we hope the message object has it or we deduce it.
        const msgs = Array.isArray(messages) ? messages : [messages];
        const enriched = msgs.map(m => {
            if (chatId && !m.chatId) {
                return { ...m, chatId };
            }
            return m;
        });
        return db.saveMessages(enriched);
    }
};
