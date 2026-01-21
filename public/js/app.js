import { api } from './api.js';
import { PushNotifications } from './capacitor-push/index.js';
import { registerPlugin } from './capacitor-core.js';

const App = registerPlugin('App');

const state = {
    user: null,
    chats: [],
    messages: [],
    activeChatId: null,
    isSearching: false,
    searchResults: [],
    mobileView: 'list', // 'list' or 'chat'
    settingsView: null, // null, 'profile', 'appearance', etc.
    activeTab: 'messages', // 'messages', 'calls', 'contacts', 'profile'
    // onlineUsers: new Set(), 
    activeTab: 'messages', // 'messages', 'calls', 'contacts', 'profile'
    onlineUsers: new Set(), // Set of user IDs
    userStatuses: {} // Map of userId -> { online, lastSeen }
};

const soundManager = {
    sounds: {
        ringtone: new Audio(`sounds/ringtone.mp3?v=${Date.now()}`),
        calling: new Audio(`sounds/calling.mp3?v=${Date.now()}`),
        message: new Audio(`sounds/message.mp3?v=${Date.now()}`)
    },
    init() {
        this.sounds.ringtone.loop = true;
        this.sounds.calling.loop = true;
    },
    play(type) {
        try {
            if (this.sounds[type]) {
                const promise = this.sounds[type].play();
                if (promise !== undefined) {
                    promise.catch(e => console.log("Audio play failed (autoplay policy):", e));
                }
            }
        } catch (e) { console.error("Sound error", e); }
    },
    stop(type) {
        if (this.sounds[type]) {
            this.sounds[type].pause();
            this.sounds[type].currentTime = 0;
        }
    },
    stopAll() {
        this.stop('ringtone');
        this.stop('calling');
    },
    unlock() {
        // Play and pause all sounds silently to unlock audio context
        Object.values(this.sounds).forEach(sound => {
            sound.play().then(() => {
                sound.pause();
                sound.currentTime = 0;
            }).catch(() => { });
        });
        document.removeEventListener('click', soundManagerUnlocker);
        document.removeEventListener('touchstart', soundManagerUnlocker);
    }
};
soundManager.init();

const soundManagerUnlocker = () => {
    soundManager.unlock();
};
document.addEventListener('click', soundManagerUnlocker);
document.addEventListener('touchstart', soundManagerUnlocker);

// --- Initialization ---

async function init() {
    // setupTheme(); // Assuming this function exists elsewhere or will be added

    // Check Auth
    const user = localStorage.getItem('oma_user');
    if (user) {
        state.user = JSON.parse(user);
        initSocket(); // Enable Real-time

        // Load Chats
        const chats = localStorage.getItem('oma_chats');
        if (chats) state.chats = JSON.parse(chats);

        render(); // Initial Render
        // refreshSidebar(); // Assuming this function exists elsewhere or will be added

        // Register Push (Mobile)
        registerPush();
    } else {
        render(); // Render login/signup if not authenticated
    }

    // Back Button Handling (Capacitor)
    // Try imported App first, then fallback to global
    // Note: If using a bundler, 'App' should work. If script tag, 'window.Capacitor.Plugins.App'
    const AppPlugin = App || (window.Capacitor && window.Capacitor.Plugins ? window.Capacitor.Plugins.App : null);

    if (AppPlugin) {
        try {
            AppPlugin.addListener('backButton', async () => {
                // 1. Modals
                if (!document.getElementById('video-call-modal').classList.contains('hidden')) {
                    // Minify call logic if possible, else do nothing or hangup?
                    // User said "back closes app", checking for hangup might be accidental.
                    // Let's just return to prevent closing app.
                    return;
                }
                if (!document.getElementById('incoming-call-popup').classList.contains('hidden')) {
                    window.rejectCall();
                    return;
                }
                if (document.getElementById('group-modal-container')) {
                    window.closeGroupModal();
                    return;
                }

                // 2. Attachment/Emoji
                if (!document.getElementById('attachment-menu').classList.contains('hidden')) {
                    window.toggleAttachmentMenu();
                    return;
                }
                if (!document.getElementById('emoji-picker').classList.contains('hidden')) {
                    window.toggleEmojiPicker();
                    return;
                }

                // 3. Settings
                if (state.settingsView) {
                    window.closeSettings();
                    return;
                }

                // 4. Chat View (Mobile)
                if (state.mobileView === 'chat' && state.activeChatId) {
                    window.closeChat();
                    return;
                }

                // 5. Root (Tab View)
                // If not on 'messages' tab, switch to it first
                if (state.activeTab !== 'messages') {
                    window.switchTab('messages');
                } else {
                    AppPlugin.minimizeApp();
                }
            });
            console.log("Back Button Listener Attached Successfully");
        } catch (e) {
            console.warn("Back button setup failed", e);
        }
    }
}

// --- Navigation Logic ---

window.switchTab = (tab) => {
    if (tab === 'profile') {
        window.openSettings('profile'); // Reuse existing settings view for profile
        return;
        // Or actually switch tab if we want it to be a main view? 
        // User asked for "Profile" tab. Usually this means a profile view.
        // Let's make it a distinct tab view or just open settings. 
        // If I make it a tab, I need to handle "Settings" separately.
        // Let's treat 'profile' tab as a shortcut to the Profile Settings for now, keeping 'messages' active in bg?
        // No, visual tab matching is better.
    }
    state.activeTab = tab;
    state.settingsView = null; // Close settings if open
    render();
};

function renderBottomNav() {
    const tabs = [
        { id: 'messages', icon: 'fas fa-comment-alt', label: 'Messages' },
        { id: 'calls', icon: 'fas fa-phone-alt', label: 'Calls' },
        { id: 'contacts', icon: 'fas fa-address-book', label: 'Contacts' },
        { id: 'profile', icon: 'fas fa-user', label: 'Profile' }
    ];

    return `
        <div class="bottom-nav">
            ${tabs.map(tab => `
                <div class="nav-item ${state.activeTab === tab.id ? 'active' : ''}" onclick="window.switchTab('${tab.id}')">
                    <i class="${tab.icon}"></i>
                    <span>${tab.label}</span>
                </div>
            `).join('')}
        </div>
    `;
}

function render() {
    const app = document.getElementById('app');
    let hash = window.location.hash || '#login';

    if (state.user) {
        if (hash === '#login' || hash === '#signup') {
            window.location.hash = '#chat';
            return;
        }
    } else {
        if (hash !== '#login' && hash !== '#signup') {
            window.location.hash = '#login';
            return;
        }
    }

    app.className = '';

    if (hash.startsWith('#chat')) {
        const parts = hash.split('/');
        if (parts.length > 1 && parts[1]) {
            state.activeChatId = parts[1];
            state.mobileView = 'chat';
        } else {
            // Default view (list)
            state.mobileView = 'list';
            state.activeChatId = null;
        }
        renderChatLayout(app);
    } else if (hash === '#login') {
        renderLogin(app);
    } else if (hash === '#signup') {
        renderSignup(app);
    } else {
        app.innerHTML = '<h1>404</h1>';
    }
}

function renderLogin(container) {
    container.innerHTML = `
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Log in</h2>
                <form id="login-form">
                    <input type="text" id="username" placeholder="Username" required>
                    <input type="password" id="password" placeholder="Password" required>
                    <button type="submit">Log In</button>
                    <div style="text-align:center; margin: 15px 0; color: grey; font-size: 0.8rem;">OR</div>
                    <button type="button" class="secondary" onclick="window.switchPhoneLogin()" style="background: rgba(var(--primary-color-rgb), 0.1); color: var(--primary-color); border: 1px solid var(--primary-color);">Login with Phone</button>
                    <a href="#signup" style="display:block; margin-top:15px;">Create Account</a>
                    <div id="error-msg" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
    document.getElementById('login-form').onsubmit = handleLogin;
    document.getElementById('login-form').onsubmit = handleLogin;
}

// --- Phone Auth (SMS OTP) ---

window.switchPhoneLogin = () => {
    const app = document.getElementById('app');
    app.innerHTML = `
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Login with Phone</h2>
                <p style="color: grey; font-size: 0.85rem; margin-bottom: 20px;">Enter your phone number with country code (e.g. +1...)</p>
                <form id="phone-login-form">
                    <input type="tel" id="phoneNumber" placeholder="+1..." required>
                    <button type="submit" id="btn-send-otp">Send OTP</button>
                    <a href="#login" onclick="window.renderLogin(document.getElementById('app'))">Back to Login</a>
                    <div id="error-msg" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
    document.getElementById('phone-login-form').onsubmit = handleSendOTP;
};

window.renderOTPVerify = () => {
    const app = document.getElementById('app');
    app.innerHTML = `
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Verify OTP</h2>
                <p style="color: grey; font-size: 0.85rem; margin-bottom: 20px;">Enter the 6-digit code sent to your phone</p>
                <form id="otp-verify-form">
                    <input type="number" id="otpCode" placeholder="Enter 6-digit code" required maxlength="6">
                    <button type="submit">Verify & Login</button>
                    <button type="button" class="secondary" onclick="window.switchPhoneLogin()" style="margin-top:10px;">Change Number</button>
                    <div id="error-msg" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
    document.getElementById('otp-verify-form').onsubmit = handleVerifyOTP;
};

// Global for Firebase confirmation
let confirmationResult = null;

async function initFirebaseClient() {
    if (window.firebase && firebase.apps.length > 0) return;

    // IMPORTANT: For Web/PWA, these keys are public and required.
    // In a production app, these should be securely managed or injected during build.
    // If you are testing locally, these can be found in your Firebase Console Project Settings (Web App).
    const config = {
        apiKey: "AIzaSyDFUVWEfVEDdaT0iDA7_6EqqU6X3377fIE", // Restored original Web Key
        authDomain: "oma-chat-a1b8e.firebaseapp.com",
        projectId: "oma-chat-a1b8e",
        storageBucket: "oma-chat-a1b8e.firebasestorage.app",
        messagingSenderId: "836902266336",
        appId: "1:836902266336:web:60cd4bb9fbb170c3ed7785"
    };

    if (config.apiKey !== "YOUR_FIREBASE_API_KEY") {
        firebase.initializeApp(config);
    } else {
        console.warn("Firebase Web Config not found. Phone Auth might fail on Web. Please update apiKey in initFirebaseClient().");
    }
}

async function handleSendOTP(e) {
    e.preventDefault();
    await initFirebaseClient();

    const phone = document.getElementById('phoneNumber').value;
    const errorMsg = document.getElementById('error-msg');
    const btn = document.getElementById('btn-send-otp');

    btn.disabled = true;
    btn.innerText = 'Sending...';

    try {
        // Initialize reCAPTCHA
        if (!window.recaptchaVerifier) {
            window.recaptchaVerifier = new firebase.auth.RecaptchaVerifier('recaptcha-container', {
                'size': 'invisible'
            });
        }

        confirmationResult = await firebase.auth().signInWithPhoneNumber(phone, window.recaptchaVerifier);
        window.renderOTPVerify();
    } catch (error) {
        console.error("SMS Send Error:", error);
        errorMsg.innerText = error.message;
        btn.disabled = false;
        btn.innerText = 'Send OTP';
    }
}

async function handleVerifyOTP(e) {
    e.preventDefault();
    const code = document.getElementById('otpCode').value;
    const errorMsg = document.getElementById('error-msg');

    try {
        const result = await confirmationResult.confirm(code);
        const idToken = await result.user.getIdToken();

        // Send to backend for JWT
        const res = await api.verifyPhone(idToken);

        localStorage.setItem('oma_user', JSON.stringify(res));
        state.user = res;
        initSocket();

        if (res.isNew) {
            window.renderNameSetup();
        } else {
            window.location.hash = '#chat';
            render();
        }
    } catch (error) {
        console.error("OTP Verification Error:", error);
        let displayMsg = error.message || 'Verification failed';
        if (error.response?.data?.details) {
            displayMsg += `: ${error.response.data.details}`;
        }
        errorMsg.innerText = displayMsg;
    }
}

window.renderNameSetup = () => {
    const app = document.getElementById('app');
    app.innerHTML = `
        <div class="centered-view">
            <div class="auth-box animate__animated animate__zoomIn">
                <h2>Welcome!</h2>
                <p>Welcome to OMA. Let's finish setting up your profile.</p>
                <form id="name-setup-form" style="margin-top: 20px;">
                    <input type="text" id="displayName" placeholder="What's your name?" required>
                    <button type="submit">Start Chatting</button>
                    <div id="setup-error" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
    document.getElementById('name-setup-form').onsubmit = async (e) => {
        e.preventDefault();
        const name = document.getElementById('displayName').value;
        const btn = e.target.querySelector('button');
        btn.disabled = true;

        try {
            await api.updateProfile({ name });
            state.user.user.name = name;
            localStorage.setItem('oma_user', JSON.stringify(state.user));
            window.location.hash = '#chat';
            render();
        } catch (err) {
            document.getElementById('setup-error').innerText = err.message;
            btn.disabled = false;
        }
    };
};

function renderSignup(container) {
    container.innerHTML = `
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Sign Up</h2>
                <form id="signup-form">
                    <input type="text" id="username" placeholder="Username" required>
                    <input type="text" id="name" placeholder="Display Name">
                    <input type="password" id="password" placeholder="Password" required>
                    <button type="submit">Sign Up</button>
                    <a href="#login">Log In</a>
                    <div id="error-msg" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
    document.getElementById('signup-form').onsubmit = handleSignup;
}

async function handleLogin(e) {
    e.preventDefault();
    try {
        const u = document.getElementById('username').value;
        const p = document.getElementById('password').value;
        const res = await api.login(u, p);
        loginUser(res);
    } catch (err) {
        document.getElementById('error-msg').innerText = err.message;
    }
}

async function handleSignup(e) {
    e.preventDefault();
    try {
        const u = document.getElementById('username').value;
        const n = document.getElementById('name').value;
        const p = document.getElementById('password').value;
        const res = await api.signup(u, p, n);
        loginUser(res);
    } catch (err) {
        document.getElementById('error-msg').innerText = err.message;
    }
}

function renderChatLayout(container) {
    const appClass = (state.mobileView === 'chat') ? 'app-state-chat' : 'app-state-list';
    document.getElementById('app').className = appClass;

    let sidebarContent = '';
    if (state.settingsView) {
        sidebarContent = renderSettings();
    } else {
        sidebarContent = renderSidebarMain();
    }

    container.innerHTML = `
        <div class="chat-layout">
            <div class="sidebar" id="sidebar">
                ${sidebarContent}
            </div>
            <div class="chat-main">
                ${renderMainChatArea()}
            </div>
        </div>
    `;

    setupChatLogic();
    setupPullToRefresh();
}

function setupPullToRefresh() {
    const list = document.getElementById('chat-list');
    const indicator = document.getElementById('pull-indicator');
    if (!list || !indicator) return;

    let startY = 0;
    let isPulling = false;

    list.addEventListener('touchstart', (e) => {
        if (list.scrollTop === 0) {
            startY = e.touches[0].pageY;
            isPulling = true;
        }
    });

    list.addEventListener('touchmove', (e) => {
        if (!isPulling) return;
        const currentY = e.touches[0].pageY;
        const diff = currentY - startY;

        if (diff > 0 && diff < 150) {
            indicator.style.height = `${diff / 2}px`;
            e.preventDefault(); // Prevent body scroll
        }
    });

    list.addEventListener('touchend', (e) => {
        if (!isPulling) return;
        isPulling = false;
        const height = parseInt(indicator.style.height || '0');

        if (height > 50) {
            indicator.classList.add('loading');
            indicator.style.height = '60px';
            setTimeout(() => {
                window.location.reload();
            }, 1000);
        } else {
            indicator.style.height = '0';
        }
    });
}

// Helper for Avatar consistency
function getAvatarUrl(chat) {
    if (chat.avatar) return chat.avatar;
    const seed = chat.name || chat.username || 'User';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(seed)}&background=random`;
}

function renderSidebarMain() {
    let content = '';

    if (state.activeTab === 'messages') {
        content = renderMessagesView();
    } else if (state.activeTab === 'calls') {
        content = renderCallsView();
    } else if (state.activeTab === 'contacts') {
        content = renderContactsView();
    } else if (state.activeTab === 'profile') {
        // We can reuse the Profile Settings render, or build a dedicated wrapper
        // Use a wrapper to keep the bottom nav visible
        content = `
            <div class="sidebar-header"><h3>My Profile</h3></div>
            <div class="settings-list" style="padding-top:10px;">
                ${renderProfileContent()}
            </div>
        `;
    }

    return `
        ${content}
        ${renderBottomNav()}
    `;
}

function renderMessagesView() {
    let chatList = [];
    if (state.isSearching) {
        chatList = state.searchResults;
    } else {
        const general = { id: 'general', name: 'General Group', lastMsg: 'Tap to chat', avatar: 'https://ui-avatars.com/api/?name=General+Group&background=random', time: '' };
        chatList = [general, ...state.chats];
    }

    return `
        <div class="sidebar-header">
            <div class="user-pill" onclick="window.switchTab('profile')">
                <img src="${getAvatarUrl(state.user?.user || {})}" class="avatar-small">
                 <span style="font-weight:600;">${state.user?.user.name}</span>
            </div>
            <div style="display:flex;gap:5px;">
                 <button class="icon-btn" onclick="window.openGroupModal()" title="New Group"><i class="fas fa-plus-square"></i></button>
                 <button class="icon-btn" onclick="window.openSettings('main')" title="Settings"><i class="fas fa-cog"></i></button>
            </div>
        </div>
        <div class="sidebar-search">
             <div class="search-wrapper">
                <i class="fas fa-search search-icon"></i>
                <input type="text" id="user-search" placeholder="Search chats..." oninput="window.handleSearch(this.value)">
             </div>
        </div>
        <div class="chat-list" id="chat-list">
             <div class="pull-indicator" id="pull-indicator"><i class="fas fa-spinner"></i></div>
            ${chatList.map(chat => {
        const isUnread = chat.unreadCount > 0;
        return `
                <div class="chat-item ${chat.id === state.activeChatId ? 'active' : ''}" onclick="window.openChat('${chat.id}')">
                    <div class="avatar-wrapper">
                        <img src="${getAvatarUrl(chat)}">
                        ${state.onlineUsers.has(chat.id) ? '<div class="status-dot"></div>' : ''}
                    </div>
                    <div class="chat-info">
                        <div style="display:flex;justify-content:space-between;align-items:center;">
                            <h4 style="${isUnread ? 'font-weight: 800; color: var(--text-primary);' : ''}">${chat.name || chat.username}</h4>
                            <span style="font-size:0.75rem; color: ${isUnread ? 'var(--primary-color)' : 'var(--text-secondary)'};">
                               ${chat.time ? timeAgo(chat.time) : ''}
                            </span>
                        </div>
                        <div style="display:flex;justify-content:space-between;align-items:center;">
                            <p style="${isUnread ? 'font-weight: 700; color: var(--text-primary);' : 'color: var(--text-secondary);'}">
                                ${chat.lastMsg || (chat.username ? '@' + chat.username : '')}
                            </p>
                            ${isUnread ? `<div style="background:var(--primary-color);color:white;border-radius:50%;padding:2px 6px;font-size:0.7rem;">${chat.unreadCount}</div>` : ''}
                        </div>
                    </div>
                </div>
            `}).join('')}
             ${state.isSearching && chatList.length === 0 ? '<div style="padding:20px;text-align:center;color:grey;">No users found</div>' : ''}
        </div>
    `;
}

function renderCallsView() {
    // If we haven't loaded calls yet or want to refresh, we show loading.
    // We'll rely on a global or module-level `window.loadCallHistory()` to populate this.
    // Check if we have cached calls? No, let's fetch fresh.

    // Trigger load in background (debounce/check if already loading)
    setTimeout(() => window.loadCallHistory(), 0);

    return `
        <div class="sidebar-header"><h3>Recent Calls</h3></div>
        <div class="chat-list" id="calls-list">
             <div style="padding:40px;text-align:center;color:grey;">
                <i class="fas fa-spinner fa-spin"></i> Loading...
             </div>
        </div>
    `;
}

window.loadCallHistory = async () => {
    const container = document.getElementById('calls-list');
    if (!container) return;

    try {
        // Fetch all history (no type filter) to bypass backend filter issues
        const allLogs = await api.getHistory(0, 'all', null);

        // Client-Side Filter for Call Logs
        const logs = allLogs.filter(m => m.type === 'call_log');

        if (logs.length === 0) {
            container.innerHTML = `
                <div style="padding:40px;text-align:center;color:grey;display:flex;flex-direction:column;align-items:center;">
                    <i class="fas fa-phone-slash" style="font-size:3rem;margin-bottom:15px;opacity:0.3;"></i>
                    <p>No recent call history found.</p>
                </div>`;
            return;
        }


        // Render Logs (Newest First)
        logs.reverse();

        // Note: logs contains messages. We need to find the "partner" for each log.
        // If senderId == me, partner is receiverId.
        // If receiverId == me, partner is senderId.

        container.innerHTML = logs.map(msg => {
            const isMe = msg.senderId === state.user.user.id;
            const partnerId = isMe ? msg.receiverId : msg.senderId;
            const partnerName = isMe ? (state.chats.find(c => c.id === partnerId)?.name || 'Unknown User') : msg.senderName;

            // Resolve Avatar using existing chat data if available, or fallback
            const chatObj = state.chats.find(c => c.id === partnerId);
            const avatar = chatObj ? getAvatarUrl(chatObj) : 'https://ui-avatars.com/api/?name=' + encodeURIComponent(partnerName);

            let icon = 'fa-phone';
            let color = 'var(--text-secondary)';
            let label = msg.content; // "Answered...", "Declined"

            if (label.includes('Answered')) {
                icon = 'fa-phone';
                color = '#22c55e'; // Green
                if (!isMe) icon = 'fa-phone-volume'; // Incoming answered
            } else if (label.includes('Declined')) {
                icon = 'fa-phone-slash';
                color = '#ef4444'; // Red
            } else if (label.includes('No Answer') || label.includes('Missed')) {
                icon = 'fa-phone-slash';
                color = '#f59e0b'; // Orange

                // If I see a "No Answer" message from someone else, it's a "Missed Call" for me.
                if (!isMe && label.includes('No Answer')) {
                    label = 'Missed Call';
                }
            }

            // Parse Time
            const date = new Date(msg.timestamp);
            const timeStr = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            const dateStr = date.toLocaleDateString();

            return `
                <div class="chat-item" style="cursor:default;">
                    <img src="${avatar}">
                    <div class="chat-info">
                        <h4>${partnerName}</h4>
                        <div style="display:flex;align-items:center;gap:8px;font-size:0.8rem;color:var(--text-secondary);">
                            <i class="fas ${icon}" style="font-size:0.8rem; color:${color}"></i>
                            <p>${label}</p>
                            <span style="font-size:0.7rem;opacity:0.6;margin-left:5px;">• ${dateStr} ${timeStr}</span>
                        </div>
                    </div>
                     <button class="icon-btn" onclick="window.startCall('audio', '${partnerId}')" style="margin-left:auto;color:var(--primary-color);">
                        <i class="fas fa-phone"></i>
                    </button>
                </div>
            `;
        }).join('');

    } catch (e) {
        console.error("Failed to load calls", e);
        container.innerHTML = `<div style="padding:20px;text-align:center;color:red;">Failed to load history</div>`;
    }
};

function renderContactsView() {
    // Placeholder for Contacts (Reuse Search Logic potentially?)
    return `
        <div class="sidebar-header"><h3>Contacts</h3></div>
        <div class="sidebar-search">
             <div class="search-wrapper">
                <i class="fas fa-search search-icon"></i>
                <input type="text" placeholder="Search users..." oninput="window.handleSearch(this.value)">
             </div>
        </div>
         <div class="chat-list" id="chat-list">
            ${state.isSearching ?
            state.searchResults.map(u => `
                    <div class="chat-item" onclick="window.openChat('${u.id}')">
                         <img src="${getAvatarUrl(u)}">
                        <div class="chat-info">
                            <h4>${u.name}</h4>
                            <p>@${u.username}</p>
                        </div>
                    </div>
                `).join('')
            : `<div style="padding:40px;text-align:center;color:grey;">Start typing to find people...</div>`
        }
         </div>
    `;
}

// Reuse the Profile Content logic from Settings
function renderProfileContent() {
    return `
        <div class="profile-section">
            <div style="position:relative;cursor:pointer;" onclick="document.getElementById('avatar-input').click()">
                <img src="${getAvatarUrl(state.user?.user || {})}" class="profile-avatar-large">
                <div style="position:absolute;bottom:0;right:0;background:var(--primary-color);color:white;padding:8px;border-radius:50%;">
                    <i class="fas fa-camera"></i>
                </div>
            </div>
            <input type="file" id="avatar-input" style="display:none;" accept="image/*" onchange="window.updateAvatar(this)">
            
            <div class="input-group">
                <label>Name</label>
                <input type="text" id="settings-name" value="${state.user?.user.name}">
            </div>
            <div class="input-group">
                <label>Bio</label>
                <input type="text" id="settings-bio" value="${state.user?.user.bio || ''}" placeholder="Add a bio">
            </div>
            <button class="primary" style="width:100%;margin-top:10px;" onclick="window.saveProfile()">Save Changes</button>
             <button class="secondary" style="width:100%;margin-top:10px;" onclick="window.logout()">Log Out</button>
        </div>
     `;
}

function renderSettings() {
    // Dispatch to specific settings renderer
    switch (state.settingsView) {
        case 'profile': return renderSettingsProfile();
        case 'appearance': return renderSettingsAppearance();
        case 'privacy': return renderSettingsPrivacy();
        case 'blocked': return renderSettingsBlocked();
        case 'account': return renderSettingsAccount();
        // Add other cases here as we build them. Default to main.
        default: return renderSettingsMain();
    }
}

function renderSettingsMain() {
    return `
        <div class="sidebar-header">
             <button class="icon-btn" onclick="window.closeSettings()"><i class="fas fa-arrow-left"></i></button>
             <h3>Settings</h3>
        </div>
        <div class="settings-content settings-slide-in">
             <div class="settings-list">
                
                <!-- Profile Snippet -->
                <div class="settings-item" onclick="window.openSettings('profile')">
                    <img src="${getAvatarUrl(state.user?.user || {})}" class="profile-avatar-large" style="width:50px;height:50px;border-width:2px;margin-right:16px;">
                    <div class="settings-text">
                        <h4>${state.user?.user.name}</h4>
                        <p style="opacity:0.7;">${state.user?.user.bio || 'Set a bio...'}</p>
                    </div>
                    <i class="fas fa-chevron-right settings-arrow"></i>
                </div>
                
                <div class="settings-section-header">Settings</div>

                <div class="settings-item" onclick="window.openSettings('account')">
                    <div class="settings-icon-container" style="background:linear-gradient(135deg, #f59e0b, #d97706);"><i class="fas fa-key" style="color:white;"></i></div>
                    <div class="settings-text">
                        <h4>Account</h4>
                    </div>
                    <i class="fas fa-chevron-right settings-arrow"></i>
                </div>

                <div class="settings-item" onclick="window.openSettings('privacy')">
                    <div class="settings-icon-container" style="background:linear-gradient(135deg, #10b981, #059669);"><i class="fas fa-lock" style="color:white;"></i></div>
                    <div class="settings-text">
                        <h4>Privacy</h4>
                    </div>
                    <i class="fas fa-chevron-right settings-arrow"></i>
                </div>

                <div class="settings-item" onclick="window.openSettings('appearance')">
                    <div class="settings-icon-container" style="background:linear-gradient(135deg, #3b82f6, #2563eb);"><i class="fas fa-palette" style="color:white;"></i></div>
                    <div class="settings-text">
                        <h4>Appearance</h4>
                    </div>
                    <i class="fas fa-chevron-right settings-arrow"></i>
                </div>

                 <div class="settings-item" onclick="window.logout()">
                    <div class="settings-icon-container" style="background:linear-gradient(135deg, #ef4444, #dc2626);"><i class="fas fa-sign-out-alt" style="color:white;"></i></div>
                    <div class="settings-text">
                        <h4>Log Out</h4>
                    </div>
                </div>

             </div>
        </div>
    `;
}

function renderSettingsProfile() {
    return `
        <div class="sidebar-header">
             <button class="icon-btn" onclick="window.openSettings('main')"><i class="fas fa-arrow-left"></i></button>
             <h3>Edit Profile</h3>
        </div>
        <div class="settings-content settings-slide-in">
             <div class="profile-section">
                <div style="position:relative;cursor:pointer;" onclick="document.getElementById('avatar-input').click()">
                    <img src="${getAvatarUrl(state.user?.user || {})}" class="profile-avatar-large">
                    <div style="position:absolute;bottom:0;right:0;background:var(--primary-color);color:white;padding:8px;border-radius:50%;">
                        <i class="fas fa-camera"></i>
                    </div>
                </div>
                <input type="file" id="avatar-input" style="display:none;" accept="image/*" onchange="window.updateAvatar(this)">
                
                <div class="input-group">
                    <label>Name</label>
                    <input type="text" id="settings-name" value="${state.user?.user.name}">
                </div>
                <div class="input-group">
                    <label>Bio</label>
                    <input type="text" id="settings-bio" value="${state.user?.user.bio || ''}" placeholder="Add a bio">
                </div>
                <button class="primary" style="width:100%;margin-top:10px;" onclick="window.saveProfile()">Save Changes</button>
                <button class="secondary" style="width:100%;margin-top:10px;background:#4f46e5;" onclick="window.testNotification()">🔔 Test Notification</button>
             </div>
        </div>
    `;
}

function renderSettingsPrivacy() {
    const s = state.user?.user?.settings || { lastSeenPrivacy: 'everyone', readReceipts: true };

    return `
        <div class="sidebar-header">
             <button class="icon-btn" onclick="window.openSettings('main')"><i class="fas fa-arrow-left"></i></button>
             <h3>Privacy</h3>
        </div>
        <div class="settings-content settings-slide-in">
             <div class="settings-list">
                
                <div class="settings-section-header">Last Seen & Status</div>
                <div class="settings-item">
                    <div class="settings-text" style="flex-direction:row; justify-content:space-between; display:flex; align-items:center; width:100%;">
                        <div>
                            <h4>Who can see my Last Seen</h4>
                        </div>
                        <select id="privacy-lastseen" onchange="window.savePrivacy()" style="background:#0f172a; color:white; border:1px solid #334155; padding:5px; border-radius:8px;">
                            <option value="everyone" ${!s.lastSeenPrivacy || s.lastSeenPrivacy === 'everyone' ? 'selected' : ''}>Everyone</option>
                            <option value="nobody" ${s.lastSeenPrivacy === 'nobody' ? 'selected' : ''}>Nobody</option>
                        </select>
                    </div>
                </div>

                <div class="settings-section-header">Messaging</div>
                <div class="settings-item">
                    <div class="settings-text">
                        <h4>Read Receipts</h4>
                        <p>If turned off, you won't send or receive read receipts.</p>
                    </div>
                    <label class="switch">
                        <input type="checkbox" id="privacy-readreceipts" ${s.readReceipts ? 'checked' : ''} onchange="window.savePrivacy()">
                        <span class="slider"></span>
                    </label>
                </div>

                <div class="settings-section-header">Connections</div>
                <div class="settings-item" onclick="window.openBlockedSettings()">
                    <div class="settings-text">
                        <h4>Blocked Users</h4>
                        <p>${state.user?.user?.blockedUsers?.length || 0} users</p>
                    </div>
                    <i class="fas fa-chevron-right settings-arrow"></i>
                </div>

             </div>
        </div>
    `;
}

function renderSettingsBlocked() {
    // We expect state.blockedUsersDetails to be populated or we show loading
    const blockedList = state.blockedUsersDetails || [];

    return `
        <div class="sidebar-header">
             <button class="icon-btn" onclick="window.openSettings('privacy')"><i class="fas fa-arrow-left"></i></button>
             <h3>Blocked Users</h3>
        </div>
        <div class="settings-content settings-slide-in">
             <div class="settings-list">
                ${blockedList.length === 0 ? '<div style="padding:20px; text-align:center; color:gray;">No blocked users</div>' : ''}
                ${blockedList.map(u => `
                    <div class="settings-item" style="cursor:default;">
                        <img src="${u.avatar}" class="avatar-small">
                        <div class="settings-text">
                            <h4>${u.name}</h4>
                            <p>@${u.username}</p>
                        </div>
                        <button class="icon-btn" style="color:red; font-size:0.9rem; border:1px solid red; border-radius:4px; padding:4px 8px;" onclick="window.unblockUser('${u.id}')">Unblock</button>
                    </div>
                `).join('')}
             </div>
        </div>
    `;
}

function renderSettingsAppearance() {
    const isDark = document.body.classList.contains('dark-mode');
    return `
        <div class="sidebar-header">
             <button class="icon-btn" onclick="window.openSettings('main')"><i class="fas fa-arrow-left"></i></button>
             <h3>Appearance</h3>
        </div>
        <div class="settings-content settings-slide-in">
             <div class="settings-list">
                <div class="settings-item">
                    <div class="settings-text">
                        <h4>Dark Mode</h4>
                        <p>Easier on the eyes</p>
                    </div>
                    <label class="switch">
                        <input type="checkbox" ${isDark ? 'checked' : ''} onchange="window.toggleDarkMode(this)">
                        <span class="slider"></span>
                    </label>
                </div>
                 <div class="settings-item" onclick="window.clearChats()">
                    <div class="settings-text">
                         <h4 style="color:red;">Clear Local History</h4>
                    </div>
                </div>
             </div>
        </div>
    `;
}

function renderMainChatArea() {
    let activeChat = state.chats.find(c => c.id === state.activeChatId);

    if (!activeChat && state.activeChatId === 'general') {
        activeChat = { id: 'general', name: 'General Group', avatar: 'https://ui-avatars.com/api/?name=General+Group&background=random' };
    }

    if (!activeChat) {
        // Try search results just in case
        activeChat = state.searchResults.find(c => c.id === state.activeChatId);
    }

    if (!activeChat) {
        // Fallback since we might have opened via URL
        activeChat = { name: 'Chat', avatar: 'https://ui-avatars.com/api/?name=?' };
    }

    return `
        <div class="chat-header">
            <div class="chat-header-user">
                <button class="back-btn" onclick="window.closeChat()"><i class="fas fa-arrow-left"></i></button>
                    <img src="${getAvatarUrl(activeChat)}" id="header-avatar">
                    <div>
                    <h4 style="margin:0;" id="header-name">${activeChat.name || activeChat.username}</h4>
                <span id="header-status" style="font-size:0.8rem;color:var(--text-secondary); transition: all 0.3s;">
                    ${getHeaderStatusText(activeChat)}
                </span>
                </div>
            </div>
            
            <!-- SEARCH BAR (Hidden by default) -->
            <div id="chat-search-bar" style="display:none; flex:1; align-items:center; gap:8px;">
                <input type="text" id="chat-search-input" placeholder="Search messages..." 
                    style="flex:1; padding:8px 12px; border-radius:20px; border:1px solid var(--border-color); outline:none;"
                    onkeyup="window.filterChatMessages(this.value)">
                <button class="icon-btn" onclick="window.toggleChatSearch()"><i class="fas fa-times"></i></button>
            </div>
            
            <div class="chat-actions" id="chat-actions-default">
                <button class="icon-btn" onclick="window.toggleChatSearch()" title="Search"><i class="fas fa-search"></i></button>
                <button class="icon-btn" onclick="window.startCall('audio')"><i class="fas fa-phone"></i></button>
                <button class="icon-btn" onclick="window.startCall()"><i class="fas fa-video"></i></button>
                ${activeChat.id !== 'general' ? `
                    <button class="icon-btn" style="color:#ef4444;" onclick="window.blockCurrentUser('${activeChat.id}')" title="Block User"><i class="fas fa-ban"></i></button>
                    <button class="icon-btn" style="color:#f59e0b;" onclick="window.reportCurrentUser('${activeChat.id}')" title="Report User"><i class="fas fa-flag"></i></button>
                ` : ''}
            </div>
        </div>
        
        <div id="messages-container" class="messages-container"></div>
        
        <emoji-picker class="hidden" id="emoji-picker"></emoji-picker>
        
        <!-- Attachment Menu -->
        <!-- Attachment Menu -->
        <div id="attachment-menu" class="hidden attachment-menu">
            <div onclick="document.getElementById('input-media').click()">
                <div class="menu-icon icon-photo"><i class="fas fa-image"></i></div>
                <span>Photo/Video</span>
            </div>
            <div onclick="document.getElementById('input-file').click()">
                 <div class="menu-icon icon-doc"><i class="fas fa-file-alt"></i></div>
                 <span>Document</span>
            </div>
        </div>

        <!-- Reply Preview Area -->
        <div id="reply-preview" class="reply-preview hidden">
            <div class="reply-content-box">
                 <div class="reply-line"></div>
                 <div class="reply-text-col">
                    <span id="reply-to-name" class="reply-sender">Sender Name</span>
                    <span id="reply-to-text" class="reply-text">Message Text</span>
                 </div>
            </div>
            <button onclick="window.cancelReply()" class="icon-btn close-reply"><i class="fas fa-times"></i></button>
        </div>

        <div class="input-area">
            <input type="file" id="input-media" style="display:none;" accept="image/*,video/*" onchange="window.handleMedia(this)">
            <input type="file" id="input-file" style="display:none;" accept="*" onchange="window.handleMedia(this)">
            
            <button class="icon-btn" onclick="window.toggleAttachmentMenu()"><i class="fas fa-paperclip"></i></button>
            <button class="icon-btn" onclick="window.toggleEmojiPicker()"><i class="far fa-smile"></i></button>
            <form id="msg-form">
                <input type="text" id="msg-input" placeholder="Message..." autocomplete="off">
                <button type="submit" class="icon-btn" style="color:var(--primary-color)"><i class="fas fa-paper-plane"></i></button>
            </form>
        </div>
    `;
}

let pollingInterval = null;
let lastTimestamp = 0;

async function setupChatLogic() {
    if (pollingInterval) clearInterval(pollingInterval);
    if (state.settingsView) return;

    // Start Polling ALWAYS (for Sidebar updates)
    const container = document.getElementById('messages-container');
    const form = document.getElementById('msg-form');

    pollingInterval = setInterval(() => pollMessages(container), 3000);

    if (!form || !container) return; // If no chat UI, stop here (don't bind form)

    // Render local messages if any (rare since we clear state.messages, but ok)
    state.messages.forEach(msg => appendMessage(msg, container));
    scrollToBottom(container);

    form.onsubmit = async (e) => {
        e.preventDefault();
        const input = document.getElementById('msg-input');
        const content = input.value.trim();
        if (!content) return;
        input.value = '';

        // Optimistic Render
        const tempId = 'temp-' + Date.now();
        const tempMsg = {
            id: tempId,
            senderId: state.user.user.id,
            senderName: state.user.user.name,
            content: content,
            type: 'text',
            timestamp: Date.now(),
            status: 'sending',
            replyTo: state.replyingTo ? {
                id: state.replyingTo.id,
                senderName: state.replyingTo.senderName,
                content: state.replyingTo.content,
                type: state.replyingTo.type
            } : null
        };
        appendMessage(tempMsg, container);
        scrollToBottom(container);

        // Disable button to prevent double-send
        const btn = form.querySelector('button[type="submit"]');
        if (btn) btn.disabled = true;

        try {
            const replyToId = state.replyingTo ? state.replyingTo.id : null;
            const realMsg = await api.sendMessage(content, 'text', state.activeChatId, replyToId);

            // Clear Reply State
            if (state.replyingTo) window.cancelReply();

            // FIX: Check if poller already added this message to prevent duplicates
            const alreadyInDom = document.getElementById(`msg-${realMsg.id}`);

            if (alreadyInDom) {
                // Poller beat us. Remove our temp bubble.
                const tempEl = document.getElementById(`msg-${tempId}`);
                if (tempEl) tempEl.remove();

                // Ensure state is consistent
                if (!state.messages.find(m => m.id === realMsg.id)) {
                    state.messages.push(realMsg);
                }
            } else {
                state.messages.push(realMsg);
                if (realMsg.timestamp > lastTimestamp) lastTimestamp = realMsg.timestamp;

                // Fix Duplication: Update the DOM element ID from Temp to Real
                const tempEl = document.getElementById(`msg-${tempId}`);
                if (tempEl) {
                    tempEl.id = `msg-${realMsg.id}`;
                    // Update tick to single tick immediately
                    const tick = tempEl.querySelector('.tick-icon');
                    if (tick) tick.innerHTML = '<i class="fas fa-check" style="color:rgba(255,255,255,0.5);"></i>';
                }
            }
        } catch (e) {
            console.error("Send failed", e);
            // Optionally remove temp message or show error
        } finally {
            if (btn) btn.disabled = false;
            // Re-focus input
            input.focus();
        }
    };

    lastTimestamp = 0;
    state.messages = [];
    container.innerHTML = '';

    // Initial Load: Specific Chat History
    try {
        const initialMessages = await api.getHistory(0, state.activeChatId);
        initialMessages.forEach(msg => {
            state.messages.push(msg);
            appendMessage(msg, container);
            if (msg.timestamp > lastTimestamp) lastTimestamp = msg.timestamp;
        });
        scrollToBottom(container);
    } catch (e) {
        console.error("Initial load failed", e);
    }

    // Start Polling for New Updates (Global)
    // Removed duplicate setInterval here, moved to top of function


    // Emoji Listener
    const picker = document.querySelector('emoji-picker');
    if (picker) {
        picker.addEventListener('emoji-click', event => {
            const input = document.getElementById('msg-input');
            input.value += event.detail.unicode;
            input.focus();
        });
    }

    // Typing Listener
    const msgInput = document.getElementById('msg-input');
    if (msgInput) {
        let typingTimeout;
        msgInput.addEventListener('input', () => {
            if (state.activeChatId) {
                // Clear previous timeout to debounce
                // Actually we want to throttle sending: Send "I am typing" every 2s

                // Simple approach: Just call it. Backend handles timestamp updates.

                if (!window.lastTypingEmit || Date.now() - window.lastTypingEmit > 2000) {
                    window.lastTypingEmit = Date.now();
                    api.sendTyping(state.activeChatId).catch(console.error);
                }
            }
        });

    }
}



async function pollMessages(container) {
    if (!state.user) return;
    try {
        // If we are in "list" view (mobile) or just want global updates, we should theoretically poll 'all'.
        // But to keep it simple and fix the user's issue: ALWAYS poll 'all' if we want sidebar updates.
        // However, fetching 'all' might return messages irrelevant to the CURRENT active chat.
        // So we filter inside the loop.

        // Use 'all' if the user is in list view? 
        // No, the user complains about "Recent Chats" not updating. 
        // Recent Chats update when we receive a message.
        // So we MUST poll 'all' to see messages from other users.

        // But `state.activeChatId` logic in `setupChatLogic` sets the context.
        // If I change this to 'all', `api.getHistory` will return everything.
        // I need to filter what I show in `container` (Active Chat) vs what I use to update Sidebar.

        // 1. DEDICATED ACTIVE CHAT POLL (Auto-Refresh)
        // This ensures the current view is always up to date relative to itself.
        if (state.activeChatId) {
            // BRUTE FORCE SYNC: Always fetch last 50 messages to ensure we don't miss anything due to timestamp skew
            // Deduplication below handles the rest.
            const since = 0;

            // Explicitly fetch ONLY generic or direct messages for this chat
            // Note: getHistory handles the 'general' vs ID logic
            const activeUpdates = await api.getHistory(since, state.activeChatId);

            if (activeUpdates && activeUpdates.length > 0) {
                const activeContainer = document.getElementById('messages-container');
                const searchInput = document.getElementById('chat-search-input');
                const searchQuery = searchInput && searchInput.value ? searchInput.value.toLowerCase() : null;

                let needsReadAck = false;

                activeUpdates.forEach(msg => {
                    // 1. Sync State
                    if (!state.messages.find(m => m.id == msg.id)) {
                        state.messages.push(msg);
                        // We do NOT ack delivery here. We wait to see if we should Ack Read.
                    }

                    // Check Filter match
                    const matchesFilter = !searchQuery || (msg.content && msg.content.toLowerCase().includes(searchQuery));

                    // 2. Sync View (Force Append if missing from DOM)
                    // ONLY append if it matches current filter (or no filter)
                    if (activeContainer && matchesFilter && !document.getElementById('msg-' + msg.id)) {
                        appendMessage(msg, activeContainer);
                    }

                    // 3. Read Acknowledgment Logic
                    if (msg.receiverId == state.user.user.id && msg.status !== 'seen') {
                        needsReadAck = true;
                    }
                });

                if (activeContainer) {
                    // Only scroll if we added new messages
                    // Wait... we iterate updates.
                    // Let's rely on logic: if `activeUpdates.length > 0` AND we filtered them.
                    // But we might have just updated status.
                    // Let's check if the container scroll height changed significantly or if we appended.
                    // Better: `appendMessage` appends.
                    // Let's pass a flag to appendMessage? No.

                    // Simple fix: If we are already near bottom, scroll to bottom. 
                    // OR check if we actually added a new DOM element.
                    const lastMsg = activeUpdates[activeUpdates.length - 1];
                    // If the last message in updates is NEW to our state, we essentially scroll.
                    // But `state.messages` was already updated in the loop.

                    // Let's just check if we are near bottom before update, and stay there?
                    // The user says "automatically scroll down" when they "scroll up".
                    // This implies unconditional scrollToBottom() is checking in.

                    // FIX: Only scroll if the last message is NOT visible or we are already at bottom?
                    // No, standard chat behavior:
                    // 1. If I am at the bottom, stay at bottom.
                    // 2. If I receive a NEW message, scroll to bottom.
                    // 3. If I am scrolled up viewing history, DO NOT scroll to bottom (unless I sent it).

                    const isAtBottom = activeContainer.scrollHeight - activeContainer.scrollTop <= activeContainer.clientHeight + 100;

                    if (isAtBottom) {
                        scrollToBottom(activeContainer);
                    }
                }

                if (needsReadAck) {
                    api.markAsRead(state.activeChatId).catch(console.error);
                }
            }
        }

        // 2. Global Poll (For Sidebar & Background Updates)
        const pollTarget = 'all';
        const newMessages = await api.getHistory(lastTimestamp, pollTarget);

        if (newMessages.length > 0) {
            let chatsUpdated = false;
            const toDeliverIds = [];

            newMessages.forEach(msg => {
                // Global Max Timestamp for next poll
                if (msg.timestamp > lastTimestamp) lastTimestamp = msg.timestamp;

                // Delivery Logic: If msg is for me and status is 'sent', I acknowledge it
                if (msg.receiverId == state.user.user.id && msg.status === 'sent') {
                    toDeliverIds.push(msg.id);
                }

                // 1. Logic for Active Chat Window
                // Show if it belongs to current active chat OR if it's general and we are in general
                const isForActiveChat =
                    (state.activeChatId === 'general' && msg.receiverId === 'general') ||
                    (state.activeChatId !== 'general' && (
                        (msg.senderId == state.activeChatId && msg.receiverId == state.user.user.id) ||
                        (msg.senderId == state.user.user.id && msg.receiverId == state.activeChatId)
                    ));

                if (isForActiveChat) {
                    if (!state.messages.find(m => m.id == msg.id)) {
                        state.messages.push(msg);
                        // Robustness: Get fresh container reference
                        const activeContainer = document.getElementById('messages-container');
                        if (activeContainer) appendMessage(msg, activeContainer);
                    }
                }

                // 2. Logic for Sidebar (Recent Chats)
                const isGroupMsg = msg.receiverId !== state.user.user.id && msg.receiverId !== 'general' && msg.senderId !== state.user.user.id;
                // If I am sender, partner is receiver. If I am receiver, partner is sender.
                let partnerId = (msg.senderId === state.user.user.id) ? msg.receiverId : msg.senderId;

                // If it's a group message (I received it but receiverId is not me), partner is the Group ID.
                if (isGroupMsg) partnerId = msg.receiverId;

                if (partnerId !== 'general') {
                    const chatIndex = state.chats.findIndex(c => c.id === partnerId);

                    if (chatIndex !== -1) {
                        // Existing Chat Update
                        let changed = false;

                        // Bug Fix: Only update avatar if the message is FROM the partner.
                        // If I sent the message, msg.avatar is MY avatar, so don't overwrite the chat avatar.
                        if (msg.senderId === partnerId && msg.avatar) {
                            if (state.chats[chatIndex].avatar !== msg.avatar) {
                                state.chats[chatIndex].avatar = msg.avatar;
                                changed = true;
                            }
                        }
                        // Update Last Msg
                        if (msg.timestamp > (state.chats[chatIndex].time || 0)) {
                            state.chats[chatIndex].time = msg.timestamp;
                            state.chats[chatIndex].lastMsg = (msg.type === 'text' || msg.type === 'call_log') ? msg.content : (msg.type === 'image' ? 'Image' : 'New Message');
                            changed = true;

                            // Increment Unread Count if not active
                            if (state.activeChatId !== partnerId) {
                                state.chats[chatIndex].unreadCount = (state.chats[chatIndex].unreadCount || 0) + 1;
                            }

                            // Play Sound for Incoming Message (if not mine and not active chat focused? Or just play it)
                            if (msg.senderId !== state.user.user.id) {
                                soundManager.play('message');
                            }
                        }
                        if (changed) chatsUpdated = true;
                    } else {
                        // NEW CHAT DISCOVERY
                        // Only for DMs (users) or if we want to support auto-group discovery (harder without group fetch)
                        // For now, support Users.

                        // Prevent multi-add if multiple messages arrive at once
                        if (!state.chats.find(c => c.id === partnerId)) {
                            // Add Placeholder
                            const newChat = {
                                id: partnerId,
                                name: msg.senderName || 'New Chat', // Temporary
                                avatar: msg.avatar || 'https://ui-avatars.com/api/?name=New',
                                lastMsg: (msg.type === 'text') ? msg.content : 'New Message',
                                time: msg.timestamp,
                                type: 'user', // Assume user for now, verify later
                                status: 'online'
                            };
                            state.chats.push(newChat);
                            chatsUpdated = true;

                            // Fetch Real Profile
                            api.batchGetUsers([partnerId]).then(users => {
                                if (users && users.length > 0) {
                                    const u = users[0];
                                    const idx = state.chats.findIndex(c => c.id === u.id);
                                    if (idx !== -1) {
                                        state.chats[idx].name = u.name;
                                        state.chats[idx].avatar = u.avatar;
                                        state.chats[idx].username = u.username;
                                        localStorage.setItem('oma_chats', JSON.stringify(state.chats));
                                        window.refreshSidebar();
                                    }
                                }
                            }).catch(e => {
                                // If batch fails (e.g. it's a group), maybe try getGroups?
                                // For now, silent fail, placeholder remains.
                            });
                        }
                    }
                }
            });

            if (chatsUpdated) {
                localStorage.setItem('oma_chats', JSON.stringify(state.chats));
                window.refreshSidebar();
            }
            const activeContainer = document.getElementById('messages-container');
            // FIX: Only scroll if we are already at the bottom
            if (activeContainer) {
                const isAtBottom = activeContainer.scrollHeight - activeContainer.scrollTop <= activeContainer.clientHeight + 100;
                if (isAtBottom) scrollToBottom(activeContainer);
            }

            // Send Delivery Acknowledgement
            if (toDeliverIds.length > 0) {
                api.markAsDelivered(toDeliverIds).catch(console.error);
            }
        }

        // 3. Status Sync for Active Chat (Blue Ticks)
        // We fetch the last 50 messages of the active chat to check for status updates (e.g. sent -> seen)
        if (state.activeChatId && state.activeChatId !== 'general') {
            const recentMsgs = await api.getHistory(0, state.activeChatId);

            recentMsgs.forEach(msg => {
                if (msg.senderId == state.user.user.id) {
                    const existingMsg = state.messages.find(m => m.id == msg.id);
                    // If status in UI differs from Backend
                    if (existingMsg && existingMsg.status !== msg.status) {
                        existingMsg.status = msg.status;

                        // Update DOM
                        const bubble = document.getElementById(`msg-${msg.id}`);
                        if (bubble) {
                            const tickSpan = bubble.querySelector('.tick-icon');
                            if (tickSpan) {
                                let newIcon = '';
                                if (msg.status === 'seen') newIcon = '<i class="fas fa-check-double" style="color:#67e8f9;"></i>'; // Cyan (Seen)
                                else if (msg.status === 'delivered') newIcon = '<i class="fas fa-check-double" style="color:rgba(255,255,255,0.9);"></i>'; // White Double
                                else newIcon = '<i class="fas fa-check" style="color:rgba(255,255,255,0.5);"></i>'; // White Single
                                tickSpan.innerHTML = newIcon;
                            }
                        }
                    }
                }
            });
        }

        // 4. Typing Status Poll
        if (state.activeChatId && state.activeChatId !== 'general') {
            try {
                // Poll for *my* ID to see who is typing to *me*
                const res = await api.getTypingStatus(state.user.user.id);
                const typingUsers = res.typingUsers || [];
                const statusEl = document.getElementById('header-status');

                if (statusEl) {
                    // Check if current partner is typing
                    if (typingUsers.includes(state.activeChatId)) {
                        statusEl.textContent = "typing...";
                        statusEl.style.color = "var(--primary-color)";
                        statusEl.style.fontWeight = "bold";
                        statusEl.classList.add('animate__animated', 'animate__pulse', 'animate__infinite');
                    } else {
                        // Revert
                        const chat = state.chats.find(c => c.id === state.activeChatId) || { id: state.activeChatId };
                        statusEl.style.color = "var(--text-secondary)";
                        statusEl.style.fontWeight = "normal";
                        statusEl.classList.remove('animate__animated', 'animate__pulse', 'animate__infinite');
                        statusEl.innerHTML = getHeaderStatusText(chat);
                    }
                }
            } catch (e) { }
        }
    } catch (e) {
        if (e.message === 'Unauthorized' || e.message === 'Invalid Token') window.logout();
    }
}

window.refreshSidebar = () => {
    const listContainer = document.getElementById('chat-list');
    if (!listContainer) return;

    // We need the same logic as renderSidebarMain but just the list part
    // Actually, renderSidebarMain returns the whole HTML string including header.
    // Let's just manually rebuild the list HTML here to avoid complex refactors.

    // Wait, reusing renderSidebarMain's logic is safer. 
    // But renderSidebarMain returns a string.
    // Let's just extract the list generation logic.

    // Easier: Just look at renderSidebarMain in Step 405.
    // It maps `chatList`.

    let chatList = [];
    if (state.isSearching) {
        chatList = state.searchResults;
    } else {
        const general = { id: 'general', name: 'General Group', lastMsg: 'Tap to chat', avatar: 'https://ui-avatars.com/api/?name=General+Group&background=random', time: '' };
        // Determine "General" presence based on preference, but here we just follow renderSidebarMain
        chatList = [general, ...state.chats];
    }

    const html = `
        <div class="pull-indicator" id="pull-indicator"><i class="fas fa-spinner"></i></div>
        ${chatList.map(chat => {
        // Calculate Unread (Basic: if bold logic needed, we need 'unread' property.
        // For now, let's assume 'lastMsg' being BOLD implies unread if we successfully tracked it.
        // Or we just style it. User asked for "like Instagram".
        // Bold text + Dot.
        // We don't have 'unreadCount' in state.chats yet. We need to key it off something.
        // For now, let's just make the Time/LastMsg bold if it was updated recently?
        // No, true read state requires backend support.
        // Let's apply a visual style if it looks "new" (e.g. bold always for now as requested? No that's bad).
        // Let's checking if we have 'unread' flag. If not, we'll just implement the style CLASS and toggle it later.

        // Actually, Phase 1 just asked for "Bold texts".
        const isUnread = chat.unreadCount > 0; // We need to populate this

        return `
            <div class="chat-item ${chat.id === state.activeChatId ? 'active' : ''}" onclick="window.openChat('${chat.id}')">
                <img src="${chat.avatar || 'https://ui-avatars.com/api/?name=' + chat.username}">
                <div class="chat-info">
                    <div style="display:flex;justify-content:space-between;align-items:center;">
                        <h4 style="${isUnread ? 'font-weight: 800; color: var(--text-primary);' : ''}">${chat.name || chat.username}</h4>
                        <span style="font-size:0.75rem; color: ${isUnread ? 'var(--primary-color)' : 'var(--text-secondary)'};">
                           ${chat.time ? timeAgo(chat.time) : ''}
                        </span>
                    </div>
                    <div style="display:flex;justify-content:space-between;align-items:center;">
                        <p style="${isUnread ? 'font-weight: 700; color: var(--text-primary);' : 'color: var(--text-secondary);'}">
                            ${chat.lastMsg || (chat.username ? '@' + chat.username : '')}
                        </p>
                        ${isUnread ? `<div style="background:var(--primary-color);color:white;border-radius:50%;padding:2px 6px;font-size:0.7rem;">${chat.unreadCount}</div>` : ''}
                    </div>
                </div>
            </div>
        `}).join('')}
        ${state.isSearching && chatList.length === 0 ? '<div style="padding:20px;text-align:center;color:grey;">No users found</div>' : ''}
    `;

    listContainer.innerHTML = html;

    // Re-attach pull listeners
    setupPullToRefresh();
};

// Helper for consistent name colors
function getColorForName(name) {
    if (!name) return 'var(--primary-color)';
    const colors = [
        '#ef4444', '#f97316', '#f59e0b', '#84cc16', '#10b981', '#06b6d4',
        '#3b82f6', '#6366f1', '#8b5cf6', '#d946ef', '#f43f5e'
    ];
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash) % colors.length];
}

function appendMessage(msg, container) {
    if (!container) return;
    const isMe = msg.senderId === state.user.user.id;
    const div = document.createElement('div');
    div.id = `msg-${msg.id}`;
    div.className = `message-bubble ${isMe ? 'message-sent' : 'message-received'} animate__animated animate__fadeInUp`;

    // Use LIVE avatar for self, otherwise use message avatar
    const avatarToUse = isMe ? state.user.user.avatar : msg.avatar;

    let contentHtml = `<div class="msg-content">${msg.content}</div>`;
    if (msg.type === 'image') {
        contentHtml = `<img src="${msg.content}" class="msg-image">`;
    } else if (msg.type === 'video') {
        contentHtml = `<video src="${msg.content}" controls class="msg-video"></video>`;
    } else if (msg.type === 'file') {
        let fileData = {};
        try {
            fileData = JSON.parse(msg.content);
        } catch (e) {
            fileData = { name: 'Unknown File', data: '#' };
        }
        contentHtml = `
            <div class="msg-file">
                <a href="${fileData.data}" download="${fileData.name}" class="file-link">
                    <div class="file-icon"><i class="fas fa-file-alt"></i></div>
                    <div class="file-info">
                        <span class="file-name">${fileData.name}</span>
                        <span class="file-size">Click to Download</span>
                    </div>
                    <i class="fas fa-download download-icon"></i>
                </a>
            </div>
        `;
    } else if (msg.type === 'system') {
        // System Message Style
        div.className = 'message-bubble system-message animate__animated animate__fadeIn';
        div.style.alignSelf = 'center';
        div.style.background = 'rgba(0,0,0,0.2)';
        div.style.color = 'var(--text-secondary)';
        div.style.fontSize = '0.8rem';
        div.innerHTML = msg.content;
        container.appendChild(div);
        return;
    }

    const date = new Date(msg.timestamp);
    const time = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    // Tick Status
    let tickHtml = '';
    if (isMe) {
        if (msg.status === 'sending') tickHtml = '<i class="fas fa-clock" style="color:rgba(255,255,255,0.5); font-size: 0.8em;"></i>';
        else if (msg.status === 'seen') tickHtml = '<i class="fas fa-check-double" style="color:#67e8f9;"></i>'; // Cyan
        else if (msg.status === 'delivered') tickHtml = '<i class="fas fa-check-double" style="color:rgba(255,255,255,0.9);"></i>'; // White Double
        else tickHtml = '<i class="fas fa-check" style="color:rgba(255,255,255,0.5);"></i>'; // White Single
    }

    // Determine if we should show sender name
    // Show if: NOT me AND (Unique Chat is 'general' OR Active Chat Type is 'group')
    let isGroupContext = state.activeChatId === 'general';
    if (!isGroupContext) {
        const currentChat = state.chats.find(c => c.id === state.activeChatId);
        if (currentChat && currentChat.type === 'group') {
            isGroupContext = true;
        }
    }
    const showSenderName = !isMe && isGroupContext;

    div.innerHTML = `
            <div style="width:100%;${isMe ? 'margin-left:auto;' : ''}">
               ${showSenderName ? `<div class="sender-name" style="font-size:0.75rem;color:${getColorForName(msg.senderName)};margin-bottom:2px;font-weight:700;">${msg.senderName}</div>` : ''}
               
               <!-- Reply Context -->
               ${msg.replyTo ? `
                   <div class="reply-context" onclick="window.scrollToMessage('${msg.replyTo.id}')">
                       <div class="reply-bar"></div>
                       <div class="reply-info">
                           <span class="reply-sender">${msg.replyTo.senderName}</span>
                           <span class="reply-content">${(msg.replyTo.type === 'image') ? '📷 Photo' : (msg.replyTo.type === 'video') ? '🎥 Video' : msg.replyTo.content}</span>
                       </div>
                   </div>
               ` : ''}

               ${contentHtml}
               
               <span class="msg-time" style="display:flex;align-items:center;justify-content:flex-end;gap:4px;">
                    ${msg.isStarred ? '<i class="fas fa-star" style="font-size:0.7rem;color:#f59e0b;"></i>' : ''}
                    ${msg.isPinned ? '<i class="fas fa-thumbtack" style="font-size:0.7rem;color:#f59e0b;"></i>' : ''}
                    ${time}
                    ${msg.isEdited ? '<span class="edited-tag" style="font-size:0.7em;opacity:0.6;">(edited)</span>' : ''}
                    <span class="tick-icon" style="font-size:0.75rem; min-width:14px; text-align:right;">${tickHtml}</span>
               </span>
           </div>
    `;

    // Long Press / Context Menu Logic
    // Swipe to Reply Logic
    let touchStartX = 0;
    let touchMoveX = 0;
    let isSwiping = false;
    let pressTimer;

    div.addEventListener('touchstart', (e) => {
        touchStartX = e.touches[0].clientX;
        // Long Press Logic
        pressTimer = setTimeout(() => {
            if (!isSwiping) window.handleMessageLongPress(msg.id);
        }, 600);
    }, { passive: true });

    div.addEventListener('touchmove', (e) => {
        touchMoveX = e.touches[0].clientX;
        const diff = touchMoveX - touchStartX;

        if (!isSwiping && diff > 15) {
            isSwiping = true;
            clearTimeout(pressTimer);
            div.style.transition = 'none';
        }

        if (isSwiping && diff > 0) {
            if (e.cancelable) e.preventDefault();
            const drag = Math.min(diff, 100);
            div.style.transform = `translateX(${drag}px)`;
        }
    }, { passive: false });

    div.addEventListener('touchend', (e) => {
        clearTimeout(pressTimer);
        const diff = touchMoveX - touchStartX;

        div.style.transition = 'transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275)';
        div.style.transform = 'translateX(0)';

        if (isSwiping && diff > 60) {
            if (navigator.vibrate) navigator.vibrate(50);
            window.replyToMessage(msg.id);
        }

        setTimeout(() => {
            isSwiping = false;
        }, 300);
        touchStartX = 0;
        touchMoveX = 0;
    });

    // Desktop Right Click
    div.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        window.handleMessageLongPress(msg.id);
    });

    container.appendChild(div);
}

function scrollToBottom(container) {
    if (container) container.scrollTop = container.scrollHeight;
}

// --- Message Options Modal Logic ---
window.selectedMessageId = null;

window.handleMessageLongPress = (msgId) => {
    // Vibrate
    if (navigator.vibrate) navigator.vibrate(50);

    window.selectedMessageId = msgId;
    const modal = document.getElementById('message-options-modal');
    const msg = state.messages.find(m => m.id == msgId);
    const deleteBtn = document.getElementById('btn-delete-msg');

    // Show Delete/Edit only if I am the sender
    if (msg && msg.senderId === state.user.user.id) {
        deleteBtn.style.display = 'flex';
        const editBtn = document.getElementById('btn-edit-msg');
        if (editBtn) editBtn.style.display = 'flex';
    } else {
        deleteBtn.style.display = 'none';
        const editBtn = document.getElementById('btn-edit-msg');
        if (editBtn) editBtn.style.display = 'none';
    }

    // Toggle Pin/Star Text
    const starBtn = document.getElementById('btn-star-msg');
    const pinBtn = document.getElementById('btn-pin-msg');
    if (starBtn) {
        starBtn.innerHTML = msg.isStarred ? '<i class="fas fa-star"></i> Unstar Message' : '<i class="fas fa-star"></i> Star Message';
    }
    if (pinBtn) {
        pinBtn.innerHTML = msg.isPinned ? '<i class="fas fa-thumbtack"></i> Unpin Message' : '<i class="fas fa-thumbtack"></i> Pin Message';
    }

    if (modal) {
        modal.style.display = 'flex';
        // Small delay to allow display:flex to apply before opacity transition
        requestAnimationFrame(() => {
            modal.classList.remove('hidden');
        });
    }
};

window.closeMessageOptions = () => {
    window.selectedMessageId = null;
    const modal = document.getElementById('message-options-modal');
    if (modal) {
        modal.classList.add('hidden');
        setTimeout(() => {
            modal.style.display = 'none';
        }, 200);
    }
};

window.copySelectedMessage = () => {
    if (!window.selectedMessageId) return;
    const msg = state.messages.find(m => m.id == window.selectedMessageId);
    if (msg && msg.content) {
        navigator.clipboard.writeText(msg.content).then(() => {
            alert('Copied to clipboard');
        }).catch(err => {
            console.error('Failed to copy: ', err);
        });
    }
    window.closeMessageOptions();
};

window.deleteSelectedMessage = async () => {
    if (!window.selectedMessageId) return;
    if (!confirm('Delete this message?')) return;

    try {
        // Optimistic UI Removal
        const msgId = window.selectedMessageId;
        const msgEl = document.getElementById(`msg-${msgId}`);
        if (msgEl) {
            msgEl.classList.remove('animate__fadeInUp');
            msgEl.classList.add('animate__fadeOut');
            setTimeout(() => msgEl.remove(), 300);
        }

        // Remove from state
        state.messages = state.messages.filter(m => m.id != msgId);

        // Call API
        await api.deleteMessage(msgId, 'everyone');

    } catch (e) {
        alert('Failed to delete');
        // Reload messages on failure?
    }
    window.closeMessageOptions();
};

// --- New Action Handlers ---

window.scrollToMessage = (msgId) => {
    const el = document.getElementById(`msg-${msgId}`);
    if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        el.classList.add('highlight-message');
        setTimeout(() => el.classList.remove('highlight-message'), 2000);
    } else {
        // Message might not be loaded if we are using pagination (future proofing)
        alert("messsage not loaded");
    }
};

window.replyToMessage = (msgId) => {
    // If msgId not passed (from menu), use selected
    const id = msgId || window.selectedMessageId;
    if (!id) return;

    window.closeMessageOptions();

    const msg = state.messages.find(m => m.id == id);
    if (!msg) return;

    state.replyingTo = msg; // Store in state

    // Show Preview
    const preview = document.getElementById('reply-preview');
    const nameEl = document.getElementById('reply-to-name');
    const textEl = document.getElementById('reply-to-text');
    const input = document.getElementById('msg-input');

    if (preview && nameEl && textEl) {
        nameEl.innerText = msg.senderName || 'User';
        nameEl.style.color = getColorForName(msg.senderName);

        textEl.innerText = (msg.type === 'image') ? '📷 Photo' :
            (msg.type === 'video') ? '🎥 Video' :
                (msg.type === 'file') ? '📄 Document' : msg.content;

        preview.classList.remove('hidden');
        preview.classList.add('animate__animated', 'animate__fadeInUp');
        input.focus();
    }
};

window.cancelReply = () => {
    state.replyingTo = null;
    const preview = document.getElementById('reply-preview');
    if (preview) {
        preview.classList.add('hidden');
        preview.classList.remove('animate__animated', 'animate__fadeInUp');
    }
};

window.editSelectedMessage = async () => {
    if (!window.selectedMessageId) return;
    const msg = state.messages.find(m => m.id == window.selectedMessageId);
    window.closeMessageOptions();

    const newContent = prompt("Edit message:", msg.content);
    if (newContent && newContent !== msg.content) {
        try {
            await api.editMessage(msg.id, newContent);
            // Optimistic Update
            msg.content = newContent;
            msg.isEdited = true; // Add flag
            // Re-render item?
            // Ideally re-poll or just update DOM text
            const msgEl = document.getElementById(`msg-${msg.id}`);
            if (msgEl) {
                const contentEl = msgEl.querySelector('.msg-content');
                if (contentEl) contentEl.innerText = newContent;
                // Add (edited) tag if not present
                const timeEl = msgEl.querySelector('.msg-time');
                if (timeEl && !msgEl.querySelector('.edited-tag')) {
                    const editTag = document.createElement('span');
                    editTag.className = 'edited-tag';
                    editTag.innerText = ' (edited)';
                    editTag.style.fontSize = '0.7em';
                    editTag.style.opacity = '0.6';
                    timeEl.prepend(editTag);
                }
            }
        } catch (e) { alert("Failed to edit"); }
    }
};

window.pinSelectedMessage = async () => {
    if (!window.selectedMessageId) return;
    const msgId = window.selectedMessageId;
    const msg = state.messages.find(m => m.id === msgId);
    window.closeMessageOptions();
    try {
        await api.pinMessage(msgId);
        if (msg) msg.isPinned = !msg.isPinned;
        const msgEl = document.getElementById(`msg-${msgId}`);
        if (msgEl) {
            const timeEl = msgEl.querySelector('.msg-time');
            if (timeEl) {
                const existing = timeEl.querySelector('.fa-thumbtack');
                if (msg.isPinned && !existing) {
                    const pinIcon = document.createElement('i');
                    pinIcon.className = 'fas fa-thumbtack';
                    pinIcon.style.cssText = 'font-size:0.7rem; color:#f59e0b; margin-right:4px;';
                    timeEl.prepend(pinIcon);
                } else if (!msg.isPinned && existing) {
                    existing.remove();
                }
            }
        }
    } catch (e) { alert("Failed to pin"); }
};

window.starSelectedMessage = async () => {
    if (!window.selectedMessageId) return;
    const msgId = window.selectedMessageId;
    const msg = state.messages.find(m => m.id === msgId);
    window.closeMessageOptions();
    try {
        await api.starMessage(msgId);
        if (msg) msg.isStarred = !msg.isStarred;
        const msgEl = document.getElementById(`msg-${msgId}`);
        if (msgEl) {
            const timeEl = msgEl.querySelector('.msg-time');
            if (timeEl) {
                const existing = timeEl.querySelector('.fa-star');
                if (msg.isStarred && !existing) {
                    const starIcon = document.createElement('i');
                    starIcon.className = 'fas fa-star';
                    starIcon.style.cssText = 'font-size:0.7rem; color:#f59e0b; margin-right:4px;';
                    timeEl.prepend(starIcon);
                } else if (!msg.isStarred && existing) {
                    existing.remove();
                }
            }
        }
    } catch (e) { alert("Failed to star"); }
};


window.handleSearch = async (query) => {
    state.isSearching = query.length >= 2;
    const listContainer = document.querySelector('.chat-list');

    if (!state.isSearching) {
        state.searchResults = [];
        if (listContainer) {
            const defaultChat = { id: 'general', name: 'General Group', lastMsg: 'Tap to chat', avatar: 'https://ui-avatars.com/api/?name=General+Group&background=random' };
            listContainer.innerHTML = `
                <div class="chat-item ${state.activeChatId === 'general' ? 'active' : ''}" onclick="window.openChat('general')">
                    <img src="${defaultChat.avatar}">
                    <div class="chat-info">
                        <h4>${defaultChat.name}</h4>
                        <p>${defaultChat.lastMsg}</p>
                    </div>
                </div>
            `;
        }
        return;
    }

    try {
        const results = await api.searchUsers(query);
        state.searchResults = results;
        if (listContainer) {
            if (results.length === 0) {
                listContainer.innerHTML = '<div style="padding:20px;text-align:center;color:grey;">No users found</div>';
            } else {
                listContainer.innerHTML = `
                <div class="user-profile-summary" onclick="switchTab('profile')">
                    <img src="${state.user.user.avatar || 'https://ui-avatars.com/api/?name=' + state.user.user.name}" alt="Profile">
                    <div class="user-details">
                        <span class="username">${state.user.user.name}</span>
                        <span class="status-text">Online</span>
                    </div>
                </div>
                <!-- DEBUG BUTTON -->
                <button onclick="window.registerPush()" style="margin: 10px; background: red; color: white; padding: 5px;">DEBUG PUSH</button>
                ${results.map(chat => `
                    <div class="chat-item ${chat.id === state.activeChatId ? 'active' : ''}" onclick="window.openChat('${chat.id}')">
                        <img src="${chat.avatar || 'https://ui-avatars.com/api/?name=' + chat.username}">
                        <div class="chat-info">
                            <h4>${chat.name || chat.username}</h4>
                            <p>${chat.username ? '@' + chat.username : ''}</p>
                        </div>
                    </div>
                `).join('')}`;
            }
        }
    } catch (e) { console.error(e); }
};

window.openSettings = (view = 'main') => {
    state.settingsView = view;
    render();
};

window.closeSettings = () => {
    state.settingsView = null;
    render();
};

window.toggleDarkMode = (input) => {
    if (input.checked) {
        document.body.classList.add('dark-mode');
        localStorage.setItem('oma_dark', 'true');
    } else {
        document.body.classList.remove('dark-mode');
        localStorage.setItem('oma_dark', 'false');
    }
};

window.saveProfile = async () => {
    const name = document.getElementById('settings-name').value;
    const bio = document.getElementById('settings-bio').value;
    try {
        const updatedUser = await api.updateProfile({ name, bio });
        state.user.user = { ...state.user.user, ...updatedUser };
        localStorage.setItem('oma_user', JSON.stringify(state.user));
        alert('Profile Updated!');
    } catch (e) {
        alert('Update failed');
    }
};

window.savePrivacy = async () => {
    const lastSeen = document.getElementById('privacy-lastseen').value;
    const readReceipts = document.getElementById('privacy-readreceipts').checked;

    const settings = {
        lastSeenPrivacy: lastSeen,
        readReceipts: readReceipts
    };

    try {
        // Optimistic UI Update
        state.user.user.settings = { ...state.user.user.settings, ...settings };

        await api.updateProfile({ settings });
    } catch (e) {
        console.error("Privacy update failed", e);
        // Revert? For now just log
    }
};

window.openBlockedSettings = async () => {
    state.settingsView = 'blocked';
    render(); // Show loading or empty first

    const blockedIds = state.user?.user?.blockedUsers || [];
    if (blockedIds.length > 0) {
        try {
            const users = await api.batchGetUsers(blockedIds);
            state.blockedUsersDetails = users;
            render(); // Re-render with data
        } catch (e) {
            console.error("Failed to load blocked users", e);
        }
    } else {
        state.blockedUsersDetails = [];
        render();
    }
};

window.unblockUser = async (userId) => {
    if (!confirm('Unblock this user?')) return;
    try {
        await api.blockUser(userId, 'unblock');

        // Update Local State
        state.user.user.blockedUsers = state.user.user.blockedUsers.filter(id => id !== userId);
        state.blockedUsersDetails = state.blockedUsersDetails.filter(u => u.id !== userId);

        localStorage.setItem('oma_user', JSON.stringify(state.user));
        render(); // Update UI
    } catch (e) {
        alert("Failed to unblock");
    }
};

window.blockCurrentUser = async (userId) => {
    if (!confirm('Are you sure you want to BLOCK this user? You will not receive messages from them.')) return;
    try {
        await api.blockUser(userId, 'block');

        // Update Local State
        if (!state.user.user.blockedUsers) state.user.user.blockedUsers = [];
        state.user.user.blockedUsers.push(userId);

        localStorage.setItem('oma_user', JSON.stringify(state.user));
        alert('User blocked');
        // Optionally close chat or show blocked state
        window.closeChat();
    } catch (e) {
        alert("Failed to block user");
    }
};

window.reportCurrentUser = async (userId) => {
    const reason = prompt("Why are you reporting this user? (Spam, Harassment, etc.)");
    if (!reason) return;

    try {
        await api.reportUser(userId, reason);
        alert('Report submitted. Thank you.');
    } catch (e) {
        alert("Failed to submit report");
    }
};

function renderSettingsAccount() {
    const userPhone = state.user?.user?.phone;
    const isLinked = !!userPhone;

    return `
        <div class="sidebar-header">
             <button class="icon-btn" onclick="window.openSettings('main')"><i class="fas fa-arrow-left"></i></button>
             <h3>Account Security</h3>
        </div>
        <div class="settings-content settings-slide-in">
             <div class="settings-inner-content">
                
                <h4 style="margin-top:0; margin-bottom: 20px;">Phone Number</h4>
                <div id="phone-link-container">
                    ${isLinked ? `
                        <div class="settings-item" style="padding:0; border:none; margin-bottom:20px;">
                            <div class="settings-text">
                                <p style="margin:0; color:var(--text-secondary);">Linked Number</p>
                                <h4 style="margin:0; font-size:1.1rem;">${userPhone}</h4>
                            </div>
                            <i class="fas fa-check-circle" style="color:#10b981;"></i>
                        </div>
                    ` : `
                        <p style="font-size:0.9rem; color:var(--text-secondary); margin-bottom:15px;">Link your phone number to secure your account and allow login via SMS.</p>
                        <div class="input-group" id="link-phone-input-group">
                            <input type="tel" id="linkPhoneNumber" placeholder="+1234567890" style="padding:12px; border-radius:8px;">
                            <button class="primary" style="width:100%; margin-top:10px;" onclick="window.handleSendLinkOTP()" id="btn-send-link-otp">Link Phone</button>
                        </div>
                        <div id="link-otp-group" style="display:none; margin-top:15px;">
                            <input type="text" id="linkOtpCode" placeholder="Enter 6-digit OTP" style="padding:12px; border-radius:8px;">
                            <button class="primary" style="width:100%; margin-top:10px;" onclick="window.handleVerifyLinkOTP()">Verify & Link</button>
                        </div>
                        <div id="link-error" class="error-msg" style="margin-top:10px;"></div>
                    `}
                </div>

                <hr style="border:0; border-top:1px solid var(--border-color); margin:30px 0;">

                <h4 style="margin-bottom: 20px;">Change Password</h4>
                
                <div class="input-group">
                    <label>Current Password</label>
                    <input type="password" id="old-pass" placeholder="••••••••" style="padding: 12px; border-radius: 8px;">
                </div>
                
                <div class="input-group">
                    <label>New Password</label>
                    <input type="password" id="new-pass" placeholder="••••••••" style="padding: 12px; border-radius: 8px;">
                </div>

                <button class="primary" style="width:100%; margin-top:20px; padding: 12px; font-weight: 600;" onclick="window.changePassword()">Update Password</button>

             </div>
        </div>
    `;
}

window.handleSendLinkOTP = async () => {
    const phone = document.getElementById('linkPhoneNumber').value;
    const errorMsg = document.getElementById('link-error');
    const btn = document.getElementById('btn-send-link-otp');

    if (!phone) return alert("Please enter a phone number");

    btn.disabled = true;
    btn.innerText = 'Sending...';

    try {
        await initFirebaseClient();
        if (!window.recaptchaVerifier) {
            window.recaptchaVerifier = new firebase.auth.RecaptchaVerifier('recaptcha-container', {
                'size': 'invisible'
            });
        }

        window.linkConfirmationResult = await firebase.auth().signInWithPhoneNumber(phone, window.recaptchaVerifier);

        document.getElementById('link-phone-input-group').style.display = 'none';
        document.getElementById('link-otp-group').style.display = 'block';
        errorMsg.innerText = '';
    } catch (error) {
        console.error("Link SMS Send Error:", error);
        errorMsg.innerText = error.message;
        btn.disabled = false;
        btn.innerText = 'Link Phone';
    }
};

window.handleVerifyLinkOTP = async () => {
    const code = document.getElementById('linkOtpCode').value;
    const errorMsg = document.getElementById('link-error');

    try {
        const result = await window.linkConfirmationResult.confirm(code);
        const idToken = await result.user.getIdToken();

        const res = await api.linkPhone(idToken);

        // Update local state
        state.user.user.phone = res.phoneNumber;
        state.user.user.settings.phoneLinked = true;
        localStorage.setItem('oma_user', JSON.stringify(state.user));

        alert("Phone number linked successfully!");
        render();
    } catch (error) {
        console.error("Link OTP Verification Error:", error);
        errorMsg.innerText = error.message || 'Verification failed';
    }
};

window.changePassword = async () => {
    const oldPass = document.getElementById('old-pass').value;
    const newPass = document.getElementById('new-pass').value;

    if (!oldPass || !newPass) return alert("Please fill in all fields");

    try {
        await api.changePassword(oldPass, newPass);
        alert("Password updated successfully");
        document.getElementById('old-pass').value = '';
        document.getElementById('new-pass').value = '';
    } catch (e) {
        alert(e.error || "Failed to update password");
    }
};

window.deleteAccount = async () => {
    if (!confirm("Are you ABSOLUTELY sure? This cannot be undone.")) return;

    const password = prompt("Please enter your password to confirm deletion:");
    if (!password) return;

    try {
        await api.deleteAccount(password);
        alert("Account deleted. Goodbye.");
        window.logout();
    } catch (e) {
        alert(e.error || "Failed to delete account");
    }
};

window.toggleAttachmentMenu = () => {
    const menu = document.getElementById('attachment-menu');
    if (menu) menu.classList.toggle('hidden');
};

window.toggleEmojiPicker = () => {
    const picker = document.getElementById('emoji-picker');
    if (picker) picker.classList.toggle('hidden');
};

// Handle Emoji Selection
document.querySelector('emoji-picker')?.addEventListener('emoji-click', event => {
    const input = document.getElementById('msg-input');
    if (input) {
        input.value += event.detail.unicode;
        input.focus();
    }
});

// Close menu/picker when clicking outside
document.addEventListener('click', (e) => {
    const menu = document.getElementById('attachment-menu');
    const picker = document.getElementById('emoji-picker');

    // Check Attachment Menu
    const menuBtn = document.querySelector('.icon-btn .fa-paperclip')?.closest('button');
    if (menu && !menu.classList.contains('hidden')) {
        if (menu.contains(e.target)) return;
        if (menuBtn && (e.target === menuBtn || menuBtn.contains(e.target))) return;
        menu.classList.add('hidden');
    }

    // Check Emoji Picker
    const pickerBtn = document.querySelector('.icon-btn .fa-smile')?.closest('button');
    if (picker && !picker.classList.contains('hidden')) {
        if (picker.contains(e.target)) return;
        if (pickerBtn && (e.target === pickerBtn || pickerBtn.contains(e.target))) return;
        picker.classList.add('hidden');
    }
});

window.toggleChatSearch = () => {
    const userHeader = document.querySelector('.chat-header-user');
    const actions = document.getElementById('chat-actions-default');
    const searchBar = document.getElementById('chat-search-bar');
    const searchInput = document.getElementById('chat-search-input');

    if (searchBar.style.display === 'none') {
        userHeader.style.display = 'none';
        actions.style.display = 'none';
        searchBar.style.display = 'flex';
        searchInput.focus();
    } else {
        searchBar.style.display = 'none';
        userHeader.style.display = 'flex';
        actions.style.display = 'flex';
        searchInput.value = '';
        window.filterChatMessages(''); // Reset filter
    }
};

window.filterChatMessages = (query) => {
    const container = document.getElementById('messages-container');
    if (!container) return;

    // Simple DOM filtering or Re-render? 
    // Re-rendering from state is safer to maintain order and structure.

    container.innerHTML = '';
    const filtered = state.messages.filter(msg => {
        if (!query) return true;
        return msg.content.toLowerCase().includes(query.toLowerCase());
    });

    filtered.forEach(msg => appendMessage(msg, container));

    // Verify Ticks Logic for re-rendered messages (optional, pure visual)
};

// Existing Function
window.toggleEmojiPicker = () => {
    const picker = document.getElementById('emoji-picker');
    if (picker) picker.classList.toggle('hidden');
};

window.addEventListener('hashchange', render);
const resizeImage = (base64Str, maxWidth = 800) => {
    return new Promise((resolve) => {
        const img = new Image();
        img.src = base64Str;
        img.onload = () => {
            const canvas = document.createElement('canvas');
            let width = img.width;
            let height = img.height;
            if (width > maxWidth) {
                height *= maxWidth / width;
                width = maxWidth;
            }
            canvas.width = width;
            canvas.height = height;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0, width, height);
            resolve(canvas.toDataURL('image/jpeg', 0.8));
        };
    });
};

window.updateAvatar = async (input) => {
    const file = input.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (e) => {
        const base64 = e.target.result;
        try {
            const resized = await resizeImage(base64, 300);
            const updatedUser = await api.updateProfile({ avatar: resized });
            state.user.user = { ...state.user.user, ...updatedUser };
            localStorage.setItem('oma_user', JSON.stringify(state.user));

            // Sync with Recent Chats (if self is present)
            const selfChatIndex = state.chats.findIndex(c => c.id === state.user.user.id);
            if (selfChatIndex !== -1) {
                state.chats[selfChatIndex].avatar = state.user.user.avatar;
                state.chats[selfChatIndex].name = state.user.user.name; // Sync name too just in case
                localStorage.setItem('oma_chats', JSON.stringify(state.chats));
            }

            render();
        } catch (err) {
            console.error(err);
            alert("Avatar update failed.");
        }
    };
    reader.readAsDataURL(file);
};

window.handleMedia = async (input) => {
    const file = input.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (e) => {
        let base64 = e.target.result;
        let type = 'file';

        if (file.type.startsWith('image')) type = 'image';
        else if (file.type.startsWith('video')) type = 'video';

        // For generic files, we append the filename to the content or handle it in structure
        // Current backend expects 'content' string. For files, we can store "filename|base64" or just base64.
        // Let's store a JSON object in content? Or just Base64 and rely on client?
        // Simple hack: Prefix filename to base64 with a delimiter if it's a file?
        // Or better: Send JSON string as content for files: JSON.stringify({ name: file.name, data: base64 })

        try {
            if (type === 'image') {
                base64 = await resizeImage(base64, 800);
            }

            let content = base64;
            if (type === 'file') {
                content = JSON.stringify({ name: file.name, size: file.size, data: base64 });
            }

            await api.sendMessage(content, type, state.activeChatId);
            const container = document.getElementById('messages-container');
            pollMessages(container);
        } catch (err) {
            console.error("Upload failed", err);
            alert("Upload failed.");
        }
    };
    reader.readAsDataURL(file);
};

window.openChat = async (chatId) => {
    // Just update hash, let render() handle state
    window.location.hash = '#chat/' + chatId;

    // Mark as Read in State
    const chatIndex = state.chats.findIndex(c => c.id === chatId);
    if (chatIndex !== -1) {
        state.chats[chatIndex].unreadCount = 0;
        localStorage.setItem('oma_chats', JSON.stringify(state.chats));
    }

    // Mark as Read in Backend
    if (chatId !== 'general') {
        try {
            await api.markAsRead(chatId);
        } catch (e) {
            console.error("Failed to mark read", e);
        }
    }

    // Add to Recent Chats
    const searchedUser = state.searchResults.find(u => u.id === chatId);
    if (searchedUser) {
        const exists = state.chats.find(c => c.id === chatId);
        if (!exists) {
            state.chats.push(searchedUser);
            localStorage.setItem('oma_chats', JSON.stringify(state.chats));
        }
    }

    // Clear Search Mode
    state.isSearching = false;
    state.searchResults = [];
    const searchInput = document.getElementById('user-search');
    if (searchInput) searchInput.value = '';
};

window.closeChat = () => {
    window.location.hash = '#chat';
};

window.logout = () => {
    state.user = null;
    state.chats = []; // Clear chats on logout
    localStorage.removeItem('oma_user');
    // Optional: Keep chats? No, privacy.
    localStorage.removeItem('oma_chats');
    window.location.hash = '#login';
};



window.loginUser = (data) => {
    state.user = data;
    localStorage.setItem('oma_user', JSON.stringify(data));
    window.location.hash = '#chat';
    initSocket();
};

window.clearChats = () => {
    if (confirm('Are you sure you want to clear your recent chats list?')) {
        state.chats = [];
        localStorage.removeItem('oma_chats');
        alert('Chats cleared.');
        render(); // Re-render sidebar
    }
};

// GROUP CHAT CLIENT LOGIC

let selectedGroupMembers = [];

window.openGroupModal = async () => {
    // 1. Fetch Users
    let users = [];
    try {
        const res = await api.searchUsers(''); // Empty = Return recent/suggested
        users = res;
        users = users.filter(u => u.id !== state.user.user.id);
    } catch (e) { console.error(e); }

    selectedGroupMembers = [];

    // 2. Build Modal HTML (Glassmorphism)
    const modalHtml = `
        <div id="group-modal-overlay" style="position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.6);backdrop-filter:blur(5px);z-index:9999;display:flex;justify-content:center;align-items:center;" onclick="if(event.target.id==='group-modal-overlay') window.closeGroupModal()">
            <div style="background:var(--sidebar-bg); border:1px solid var(--border-color); width:90%; max-width:400px; border-radius:16px; padding:24px; box-shadow:0 20px 50px rgba(0,0,0,0.3); color:var(--text-primary); transition:all 0.3s;">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
                    <h3 style="margin:0;font-size:1.4rem;font-weight:600;">Create Group</h3>
                    <button onclick="window.closeGroupModal()" class="icon-btn" style="background:transparent;width:32px;height:32px;"><i class="fas fa-times"></i></button>
                </div>
                
                <div class="input-group" style="margin-bottom:20px;">
                    <label style="color:var(--text-secondary);font-size:0.9rem;margin-bottom:8px;display:block;">Group Name</label>
                    <input type="text" id="group-name-input" placeholder="e.g. Project Team" 
                        style="width:100%;padding:12px 16px;border-radius:12px;border:1px solid var(--border-color);background:var(--bg-color);color:var(--text-primary);font-size:1rem;outline:none;">
                </div>

                <div>
                    <label style="color:var(--text-secondary);font-size:0.9rem;margin-bottom:8px;display:block;">Add Members (${users.length})</label>
                    <div id="group-members-list" style="max-height:250px; overflow-y:auto; border:1px solid var(--border-color); border-radius:12px; background:var(--bg-color);">
                        ${users.length === 0 ? '<div style="padding:20px;text-align:center;color:var(--text-secondary);">No contacts found</div>' : ''}
                        ${users.map(u => `
                            <div class="settings-item" onclick="window.toggleGroupMember('${u.id}', this)" style="padding:12px 16px; border-bottom:1px solid var(--border-color); cursor:pointer; display:flex; align-items:center; gap:12px; transition:background 0.2s;">
                                <img src="${u.avatar}" class="avatar-small" style="width:40px;height:40px;">
                                <div style="flex:1;">
                                    <h4 style="margin:0;font-size:0.95rem;color:var(--text-primary);font-weight:500;">${u.name}</h4>
                                    <p style="margin:0;font-size:0.8rem;color:var(--text-secondary);">@${u.username}</p>
                                </div>
                                <i class="fas fa-check-circle" id="check-${u.id}" style="font-size:1.2rem; color:transparent; transition:color 0.2s;"></i>
                            </div>
                        `).join('')}
                    </div>
                </div>

                <div style="margin-top:25px;">
                    <button onclick="window.submitCreateGroup()" class="primary" style="width:100%; padding:14px; font-size:1rem; border-radius:12px; font-weight:600;">Create</button>
                </div>
            </div>
        </div>
    `;

    // 3. Inject
    const div = document.createElement('div');
    div.id = 'group-modal-container';
    div.innerHTML = modalHtml;
    document.body.appendChild(div);

    // Focus input
    setTimeout(() => {
        const input = document.getElementById('group-name-input');
        if (input) input.focus();
    }, 100);
};

window.closeGroupModal = () => {
    const el = document.getElementById('group-modal-container');
    if (el) el.remove();
};

window.toggleGroupMember = (userId, el) => {
    const check = el.querySelector(`#check-${userId}`);
    if (selectedGroupMembers.includes(userId)) {
        selectedGroupMembers = selectedGroupMembers.filter(id => id !== userId);
        check.style.color = 'transparent';
        el.style.background = 'transparent';
    } else {
        selectedGroupMembers.push(userId);
        check.style.color = 'var(--primary-color)';
        el.style.background = 'var(--hover-color)';
    }
};

window.submitCreateGroup = async () => {
    const nameInput = document.getElementById('group-name-input');
    const name = nameInput.value.trim();
    if (!name) return alert('Please enter a group name');
    if (selectedGroupMembers.length === 0) return alert('Select at least one member');

    try {
        await api.createGroup(name, selectedGroupMembers);
        window.closeGroupModal();
        alert('Group created!');
        window.location.reload(); // Simple reload to fetch everything fresh
    } catch (e) {
        alert('Failed to create group: ' + e.message);
    }
};

window.addEventListener('hashchange', render);
window.addEventListener('DOMContentLoaded', () => {
    const user = localStorage.getItem('oma_user');
    if (user) {
        state.user = JSON.parse(user);
        initSocket(); // Connect immediately on load

        // Refresh User in Background
        api.getMe().then(refreshedUser => {
            let updated = false;

            // 1. Update User State
            if (JSON.stringify(refreshedUser) !== JSON.stringify(state.user.user)) {
                state.user.user = refreshedUser;
                localStorage.setItem('oma_user', JSON.stringify(state.user));
                updated = true;
            }

            // 2. Update Self in Recent Chats (if exists)
            // We use 'state.chats' which is loaded below, but 'state.chats' might not be loaded yet 
            // inside this async callback? 
            // Wait, this callback runs LATER. 'state.chats' is loaded synchronously below.
            // So it IS available.

            const selfIndex = state.chats.findIndex(c => c.id === refreshedUser.id);
            if (selfIndex !== -1) {
                if (state.chats[selfIndex].avatar !== refreshedUser.avatar) {
                    state.chats[selfIndex].avatar = refreshedUser.avatar;
                    state.chats[selfIndex].name = refreshedUser.name;
                    localStorage.setItem('oma_chats', JSON.stringify(state.chats));
                    updated = true;
                }
            }

            if (updated) {
                render();
                if (window.refreshSidebar) window.refreshSidebar();
            }
        }).catch(err => {
            if (err.message === 'Unauthorized' || err.message === 'Invalid Token') window.logout();
        });
    }

    const chats = localStorage.getItem('oma_chats');
    if (chats) {
        state.chats = JSON.parse(chats);

        // Batch Refresh All Chat Profiles
        if (state.chats.length > 0) {
            const ids = state.chats.map(c => c.id).filter(id => id !== 'general');
            if (ids.length > 0) {
                api.batchGetUsers(ids).then(freshUsers => {
                    let listUpdated = false;
                    freshUsers.forEach(fresh => {
                        const idx = state.chats.findIndex(c => c.id === fresh.id);
                        if (idx !== -1) {
                            if (state.chats[idx].avatar !== fresh.avatar || state.chats[idx].name !== fresh.name) {
                                state.chats[idx].avatar = fresh.avatar;
                                state.chats[idx].name = fresh.name;
                                listUpdated = true;
                            }
                        }
                    });
                    if (listUpdated) {
                        localStorage.setItem('oma_chats', JSON.stringify(state.chats));
                        if (window.refreshSidebar) window.refreshSidebar();
                    }
                }).catch(e => console.error("Batch sync failed", e));
            }
        }
    }

    // Fetch Groups and Recent DMs from Server
    if (state.user) {
        Promise.all([api.getGroups(), api.getRecentChats()])
            .then(([groups, recentDMs]) => {
                let listUpdated = false;
                const processChat = (item, type) => {
                    const id = item.id;
                    const existingIdx = state.chats.findIndex(c => c.id === id);

                    // Normalize standard fields
                    const chatObj = {
                        id: item.id,
                        name: item.name,
                        avatar: item.avatar,
                        lastMsg: item.lastMsg || (type === 'group' ? 'Group created' : ''),
                        time: item.timestamp || item.lastTimestamp || item.created || 0,
                        type: type,
                        status: item.status || 'offline'
                    };

                    if (existingIdx !== -1) {
                        // Update if changed
                        if (JSON.stringify(state.chats[existingIdx]) !== JSON.stringify(chatObj)) {
                            state.chats[existingIdx] = chatObj;
                            listUpdated = true;
                        }
                    } else {
                        state.chats.push(chatObj);
                        listUpdated = true;
                    }
                };

                // Process Both
                if (Array.isArray(groups)) groups.forEach(g => processChat(g, 'group'));
                if (Array.isArray(recentDMs)) recentDMs.forEach(d => processChat(d, 'user'));

                if (listUpdated) {
                    state.chats.sort((a, b) => (b.time || 0) - (a.time || 0));
                    localStorage.setItem('oma_chats', JSON.stringify(state.chats));
                    if (window.refreshSidebar) window.refreshSidebar();
                }
            })
            .catch(e => console.error("Failed to sync chats:", e));
    }

    // Load Dark Mode Preference
    const isDark = localStorage.getItem('oma_dark') === 'true';
    if (isDark) document.body.classList.add('dark-mode');

    render();

    // Debug Function
    window.testNotification = async () => {
        try {
            await api.sendTestNotification();
            alert('Test Notification Sent! Check status bar.');
        } catch (e) {
            alert('Failed: ' + e.message);
        }
    };

    // Init Socket if logged in
    if (state.user) {
        initSocket();

        // Init Push (if native) - Non-blocking
        if (typeof initPush === 'function') {
            initPush().catch(e => console.error("Push Init Failed (Non-fatal):", e));
        }
    }
});


// --- WebRTC & Socket.io Logic ---

let socket = null;
let localStream = null;
let peerConnection = null;
let currentCallTargetId = null;

const rtcConfig = {
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:stun2.l.google.com:19302' },
        { urls: 'stun:stun3.l.google.com:19302' },
        { urls: 'stun:stun4.l.google.com:19302' },
        { urls: 'stun:global.stun.twilio.com:3478' }
    ]
};



// Initialize Socket
function initSocket() {
    if (socket) {
        if (!socket.connected) {
            socket.connect();
        } else {
            // Already connected, ensure we are in the room!
            if (state.user) {
                socket.emit('join', state.user.user.id);
                console.log('Re-joined room (socket was already connected)');
            }
        }
        return;
    }

    if (typeof io === 'undefined') {
        console.error("Socket.io not loaded. Check internet or CDN.");
        alert("Connection Error: Real-time features unavailable (Socket.io failed to load).");
        return;
    }

    try {
        // Connect to Socket.io
        // MUST point to the Render Backend, not localhost!
        socket = io('https://oma-chat-app-pho0.onrender.com', {
            reconnection: true,
            reconnectionAttempts: 5,
            reconnectionDelay: 1000,
            transports: ['websocket'] // Force WebSocket to avoid polling issues
        });

        socket.on('connect', () => {
            console.log(`[Client] *** SOCKET CONNECTED: ${socket.id} ***`);
            document.documentElement.style.setProperty('--connection-status', '#22c55e'); // Green

            // NUKE GHOST SERVICE WORKERS (Fixing database.js error)
            if ('serviceWorker' in navigator) {
                navigator.serviceWorker.getRegistrations().then(registrations => {
                    for (let registration of registrations) {
                        registration.unregister();
                        console.log("[Client] 🧹 Unregistered Ghost Service Worker:", registration);
                    }
                });
            }

            if (state.user) {
                console.log('[Client] Joining Room:', state.user.user.id);
                socket.emit('join', state.user.user.id);
            }
        });

        // Online Status Events
        socket.on('online_users', (users) => {
            console.log(`[Client] Received online_users list:`, users);
            state.onlineUsers = new Set(users);
            renderMessagesView(); // Re-render to show dots
        });

        socket.on('user_status', (data) => {
            const { userId, online, lastSeen } = data;
            if (online) {
                state.onlineUsers.add(userId);
            } else {
                state.onlineUsers.delete(userId);
            }

            // Update Cache
            if (lastSeen) {
                state.userStatuses[userId] = { ...state.userStatuses[userId], lastSeen };
            }

            // Update UI dynamically
            console.log(`[Client] Received user_status:`, { userId, online, lastSeen });
            updateUserStatusUI(userId, online, lastSeen);
        });

        socket.on('disconnect', () => {
            console.log('Socket Disconnected');
            document.documentElement.style.setProperty('--connection-status', '#ef4444'); // Red
        });

        socket.on('connect_error', (err) => {
            console.error('Socket Connection Error:', err);
            document.documentElement.style.setProperty('--connection-status', '#f59e0b'); // Orange
        });

        // Incoming Offer (Receive Call)
        socket.on('offer', async (data) => {
            console.log('Incoming Offer:', data);
            currentCallTargetId = data.callerId;
            soundManager.play('ringtone'); // Start Ringing

            // VIBRATE (Mobile Haptics)
            if (navigator.vibrate) {
                // Vibrate pattern: 1s ON, 1s OFF, repeat
                // Note: Web Vibration API doesn't support "infinite" loops nicely without setInterval,
                // but for now a long pattern is a good start. 
                // Or we can assume the Ringtone loop handles the "sound", vibration can be one-shot or long.
                // Let's do a long burst sequence.
                navigator.vibrate([1000, 1000, 1000, 1000, 1000, 1000, 1000]);
            }

            const popup = document.getElementById('incoming-call-popup');
            const nameEl = document.getElementById('caller-name');
            const avatarEl = document.getElementById('caller-avatar');
            if (popup) {
                popup.classList.remove('hidden');
                nameEl.textContent = data.callerName || 'Unknown';
                avatarEl.src = data.callerAvatar || 'https://ui-avatars.com/api/?name=U';
            }
            window.pendingOffer = data.offer;
            window.pendingCallType = data.type || 'video';
        });

        // Call Answered
        socket.on('answer', async (data) => {
            console.log('Call Answered:', data);
            soundManager.stop('calling'); // Stop Calling Tone
            if (peerConnection) {
                await peerConnection.setRemoteDescription(new RTCSessionDescription(data.answer));
                startCallTimer(); // Start timer for caller
                wasConnected = true;
            }
        });

        // ICE Candidate
        socket.on('ice-candidate', (data) => {
            if (peerConnection) {
                peerConnection.addIceCandidate(new RTCIceCandidate(data.candidate));
            }
        });

        // End Call
        socket.on('end-call', () => {
            endCallCleanup(true);
        });

    } catch (e) {
        console.error("Socket Init Failed", e);
    }
}

// Handle Mobile Sleep/Wake
document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
        if (socket && !socket.connected) {
            console.log("App woke up, reconnecting socket...");
            socket.connect();
        }
    }
});

// Start Call (Caller Side)
window.startCall = async (type = 'video', targetId = null) => {
    // Sanitize
    if (typeof type !== 'string') type = 'video';

    // If target provided (e.g. from Calls tab), set it as active
    if (targetId) {
        state.activeChatId = targetId;
    }


    // If target provided (e.g. from Calls tab), set it as active
    if (targetId) {
        state.activeChatId = targetId;
    }




    // Check if group
    if (state.activeChatId.includes('-') && !state.chats.find(c => c.id === state.activeChatId && c.type !== 'group')) {
        const chat = state.chats.find(c => c.id === state.activeChatId);
        if (chat && chat.type === 'group') return alert("Group calls coming soon!");
    }

    currentCallTargetId = state.activeChatId;

    document.getElementById('video-call-modal').classList.remove('hidden');
    document.getElementById('call-status').textContent = type === 'audio' ? "Calling (Voice)..." : "Calling...";

    soundManager.play('calling'); // Start Calling Tone

    // UI Toggle
    const wrapper = document.querySelector('.video-wrapper');
    if (type === 'audio') {
        wrapper.classList.add('audio-mode');
        // Set Avatar
        const chat = state.chats.find(c => c.id === state.activeChatId);
        const avatarImg = document.getElementById('audio-avatar-img');
        if (avatarImg) {
            // Default first to prevent stale image
            avatarImg.src = 'https://ui-avatars.com/api/?name=User';
            if (chat && chat.avatar) {
                avatarImg.src = chat.avatar;
            }
        }
    } else {
        wrapper.classList.remove('audio-mode');
    }

    await setupLocalMedia(type === 'video');

    // Disable video track for audio-only calls
    if (type === 'audio' && localStream) {
        const videoTrack = localStream.getVideoTracks()[0];
        if (videoTrack) videoTrack.enabled = false;
    }
    createPeerConnection();

    // Create Offer
    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);

    // Send Offer
    socket.emit('offer', {
        targetId: currentCallTargetId,
        callerId: state.user.user.id,
        callerName: state.user.user.name,
        callerAvatar: state.user.user.avatar,
        offer: offer,
        type: type
    });

};

/* --- Global Helpers (Moved out of startCall) --- */

function getHeaderStatusText(chat) {
    if (chat.id === 'general') return 'Tap to view info';

    if (state.onlineUsers.has(chat.id)) return '<span style="color:#22c55e;font-weight:600;">Online</span>';

    const status = state.userStatuses[chat.id];
    const lastSeen = (status && status.lastSeen) || chat.lastSeen;

    if (lastSeen) {
        if (lastSeen === 'Recently') return 'Last seen recently';
        return 'Last seen ' + timeAgo(lastSeen);
    }

    return 'Offline';
}

function timeAgo(timestamp) {
    const seconds = Math.floor((Date.now() - timestamp) / 1000);
    if (seconds < 60) return 'Just now';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    return 'Long time ago';
}

function updateUserStatusUI(userId, online, lastSeen) {
    if (state.activeTab === 'messages') {
        const sidebarContent = document.querySelector('.chat-list');
        if (sidebarContent) {
            const sidebarMain = document.getElementById('sidebar-main');
            if (sidebarMain) {
                sidebarMain.innerHTML = renderSidebarMain();
            }
        }
    }
    if (state.activeChatId === userId) {
        const headerStatus = document.getElementById('header-status');
        if (headerStatus) {
            const newText = getHeaderStatusText({ id: userId });
            console.log(`[Client] Updating header status for ${userId} to: ${newText}`);
            headerStatus.innerHTML = newText;
        }
    }
}

window.savePrivacy = async () => {
    const lastSeen = document.getElementById('privacy-lastseen').value;
    const readReceipts = document.getElementById('privacy-readreceipts').checked;
    state.user.user.settings = { ...state.user.user.settings, lastSeenPrivacy: lastSeen, readReceipts: readReceipts };
    try {
        await api.updateProfile({ settings: state.user.user.settings });
        localStorage.setItem('oma_user', JSON.stringify(state.user));
    } catch (e) { alert("Failed to save privacy settings"); }
};



// Answer Call (Callee Side)
window.answerCall = async () => {
    soundManager.stop('ringtone'); // Stop Ringing
    document.getElementById('incoming-call-popup').classList.add('hidden');
    document.getElementById('video-call-modal').classList.remove('hidden');
    document.getElementById('call-status').textContent = "Connecting...";

    // Determine Type
    const type = window.pendingCallType || 'video';
    const isVideo = type === 'video';

    // UI Toggle
    const wrapper = document.querySelector('.video-wrapper');
    if (type === 'audio') {
        wrapper.classList.add('audio-mode');
        // Set Avatar 
        const avatarImg = document.getElementById('audio-avatar-img');
        const callerAvatarEl = document.getElementById('caller-avatar');

        if (avatarImg) {
            avatarImg.src = 'https://ui-avatars.com/api/?name=User'; // Reset first
            if (callerAvatarEl && callerAvatarEl.src) {
                avatarImg.src = callerAvatarEl.src;
            }
        }
    } else {
        wrapper.classList.remove('audio-mode');
    }

    await setupLocalMedia(isVideo);
    createPeerConnection();

    await peerConnection.setRemoteDescription(new RTCSessionDescription(window.pendingOffer));

    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);

    socket.emit('answer', {
        targetId: currentCallTargetId,
        answer: answer
    });

    startCallTimer(); // Start timer for callee
    wasConnected = true;
};

window.rejectCall = () => {
    soundManager.stop('ringtone'); // Stop Ringing
    document.getElementById('incoming-call-popup').classList.add('hidden');
    socket.emit('end-call', { targetId: currentCallTargetId });

    // Log "Declined"
    if (currentCallTargetId) {
        api.sendMessage(`Declined`, 'call_log', currentCallTargetId).catch(console.error);
    }

    currentCallTargetId = null;
    window.pendingOffer = null;
};

window.endCall = () => {
    if (currentCallTargetId) {
        socket.emit('end-call', { targetId: currentCallTargetId });
    }
    endCallCleanup(false);
};

function endCallCleanup(isRemote = false) {
    soundManager.stopAll(); // Ensure all sounds stop
    if (navigator.vibrate) navigator.vibrate(0); // Stop Vibration

    const target = currentCallTargetId;

    document.getElementById('video-call-modal').classList.add('hidden');
    document.getElementById('incoming-call-popup').classList.add('hidden'); // Ensure closed

    if (callTimerInterval) clearInterval(callTimerInterval);

    if (peerConnection) {
        peerConnection.close();
        peerConnection = null;
    }

    // Stop all media tracks (Camera & Mic)
    if (localStream) {
        try {
            localStream.getTracks().forEach(track => {
                track.stop();
                track.enabled = false; // Double tap
            });
        } catch (e) { console.error("Track stop error", e); }
        localStream = null;
    }

    // Clear Video Elements
    document.getElementById('local-video').srcObject = null;
    document.getElementById('remote-video').srcObject = null;

    // Log the Call
    if (target) {
        if (wasConnected && callSeconds > 0) {
            if (!isRemote) {
                const mins = Math.floor(callSeconds / 60).toString().padStart(2, '0');
                const secs = (callSeconds % 60).toString().padStart(2, '0');
                api.sendMessage(`Answered (${mins}:${secs})`, 'call_log', target).catch(console.error);
            }
        } else {
            if (!isRemote) {
                let logMessage = "";
                // Local party ended the call before connection
                if (window.isCaller) {
                    logMessage = "No Answer"; // I was caller, I gave up
                } else {
                    logMessage = "Declined"; // I was callee, I Rejected call
                }

                if (logMessage) {
                    api.sendMessage(logMessage, 'call_log', target).catch(console.error);
                }
            }
        }
    }

    currentCallTargetId = null;

    window.pendingOffer = null;

    stopCallTimer();
    wasConnected = false;
    window.isCaller = false; // Reset
}

let wasConnected = false; // Track if we ever established connection to distinguish missed calls (naive)
let isCaller = false; // Track if the current user initiated the call

let callTimerInterval = null;
let callSeconds = 0;

function startCallTimer() {
    stopCallTimer();
    callSeconds = 0;
    const timerEl = document.getElementById('call-timer');
    if (timerEl) {
        timerEl.textContent = "00:00";
        timerEl.classList.remove('hidden');
    }

    callTimerInterval = setInterval(() => {
        callSeconds++;
        const mins = Math.floor(callSeconds / 60).toString().padStart(2, '0');
        const secs = (callSeconds % 60).toString().padStart(2, '0');
        if (timerEl) timerEl.textContent = `${mins}:${secs}`;
    }, 1000);
}

function stopCallTimer() {
    if (callTimerInterval) {
        clearInterval(callTimerInterval);
        callTimerInterval = null;
    }
    const timerEl = document.getElementById('call-timer');
    if (timerEl) timerEl.classList.add('hidden');
}

window.toggleMute = () => {
    if (localStream) {
        const audioTrack = localStream.getAudioTracks()[0];
        if (audioTrack) {
            audioTrack.enabled = !audioTrack.enabled;
            // UI Feedback
            const btn = document.getElementById('btn-toggle-mute');
            const icon = btn.querySelector('i');
            const statusIcon = document.getElementById('status-mic-off');

            if (!audioTrack.enabled) {
                btn.classList.add('disabled');
                icon.className = 'fas fa-microphone-slash';
                // Show status icon (Mic Off)
                if (statusIcon) statusIcon.classList.remove('hidden');
            } else {
                btn.classList.remove('disabled');
                icon.className = 'fas fa-microphone';
                if (statusIcon) statusIcon.classList.add('hidden');
            }
        }
    }
};

window.toggleVideo = () => {
    if (localStream) {
        const videoTrack = localStream.getVideoTracks()[0];
        if (videoTrack) {
            videoTrack.enabled = !videoTrack.enabled;
            // UI Feedback
            const btn = document.getElementById('btn-toggle-video');
            const icon = btn.querySelector('i');

            if (!videoTrack.enabled) {
                btn.classList.add('disabled');
                icon.className = 'fas fa-video-slash';
            } else {
                btn.classList.remove('disabled');
                icon.className = 'fas fa-video';
            }
        }
    }
};

async function setupLocalMedia(videoEnabled = true) {
    try {
        localStream = await navigator.mediaDevices.getUserMedia({ video: videoEnabled, audio: true });

        // Only attach to video element if video is enabled
        if (videoEnabled) {
            document.getElementById('local-video').srcObject = localStream;
        } else {
            document.getElementById('local-video').srcObject = null;
        }
    } catch (e) {
        console.error("Media Access Denied", e);
        // Specialized Error Messages
        let msg = "Camera/Mic permission needed.";
        if (location.protocol !== 'https:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
            msg = "Mobile browsers require HTTPS for Camera access. Please use localhost or setup SSL.";
        } else if (e.name === 'NotAllowedError' || e.name === 'PermissionDeniedError') {
            msg = "Permission denied. Please allow camera access in browser settings.";
        } else if (e.name === 'NotFoundError') {
            msg = "No camera/mic found.";
        }
        alert(msg);
        endCall(); // Cancel call if no media
        throw e;
    }
}

function createPeerConnection() {
    peerConnection = new RTCPeerConnection(rtcConfig);

    // Add Local Tracks
    localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));

    // Handle ICE Candidates
    peerConnection.onicecandidate = (event) => {
        if (event.candidate) {
            socket.emit('ice-candidate', {
                targetId: currentCallTargetId,
                candidate: event.candidate
            });
        }
    };

    // Handle Remote Stream
    peerConnection.ontrack = (event) => {
        document.getElementById('remote-video').srcObject = event.streams[0];
        document.getElementById('call-status').textContent = ""; // Clear status text
    };
}

// Hook into Login
// Hook into Login
// (Combined into main loginUser function above)
// --- Push Notification Logic ---

async function registerPush() {
    // alert("Debug: registerPush() called!"); // Removed Debug Alert
    const hasCap = !!window.Capacitor;
    const isNative = hasCap && window.Capacitor.isNativePlatform();
    // alert(`Debug: Cap=${hasCap}, Native=${isNative}`); // Removed Debug Alert

    // Only run on mobile (Capacitor)
    if (isNative) {
        // const { PushNotifications } = window.Capacitor.Plugins; // Removed: using import

        try {
            // Create High Priority Channel for Calls (Android)
            await PushNotifications.createChannel({
                id: 'call_channel',
                name: 'Call Notifications',
                description: 'Incoming Audio/Video Calls',
                importance: 5, // High/Max
                visibility: 1, // Public
                sound: 'calling', // references /android/app/src/main/res/raw/calling.mp3 if exists, else default
                vibration: true
            });

            // Create High Priority Channel for Messages (Android) - Enables Banners
            await PushNotifications.createChannel({
                id: 'message_channel',
                name: 'Message Notifications',
                description: 'Incoming Text Messages',
                importance: 4, // High (4) or Max (5) for heads-up
                visibility: 1, // Public
                vibration: true
            });

            // alert('Push: Initializing...'); // Removed Debug Alert
            await PushNotifications.addListener('registration', async ({ value }) => {
                // alert('Push: Token received!'); // Removed Debug Alert
                console.log('Mobile Push Token:', value);
                try {
                    await api.updatePushToken(value);
                    console.log('Push Token sent to server');
                } catch (e) {
                    console.error('Failed to send push token', e);
                }
            });

            await PushNotifications.addListener('registrationError', (error) => {
                alert('Push: Registration Error: ' + JSON.stringify(error));
                console.error('Error on registration: ' + JSON.stringify(error));
            });

            await PushNotifications.addListener('pushNotificationReceived', (notification) => {
                console.log('Push received: ', notification);
                // Show a toast or update UI?
                // For now just log
            });

            await PushNotifications.addListener('pushNotificationActionPerformed', (notification) => {
                const data = notification.notification.data;
                console.log('Push action performed:', data);
                if (data.chatId) {
                    window.openChat(data.chatId);
                }
            });

            // Request permission
            const permStatus = await PushNotifications.checkPermissions();
            if (permStatus.receive === 'prompt') {
                const newPerm = await PushNotifications.requestPermissions();
            }
            if (permStatus.receive !== 'denied') {
                await PushNotifications.register();
            }
        } catch (e) {
            console.error("Push registration failed", e);
        }
    } else {
        console.log("Web Push not implemented yet (requires Service Worker)");
    }
}


// Expose checks and function for manual debugging
window.registerPush = registerPush;
window.checkCapacitor = () => {
    alert(`Capacitor: ${!!window.Capacitor}\nNative: ${window.Capacitor ? window.Capacitor.isNativePlatform() : 'N/A'}`);
};

// Ensure init is called
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}
