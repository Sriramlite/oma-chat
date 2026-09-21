export const db = {
    dbName: 'OMADatabase',
    version: 2,
    db: null,

    async open() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(this.dbName, this.version);

            request.onupgradeneeded = (event) => {
                const db = event.target.result;
                // Store chat messages
                if (!db.objectStoreNames.contains('messages')) {
                    const store = db.createObjectStore('messages', { keyPath: 'id' });
                    store.createIndex('chatId', 'chatId', { unique: false });
                    store.createIndex('timestamp', 'timestamp', { unique: false });
                }
                // Store pending requests for sync
                if (!db.objectStoreNames.contains('queue')) {
                    db.createObjectStore('queue', { keyPath: 'id', autoIncrement: true });
                }
                // Store active chats list
                if (!db.objectStoreNames.contains('chats')) {
                    const chatStore = db.createObjectStore('chats', { keyPath: 'id' });
                    chatStore.createIndex('userId', 'userId', { unique: false });
                } else {
                    const tx = event.target.transaction;
                    const chatStore = tx.objectStore('chats');
                    if (!chatStore.indexNames.contains('userId')) {
                        chatStore.createIndex('userId', 'userId', { unique: false });
                    }
                }
            };

            request.onsuccess = (event) => {
                this.db = event.target.result;
                resolve(this.db);
            };

            request.onerror = (event) => {
                console.error("IndexedDB error:", event.target.error);
                reject(event.target.error);
            };
        });
    },

    async saveMessages(messages) {
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction(['messages'], 'readwrite');
            const store = transaction.objectStore('messages');

            // Handle single message or array
            const msgs = Array.isArray(messages) ? messages : [messages];

            msgs.forEach(msg => {
                // Ensure we have a chatId to index by
                // If it's a DM, we might need to derive it depending on the view
                // For now, we assume msg has necessary fields or we trust the API response structure
                if (msg.id) {
                    store.put(msg);
                }
            });

            transaction.oncomplete = () => resolve();
            transaction.onerror = (e) => reject(e);
        });
    },

    async getMessages(chatId) {
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction(['messages'], 'readonly');
            const store = transaction.objectStore('messages');
            const index = store.index('chatId');

            // We want messages for a specific chat
            // Note: In a real app, complex queries might be needed (e.g. DMs where chatId is variable)
            // Here we assume 'chatId' property exists on message objects as per api logic
            // If API doesn't return chatId in the message object, we might need to inject it before saving.

            // ACTUALLY: The API messages usually have senderId/receiverId. 
            // We might need to query by those or inject a 'chatId' field when saving.
            // Let's assume for now we filter in memory if needed, or query all.
            // But getting ALL messages is expensive.

            // Let's try getting all for now as a fallback if index fails, 
            // but ideally we rely on the implementation in sync.js to ensure 'chatId' is saved.

            const request = chatId ? index.getAll(chatId) : store.getAll();

            request.onsuccess = () => {
                resolve(request.result || []);
            };
            request.onerror = (e) => reject(e);
        });
    },

    // Fallback: Get all messages and filter manually (slower but safer if chatId logic is complex)
    async getAllMessages() {
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction(['messages'], 'readonly');
            const store = transaction.objectStore('messages');
            const request = store.getAll();
            request.onsuccess = () => resolve(request.result);
            request.onerror = (e) => reject(e);
        });
    },

    async addToQueue(requestData) {
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction(['queue'], 'readwrite');
            const store = transaction.objectStore('queue');
            const item = {
                ...requestData,
                timestamp: Date.now()
            };
            store.add(item);
            transaction.oncomplete = () => resolve(item);
            transaction.onerror = (e) => reject(e);
        });
    },

    async getQueue() {
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction(['queue'], 'readonly');
            const store = transaction.objectStore('queue');
            const request = store.getAll();
            request.onsuccess = () => resolve(request.result);
            request.onerror = (e) => reject(e);
        });
    },

    async removeFromQueue(id) {
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction(['queue'], 'readwrite');
            const store = transaction.objectStore('queue');
            store.delete(id);
            transaction.oncomplete = () => resolve();
            transaction.onerror = (e) => reject(e);
        });
    },

    async saveChats(chats, userId) {
        if (!userId || !Array.isArray(chats)) return;
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            try {
                const transaction = this.db.transaction(['chats'], 'readwrite');
                const store = transaction.objectStore('chats');
                chats.forEach(chat => {
                    if (chat && chat.id) {
                        store.put({
                            ...chat,
                            id: `${userId}_${chat.id}`,
                            chatId: chat.id,
                            userId: userId
                        });
                    }
                });
                transaction.oncomplete = () => resolve();
                transaction.onerror = (e) => reject(e);
            } catch (e) {
                reject(e);
            }
        });
    },

    async getChats(userId) {
        if (!userId) return [];
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            try {
                const transaction = this.db.transaction(['chats'], 'readonly');
                const store = transaction.objectStore('chats');
                if (store.indexNames.contains('userId')) {
                    const index = store.index('userId');
                    const request = index.getAll(userId);
                    request.onsuccess = () => {
                        const res = (request.result || []).map(c => ({
                            ...c,
                            id: c.chatId || c.id.replace(`${userId}_`, '')
                        }));
                        resolve(res);
                    };
                    request.onerror = (e) => reject(e);
                } else {
                    resolve([]);
                }
            } catch (e) {
                resolve([]);
            }
        });
    },

    async clearChatsForUser(userId) {
        if (!userId) return;
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            try {
                const transaction = this.db.transaction(['chats'], 'readwrite');
                const store = transaction.objectStore('chats');
                if (store.indexNames.contains('userId')) {
                    const index = store.index('userId');
                    const request = index.openKeyCursor(IDBKeyRange.only(userId));
                    request.onsuccess = (e) => {
                        const cursor = e.target.result;
                        if (cursor) {
                            store.delete(cursor.primaryKey);
                            cursor.continue();
                        }
                    };
                }
                transaction.oncomplete = () => resolve();
                transaction.onerror = (e) => reject(e);
            } catch (e) {
                resolve();
            }
        });
    },

    async clear() {
        if (!this.db) await this.open();
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction(['chats', 'messages', 'queue'], 'readwrite');
            transaction.objectStore('chats').clear();
            transaction.objectStore('messages').clear();
            // Keep queue? Maybe not if logging out.
            transaction.objectStore('queue').clear();
            transaction.oncomplete = () => resolve();
            transaction.onerror = (e) => reject(e);
        });
    }
};
