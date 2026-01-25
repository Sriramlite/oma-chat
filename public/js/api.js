import { db } from './db.js';
import { sync } from './sync.js';

// For Android App/Local Dev enabling cross-origin to prod:
const API_BASE = 'https://oma-chat-app-pho0.onrender.com/api';
// const API_BASE = '/api'; // Uncomment for strict local-only dev

async function request(endpoint, method = 'GET', data = null) {
    const headers = { 'Content-Type': 'application/json' };
    const userStr = localStorage.getItem('oma_user');
    if (userStr) {
        const user = JSON.parse(userStr);
        if (user.token) headers['Authorization'] = `Bearer ${user.token}`;
    }

    const config = {
        method,
        headers,
        body: data ? JSON.stringify(data) : null
    };

    try {
        if (!navigator.onLine) {
            throw new Error('Offline');
        }
        const res = await fetch(`${API_BASE}${endpoint}`, config);
        const json = await res.json();
        if (!res.ok) {
            const err = new Error(json.error || 'Request failed');
            err.response = { data: json };
            throw err;
        }
        return json;
    } catch (e) {
        console.warn("API Error (or Offline):", e);

        // Offline Handling
        if (!navigator.onLine || e.message === 'Offline' || e.message.includes('Failed to fetch')) {
            console.log("Offline mode triggered for:", endpoint);

            // 1. GET Requests: Try to fetch from DB
            if (method === 'GET') {
                if (endpoint.includes('/chat/history')) {
                    // Extract chatId from URL if possible. 
                    // Implementation of getHistory below constructs query string.
                    // We need to parse it back or just return *all* messages for now.
                    // Let's rely on the caller to handle empty/stale data if needed, 
                    // but ideally we return what we have.
                    const match = endpoint.match(/chatId=([^&]*)/);
                    const chatId = match ? match[1] : null;
                    if (chatId) {
                        const cached = await db.getMessages(chatId);
                        if (cached && cached.length > 0) return cached;
                    }
                    else if (endpoint.includes('chatId=general')) {
                        const cached = await db.getMessages('general');
                        if (cached && cached.length > 0) return cached;
                    }
                }
                if (endpoint.includes('/chat/list')) { // Recent Chats
                    const cached = await db.getChats();
                    if (cached && cached.length > 0) return cached;
                }
            }

            // 2. POST Requests: Queue it
            if (method === 'POST') {
                // Only queue specific actions (sending messages)
                if (endpoint === '/chat/send') {
                    await sync.addToQueue(endpoint, method, data);
                    // Return a fake "success" response so UI updates optimistically
                    const tempId = 'temp-' + Date.now();
                    return {
                        id: tempId,
                        content: data.content,
                        type: data.type,
                        senderId: JSON.parse(userStr).user.id, // minimal mock
                        timestamp: Date.now(),
                        status: 'sending'
                        // Note: The caller (app.js) handles temp messages separately, 
                        // but this return value mimics a successful API save for standard flow.
                    };
                }
            }
        }
        throw e;
    }
}

export const api = {
    login: (username, password) => request('/auth/login', 'POST', { username, password }),
    signup: (username, password, name) => request('/auth/signup', 'POST', { username, password, name }),
    verifyPhone: (idToken) => request('/auth/phone', 'POST', { idToken }),
    linkPhone: (idToken) => request('/user/link-phone', 'POST', { idToken }),
    getHistory: async (since, chatId, type) => {
        const queryParams = new URLSearchParams();
        if (since) queryParams.append('since', since);
        if (chatId) queryParams.append('chatId', chatId);
        if (type) queryParams.append('type', type);
        queryParams.append('_t', Date.now()); // Cache Buster
        const res = await request(`/chat/history?${queryParams.toString()}`, 'GET');

        // CACHE MESSAGES ON SUCCESS
        if (Array.isArray(res)) {
            // Need to ensure chatId is present. If fetching for specific chat, we know it.
            // If fetching 'all' (history loop), messages should ideally have it.
            await db.saveMessages(res);
        }
        return res;
    },
    sendMessage: async (content, type = 'text', receiverId = 'general', replyToId = null) => {
        const res = await request('/chat/send', 'POST', { content, type, receiverId, replyToId });
        // CACHE IF SUCCESS (Real message)
        if (res && res.id && !res.id.startsWith('temp-')) {
            // We need to store it with the correct chatId. 
            // In a group (general), chatId key is 'general' or similar? 
            // The message object usually has receiverId. 
            // If receiverId is a group, that's the chat ID. 
            // If receiverId is a user (DM), the chat ID logic is complex (usually both user IDs).
            // For now, let's just save the message object as is. 
            // `db.saveMessages` relies on `id`. indexing uses properties.
            await db.saveMessages(res);
        }
        return res;
    },
    searchUsers: (q) => request(`/users/search?q=${encodeURIComponent(q)}`, 'GET'),
    updateProfile: (data) => request('/user/update', 'POST', data),

    async deleteChat(chatId) {
        return request('/chat/delete_chat', 'POST', { chatId });
    },

    async manageGroup(groupId, memberId, action) {
        return request('/chat/manage_group', 'POST', { groupId, memberId, action });
    },

    updatePushToken: (token) => request('/user/push-token', 'POST', { token }),
    sendTestNotification: () => request('/user/test-push', 'POST', {}),
    getMe: () => request(`/user/me?_t=${Date.now()}`, 'GET'),
    batchGetUsers: (ids) => request('/users/batch', 'POST', { ids }),
    blockUser: (userId, action) => request('/user/block', 'POST', { userId, action }),
    reportUser: (targetedUserId, reason) => request('/user/report', 'POST', { targetedUserId, reason }),
    markAsRead: (chatId) => request('/chat/read', 'POST', { chatId }),
    sendTyping: (chatId) => request('/chat/typing', 'POST', { chatId }),
    getTypingStatus: (chatId) => request(`/chat/typing?chatId=${chatId}`, 'GET'),
    markAsDelivered: (messageIds) => request('/chat/deliver', 'POST', { messageIds }),
    createGroup: (name, members) => request('/groups/create', 'POST', { name, members }),
    getGroups: () => request('/groups/list', 'GET'),
    getRecentChats: async () => {
        const res = await request('/chat/list', 'GET');
        if (Array.isArray(res)) {
            await db.saveChats(res);
        }
        return res;
    },
    changePassword: (oldPassword, newPassword) => request('/auth/change-password', 'POST', { oldPassword, newPassword }),
    deleteAccount: (password) => request('/auth/delete-account', 'POST', { password }),

    // Message Actions
    deleteMessage: (messageId, mode) => request('/chat/actions', 'POST', { action: 'delete', messageId, mode }),
    editMessage: (messageId, newContent) => request('/chat/actions', 'POST', { action: 'edit', messageId, newContent }),
    starMessage: (messageId) => request('/chat/actions', 'POST', { action: 'star', messageId }),
    pinMessage: (messageId) => request('/chat/actions', 'POST', { action: 'pin', messageId })
};
