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
        const res = await fetch(`${API_BASE}${endpoint}`, config);
        const json = await res.json();
        if (!res.ok) throw new Error(json.error || 'Request failed');
        return json;
    } catch (e) {
        console.error("API Error:", e);
        throw e;
    }
}

export const api = {
    login: (username, password) => request('/auth/login', 'POST', { username, password }),
    signup: (username, password, name) => request('/auth/signup', 'POST', { username, password, name }),
    getHistory: (since, chatId, type) => {
        const queryParams = new URLSearchParams();
        if (since) queryParams.append('since', since);
        if (chatId) queryParams.append('chatId', chatId);
        if (type) queryParams.append('type', type);
        queryParams.append('_t', Date.now()); // Cache Buster
        return request(`/chat/history?${queryParams.toString()}`, 'GET');
    },
    sendMessage: (content, type = 'text', receiverId = 'general', replyToId = null) => request('/chat/send', 'POST', { content, type, receiverId, replyToId }),
    searchUsers: (q) => request(`/users/search?q=${encodeURIComponent(q)}`, 'GET'),
    updateProfile: (data) => request('/user/update', 'POST', data),
    updatePushToken: (token) => request('/user/push-token', 'POST', { token }),
    sendTestNotification: () => request('/user/test-push', 'POST', {}),
    getMe: () => request(`/user/me?_t=${Date.now()}`, 'GET'),
    getMe: () => request(`/user/me?_t=${Date.now()}`, 'GET'),
    batchGetUsers: (ids) => request('/users/batch', 'POST', { ids }),
    blockUser: (userId, action) => request('/user/block', 'POST', { userId, action }),
    blockUser: (userId, action) => request('/user/block', 'POST', { userId, action }),
    reportUser: (targetedUserId, reason) => request('/user/report', 'POST', { targetedUserId, reason }),
    markAsRead: (chatId) => request('/chat/read', 'POST', { chatId }),
    sendTyping: (chatId) => request('/chat/typing', 'POST', { chatId }),
    getTypingStatus: (chatId) => request(`/chat/typing?chatId=${chatId}`, 'GET'),
    markAsDelivered: (messageIds) => request('/chat/deliver', 'POST', { messageIds }),
    createGroup: (name, members) => request('/groups/create', 'POST', { name, members }),
    getGroups: () => request('/groups/list', 'GET'),
    getRecentChats: () => request('/chat/list', 'GET'),
    changePassword: (oldPassword, newPassword) => request('/auth/change-password', 'POST', { oldPassword, newPassword }),
    deleteAccount: (password) => request('/auth/delete-account', 'POST', { password }),

    // Message Actions
    deleteMessage: (messageId, mode) => request('/chat/actions', 'POST', { action: 'delete', messageId, mode }),
    editMessage: (messageId, newContent) => request('/chat/actions', 'POST', { action: 'edit', messageId, newContent }),
    starMessage: (messageId) => request('/chat/actions', 'POST', { action: 'star', messageId }),
    pinMessage: (messageId) => request('/chat/actions', 'POST', { action: 'pin', messageId })
};
