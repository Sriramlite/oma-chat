import { api } from './api.js';
import { PushNotifications } from './capacitor-push/index.js';
import { registerPlugin, Capacitor } from './capacitor-core.js';
import { FirebaseAuthentication } from './capacitor-firebase-auth/index.js';

// Register App Plugin for Deep Links
const App = registerPlugin('App');

// Deep Link Handler (Mobile)
if (Capacitor.isNativePlatform()) {
    App.addListener('appUrlOpen', data => {
        console.log('App opened with URL:', data.url);
        if (firebase.auth().isSignInWithEmailLink(data.url)) {
            console.log("Detected Deep Link Email Sign-In");
            handleEmailLinkSignIn(data.url);
        }
    });
}
import { db } from './db.js';
import { sync } from './sync.js';
import { nearby } from './nearby.js'; // BLE Module
import { Device } from './capacitor-device/index.js'; // Ensure correct path/export



window.showCustomAlert = function (msg, type = 'error') {
    const existing = document.querySelector('.custom-alert');
    if (existing) existing.remove();

    const alertEl = document.createElement('div');
    alertEl.className = 'custom-alert animate__animated animate__fadeInDown';
    alertEl.innerHTML = `
        <div class="custom-alert__icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" viewBox="0 0 24 24" height="24" fill="none"><path fill="#fff" d="m13 13h-2v-6h2zm0 4h-2v-2h2zm-1-15c-1.3132 0-2.61358.25866-3.82683.7612-1.21326.50255-2.31565 1.23915-3.24424 2.16773-1.87536 1.87537-2.92893 4.41891-2.92893 7.07107 0 2.6522 1.05357 5.1957 2.92893 7.0711.92859.9286 2.03098 1.6651 3.24424 2.1677 1.21325.5025 2.51363.7612 3.82683.7612 2.6522 0 5.1957-1.0536 7.0711-2.9289 1.8753-1.8754 2.9289-4.4189 2.9289-7.0711 0-1.3132-.2587-2.61358-.7612-3.82683-.5026-1.21326-1.2391-2.31565-2.1677-3.24424-.9286-.92858-2.031-1.66518-3.2443-2.16773-1.2132-.50254-2.5136-.7612-3.8268-.7612z"></path></svg>
        </div>
        <div class="custom-alert__title">${msg}</div>
        <div class="custom-alert__close" onclick="this.parentElement.remove()">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" viewBox="0 0 20 20" height="20"><path fill="#fff" d="m15.8333 5.34166-1.175-1.175-4.6583 4.65834-4.65833-4.65834-1.175 1.175 4.65833 4.65834-4.65833 4.6583 1.175 1.175 4.65833-4.6583 4.6583 4.6583 1.175-1.175-4.6583-4.6583z"></path></svg>
        </div>
    `;
    document.body.appendChild(alertEl);
    setTimeout(() => {
        if (alertEl.parentElement) {
            alertEl.classList.replace('animate__fadeInDown', 'animate__fadeOutUp');
            setTimeout(() => alertEl.remove(), 500);
        }
    }, 4000);
};

// Override default alert
window.alert = (msg) => window.showCustomAlert(msg);

const state = {
    user: null,
    chats: [],
    messages: [],
    activeChatId: null,
    isSearching: false,
    searchResults: [],
    mobileView: 'list', // 'list' or 'chat'
    pendingPhone: null,
    settingsView: null, // null, 'profile', 'appearance', etc.
    activeTab: 'messages', // 'messages', 'calls', 'contacts', 'profile'
    // onlineUsers: new Set(), 
    activeTab: 'messages', // 'messages', 'calls', 'contacts', 'profile'
    onlineUsers: new Set(), // Set of user IDs
    userStatuses: {}, // Map of userId -> { online, lastSeen }
    animatedChats: new Set() // Track chats that have already played initial animation
};

// Helper: Securely update and deduplicate state.chats
function updateStateChats(newChatsOrSingle) {
    const list = Array.isArray(newChatsOrSingle) ? newChatsOrSingle : [newChatsOrSingle];
    const chatMap = new Map(state.chats.map(c => [c.id, c]));
    
    list.forEach(c => {
        if (!c.id) return;
        const existing = chatMap.get(c.id) || {};
        chatMap.set(c.id, { ...existing, ...c });
    });
    
    state.chats = Array.from(chatMap.values());
}

// --- CACHE HELPERS (Optimized: Per-Chat Keys) ---
function saveChatToCache(chatId, messages) {
    if (!chatId) return;
    try {
        // Only save last 50 to keep storage small and fast
        localStorage.setItem(`oma_msg_${chatId}`, JSON.stringify(messages.slice(-50)));
    } catch (e) { console.error("Cache Save Error:", e); }
}

function loadChatFromCache(chatId) {
    if (!chatId) return null;
    try {
        const data = localStorage.getItem(`oma_msg_${chatId}`);
        return data ? JSON.parse(data) : null;
    } catch (e) { return null; }
}

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
            sound.muted = true; // Mute for unlock
            sound.play().then(() => {
                sound.pause();
                sound.currentTime = 0;
                sound.muted = false; // Unmute for future use
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

// Helper for Filter Switching - Global Scope
window.setChatFilter = (mode) => {
    if (state.chatFilter === mode) return; // No change

    state.chatFilter = mode;
    render(); // Re-render sidebar to show active chip and correct list

    if (mode === 'nearby') {
        // DEBUG: Alert
        alert("Switching to Nearby Mode...");
        if (window.nearby && window.nearby.startScanning) {
            window.nearby.startScanning();
            window.refreshNearbyList();
        } else {
            // Try to init nearby if not ready
            if (window.nearby && window.nearby.init) {
                window.nearby.init().then(() => {
                    window.nearby.startScanning();
                    window.refreshNearbyList();
                });
            } else {
                console.warn("Nearby module not loaded.");
                window.showCustomAlert("Nearby module not ready", "error");
            }
        }
    } else {
        if (window.nearby && window.nearby.stopScanning) {
            window.nearby.stopScanning();
        }
    }
};

// --- Initialization ---

async function init() {
    await initFirebaseClient(); // Ensure Firebase is ready

    // Check for Email Magic Link (Must be first)
    if (firebase.auth().isSignInWithEmailLink(window.location.href)) {
        console.log("Detected Email Link Sign-In");
        handleEmailLinkSignIn();
        return;
    }

    // Auto Login
    const storedUser = localStorage.getItem('oma_user');
    if (storedUser) {
        state.user = JSON.parse(storedUser);
        registerPush(); // Ensure push is registered on auto-login

        initSocket(); // Enable Real-time
        setupProfileSync(); // Listen for updates
        initNotificationManager(); // Register SW and Permissions

        // Initialize Offline Sync
        sync.init();

        // One-time Native Permission Request (Android/iOS)
        if (Capacitor.isNativePlatform()) {
            const Permissions = registerPlugin('Permissions');
            if (Permissions && Permissions.requestPermissions) {
                Permissions.requestPermissions(['camera', 'microphone', 'photos']).catch(() => {});
            }
        }

        // Initialize State defaults
        state.chatFilter = 'global';
        state.nearby = state.nearby || { peers: [], messages: [] };

        // Helper to refresh Nearby UI from nearby.js data
        window.refreshNearbyList = () => {
            if (state.chatFilter !== 'nearby') return;

            // Convert Map to Array for UI
            const peers = Array.from(window.nearby.peers.values());
            const listContainer = document.getElementById('chat-list');
            if (listContainer) {
                const chatObjs = peers.map(p => ({
                    id: p.id,
                    name: p.name,
                    lastMsg: `Signal: ${p.rssi} dBm`,
                    time: 'Nearby',
                    avatar: 'https://ui-avatars.com/api/?name=' + encodeURIComponent(p.name) + '&background=random',
                    isNearby: true
                }));

                listContainer.innerHTML = `
                    <div class="pull-indicator" id="pull-indicator"><i class="fas fa-spinner"></i></div>
                    ${renderChatListContent(chatObjs)}
                `;
            }
        };

        // Listen for Network Status
        window.addEventListener('network-status', (e) => {
            const isOnline = e.detail.online;
            if (!isOnline) {
                window.showCustomAlert('You are offline. Data will sync when online.', 'warning');
                document.body.classList.add('offline-mode');
            } else {
                window.showCustomAlert('Back online. Syncing...', 'success');
                document.body.classList.remove('offline-mode');
            }
        });

        // Load Chats
        const chats = localStorage.getItem('oma_chats');
        if (chats) state.chats = JSON.parse(chats);

        // Try loading from DB if empty
        if (state.chats.length === 0) {
            const overlay = document.getElementById('restoration-overlay');
            if (overlay) overlay.classList.remove('hidden');

            db.getChats().then(c => {
                if (c && c.length > 0) {
                    state.chats = c;
                    render();
                }
            }).finally(() => {
                if (overlay) {
                    overlay.style.opacity = '0';
                    setTimeout(() => overlay.classList.add('hidden'), 500);
                }
            });

            // Watchdog Timer (Safety)
            setTimeout(() => {
                if (overlay && !overlay.classList.contains('hidden')) {
                    console.warn("Restoration overlay watchdog triggered.");
                    overlay.classList.add('hidden');
                }
            }, 10000); // 10s max
        }

        render(); // Initial Render
        // refreshSidebar(); // Assuming this function exists elsewhere or will be added

        // Sync Profile Data
        syncProfile();

        // Register Push (Mobile)
        registerPush();
    } else {
        render(); // Render login/signup if not authenticated
    }
}

const AppPlugin = App || (window.Capacitor && window.Capacitor.Plugins ? window.Capacitor.Plugins.App : null);

if (AppPlugin) {
    AppPlugin.addListener('backButton', async () => {
        try {
            // DEBUG: Confirm Event
            // alert('Back Button Pressed'); 

            // 1. Modals & Overlays (Priority)
            const incomingCall = document.getElementById('incoming-call-popup');
            if (incomingCall && !incomingCall.classList.contains('hidden')) {
                window.rejectCall();
                return;
            }

            const groupModal = document.getElementById('group-modal-container');
            if (groupModal) {
                window.closeGroupModal();
                return;
            }

            const profileModal = document.getElementById('user-profile-modal');
            if (profileModal) {
                window.closeUserProfile();
                return;
            }

            const mediaViewer = document.getElementById('media-viewer-modal');
            if (mediaViewer && !mediaViewer.classList.contains('hidden')) {
                window.closeMediaViewer();
                return;
            }

            // 2. Attachment/Emoji/Menus
            const attachMenu = document.getElementById('attachment-menu');
            if (attachMenu && !attachMenu.classList.contains('hidden')) {
                attachMenu.classList.add('hidden');
                return;
            }
            const emojiPicker = document.getElementById('emoji-picker');
            if (emojiPicker && !emojiPicker.classList.contains('hidden')) {
                emojiPicker.classList.add('hidden');
                return;
            }
            const chatMenu = document.getElementById('chat-menu-dropdown');
            if (chatMenu && !chatMenu.classList.contains('hidden')) {
                chatMenu.classList.add('hidden');
                return;
            }

            // 3. Navigation Logic (Hash Based)
            const hash = window.location.hash;

            // If in Chat (#chat/...), go back to list
            if (hash.startsWith('#chat/') && hash.length > 6) {
                window.closeChat(); // Sets hash to #chat (which render handles as list?) or we should set to #
                // window.location.hash = ''; // Force list
                return;
            }

            // If in Nearby Mode, switch to Global
            if (state.chatFilter === 'nearby') {
                window.setChatFilter('global');
                return;
            }

            // If in Login Sub-state, go back to Login Choice
            if (hash === '#login/phone' || hash === '#login/email') {
                window.location.hash = '#login';
                return;
            }
            
            // If Searching, clear search
            if (state.isSearching) {
                state.isSearching = false;
                state.searchResults = [];
                const searchInput = document.getElementById('user-search');
                if (searchInput) {
                    searchInput.value = '';
                    window.render(); // Force render to clear search results
                }
                return;
            }

            // If in Name Setup or Login Choice, go back to Landing
            if (hash === '#setup' || hash === '#login' || hash === '#register') {
                window.location.hash = '';
                return;
            }

            // If in Settings
            if (state.settingsView) {
                window.closeSettings();
                return;
            }

            // 4. Root Tab Handling
            // If not on 'messages' tab, switch to it
            if (state.activeTab !== 'messages') {
                window.switchTab('messages');
            } else {
                // 4. Default: Minimize App (Home Screen)
                AppPlugin.minimizeApp();
            }
        } catch (error) {
            console.error("Back Button Error:", error);
            // Fallback
            AppPlugin.minimizeApp();
        }
    });
    console.log("Back Button Listener Attached via App Plugin");
} else {
    console.warn("Capacitor App Plugin not found. Back button handling disabled.");
}

async function syncProfile() {
    try {
        const userData = await api.getMe();
        if (userData && userData.user) {
            state.user.user = { ...state.user.user, ...userData.user };
            localStorage.setItem('oma_user', JSON.stringify(state.user));
            if (state.activeTab === 'profile' || state.settingsView === 'profile') {
                render();
            }
        }
    } catch (e) {
        console.error("Profile sync failed", e);
    }
}

// --- Navigation Logic ---
window.switchTab = async (tab) => {
    state.activeTab = tab;
    state.settingsView = null; // Close settings if open
    render();
    if (tab === 'profile') {
        syncProfile();
    }
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
    const landing = document.getElementById('landing-page');

    if (!app) {
        console.error("Critical: #app element not found. Retrying render...", new Error().stack);
        setTimeout(render, 50);
        return;
    }

    if (!state.user) {
        // Not Logged In
        const hash = window.location.hash;

        if (hash.startsWith('#login')) {
            if (landing) landing.style.display = 'none';
            app.style.display = 'block';
            
            if (hash === '#login/phone') {
                app.innerHTML = renderPhoneLoginContent();
                const form = document.getElementById('phone-login-form');
                if (form) form.onsubmit = handleSendOTP;
            } else if (hash === '#login/email') {
                app.innerHTML = renderEmailLoginContent();
                const form = document.getElementById('email-login-form');
                if (form) form.onsubmit = handleSendEmailLink;
            } else {
                app.innerHTML = renderLogin();
                const form = document.getElementById('login-form');
                if (form) form.onsubmit = handleLogin;
            }
        } else if (hash === '#register') {
            if (landing) landing.style.display = 'none';
            app.style.display = 'block';
            app.innerHTML = renderRegister();
            const form = document.getElementById('signup-form');
            if (form) form.onsubmit = handleSignup;
        } else {
            // Default: Landing Page
            if (landing) landing.style.display = 'block';
            app.style.display = 'none';
        }
        return;
    }

    // Logged In
    if (landing) landing.style.display = 'none';
    app.style.display = 'block';

    // If still on login/register hash, switch to chat
    if (window.location.hash === '#login' || window.location.hash === '#register' || !window.location.hash) {
        window.location.hash = '#chat';
    }

    const currentHash = window.location.hash;

    app.className = '';

    if (currentHash.startsWith('#chat')) {
        const parts = currentHash.split('/');
        if (parts.length > 1 && parts[1]) {
            state.activeChatId = parts[1];
            state.mobileView = 'chat';
        } else {
            // Default view (list)
            state.mobileView = 'list';
            state.activeChatId = null;
        }
        renderChatLayout(app);
    } else if (currentHash === '#setup') {
        app.innerHTML = renderNameSetupContent();
        const form = document.getElementById('name-setup-form');
        if (form) form.onsubmit = handleNameSetup;
    } else if (currentHash === '#login') {
        app.innerHTML = renderLogin();
    } else if (currentHash === '#register') {
        app.innerHTML = renderRegister();
    } else if (currentHash === '#forgot-password') {
        app.innerHTML = renderForgotPassword();
    } else if (currentHash === '#reset-password') {
        app.innerHTML = renderResetPassword();
    } else if (currentHash === '#updates') {
        app.innerHTML = renderUpdates();
    } else {
        app.innerHTML = '<h1>404</h1>';
    }

    // Bind Events after injection
    if (currentHash === '#login') {
        const form = document.getElementById('login-form');
        if (form) form.onsubmit = handleLogin;
    } else if (currentHash === '#register') {
        const form = document.getElementById('signup-form');
        if (form) form.onsubmit = handleSignup;
    } else if (currentHash === '#forgot-password') {
        const form = document.getElementById('forgot-form');
        if (form) form.onsubmit = handleForgotPassword;
    } else if (currentHash === '#reset-password') {
        const form = document.getElementById('reset-form');
        if (form) form.onsubmit = handleResetPassword;
    }
}

function renderLogin() {
    return `
        ${renderAuthNavbar()}
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Log in</h2>
                <form id="login-form">
                    <input type="text" id="username" placeholder="Username" required>
                    <input type="password" id="password" placeholder="Password" required>
                    <button type="submit">Log In</button>
                    <div style="text-align:center; margin: 15px 0; color: grey; font-size: 0.8rem;">OR</div>
                    <div style="display:flex; gap:10px; justify-content:center;">
                        <button type="button" class="secondary" disabled style="flex:1; background: rgba(0,0,0,0.05); color: grey; border: 1px solid rgba(0,0,0,0.1); font-size:0.8rem; cursor: not-allowed; opacity: 0.7;">Phone Login (Soon)</button>
                        <button type="button" class="secondary" onclick="window.switchEmailLogin()" style="flex:1; background: rgba(var(--primary-color-rgb), 0.1); color: var(--primary-color); border: 1px solid var(--primary-color); font-size:0.8rem;">Email Login</button>
                    </div>
                    <button type="button" onclick="window.handleGoogleLogin()" style="margin-top: 15px; background: #fff; color: #3c4043; border: 1px solid #dadce0; border-radius: 4px; display: flex; align-items: center; justify-content: center; gap: 10px; font-family: 'Google Sans', arial, sans-serif; font-weight: 500; font-size: 14px; padding: 10px; width: 100%; transition: background-color .2s box-shadow .2s;">
                        <img src="https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg" width="18" height="18" alt="G">
                        Sign in with Google
                    </button>
                    <a href="#register" style="display:block; margin-top:15px;">Create Account</a>
                    <a href="#forgot-password" style="display:block; margin-top:10px; color: grey; font-size: 0.8rem;">Forgot Password?</a>
                    <div id="error-msg" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
}

function renderForgotPassword() {
    return `
        ${renderAuthNavbar()}
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Forgot Password</h2>
                <p style="color: grey; font-size: 0.85rem; margin-bottom: 20px;">Enter your username to receive an OTP via WhatsApp.</p>
                <form id="forgot-form">
                    <input type="text" id="forgot-username" placeholder="Username" required>
                    <button type="submit" id="btn-forgot-send">Send OTP</button>
                    <a href="#login" style="display:block; margin-top:15px;">Back to Login</a>
                    <div id="forgot-error" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
}

async function handleForgotPassword(e) {
    e.preventDefault();
    const username = document.getElementById('forgot-username').value;
    const btn = document.getElementById('btn-forgot-send');
    const err = document.getElementById('forgot-error');

    btn.disabled = true;
    btn.innerText = "Sending...";

    try {
        await api.forgotPassword(username);
        localStorage.setItem('reset_username', username);
        window.location.hash = '#reset-password';
    } catch (e) {
        err.innerText = e.message;
        btn.disabled = false;
        btn.innerText = "Send OTP";
    }
}

function renderResetPassword() {
    const username = localStorage.getItem('reset_username') || '';
    return `
        ${renderAuthNavbar()}
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Reset Password</h2>
                <p style="color: grey; font-size: 0.85rem; margin-bottom: 20px;">Enter the 6-digit OTP sent to your WhatsApp.</p>
                <form id="reset-form">
                    <input type="text" id="reset-username" value="${username}" required disabled style="background:#f0f0f0;">
                    <input type="text" id="reset-otp" placeholder="6-digit OTP" required maxlength="6">
                    <input type="password" id="reset-new-password" placeholder="New Password (min 8 chars)" required minlength="8">
                    <button type="submit" id="btn-reset-confirm">Reset Password</button>
                    <div id="reset-error" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
}

async function handleResetPassword(e) {
    e.preventDefault();
    const username = localStorage.getItem('reset_username');
    const otp = document.getElementById('reset-otp').value;
    const newP = document.getElementById('reset-new-password').value;
    const btn = document.getElementById('btn-reset-confirm');
    const err = document.getElementById('reset-error');

    btn.disabled = true;
    btn.innerText = "Resetting...";

    try {
        await api.resetPassword(username, otp, newP);
        localStorage.removeItem('reset_username');
        showCustomAlert('Password reset successfully! Please login.');
        window.location.hash = '#login';
    } catch (e) {
        err.innerText = e.message;
        btn.disabled = false;
        btn.innerText = "Reset Password";
    }
}

function renderUpdates() {
    const changelog = [
        {
            version: 'v2.8.5',
            date: 'March 2026',
            title: 'Security & Recovery',
            tags: ['Security', 'New Feature'],
            items: [
                'WhatsApp-based Password Recovery (OTP).',
                'Upgraded to Bcrypt salted hashing for passwords.',
                'Implemented IP-based Rate Limiting on login/signup.',
                'Mandatory phone verification for enhanced security.',
                'Fixed MongoDB consistency in password management.'
            ]
        },
        {
            version: 'v2.8.0',
            date: 'Feb 2026',
            title: 'WhatsApp Integration',
            tags: ['Integration'],
            items: [
                'Fast2SMS WhatsApp API integration.',
                'Verified OTP delivery via Meta Proxy.',
                'Support for WhatsApp business templates.'
            ]
        }
    ];

    const features = [
        { icon: 'fa-comments', title: 'Real-time Chat', desc: 'Secure global and private messaging via Socket.IO.' },
        { icon: 'fa-video', title: 'HD Voice & Video', desc: 'High-quality WebRTC calls for a personal touch.' },
        { icon: 'fa-user-friends', title: 'Nearby Peer', desc: 'Find and connect with users in your local network.' },
        { icon: 'fa-battery-three-quarters', title: 'Live Status', desc: 'Share battery level and online status in real-time.' },
        { icon: 'fa-palette', title: 'Personalized', desc: 'Custom wallpapers, dark mode, and vibrant themes.' }
    ];

    return `
        <div class="updates-page animate__animated animate__fadeIn">
             <div class="auth-nav">
                <div class="auth-nav-logo" onclick="window.location.hash=''">OMA</div>
                <div class="auth-nav-back" onclick="window.history.back()"><i class="fas fa-arrow-left" style="margin-right:8px;"></i>Back</div>
            </div>

            <div class="updates-content-scroll">
                <div class="hero-section">
                    <div class="sparkles-bg"></div>
                    <h1>What's New <i class="fas fa-sparkles" style="color:#fbbf24;"></i></h1>
                    <p>Discover the latest enhancements and features in OMA.</p>
                </div>

                <div class="updates-container">
                    <h3 class="section-title">Change Log</h3>
                    ${changelog.map(update => `
                        <div class="changelog-card">
                            <div class="changelog-header">
                                <span class="version-badge">${update.version}</span>
                                <span class="update-date">${update.date}</span>
                            </div>
                            <h4>${update.title}</h4>
                            <div class="update-tags">
                                ${update.tags.map(tag => `<span class="tag">${tag}</span>`).join('')}
                            </div>
                            <ul>
                                ${update.items.map(item => `<li><i class="fas fa-check-circle"></i> ${item}</li>`).join('')}
                            </ul>
                        </div>
                    `).join('')}

                    <h3 class="section-title" style="margin-top:40px;">Core Features</h3>
                    <div class="feature-grid">
                        ${features.map(f => `
                            <div class="feature-mini-card">
                                <div class="f-icon"><i class="fas ${f.icon}"></i></div>
                                <h5>${f.title}</h5>
                                <p>${f.desc}</p>
                            </div>
                        `).join('')}
                    </div>

                    <div style="text-align:center; padding: 40px 0; opacity: 0.5; font-size: 0.8rem;">
                        OMA Messenger &bull; Crafted with <i class="fas fa-heart" style="color:#ef4444;"></i>
                    </div>
                </div>
            </div>
        </div>
    `;
}

function renderAuthNavbar() {
    const hash = window.location.hash;
    const isSubState = hash.includes('/') || hash === '#setup';
    
    return `
        <div class="auth-nav">
            <div class="auth-nav-logo" onclick="window.handleLogoClick()">OMA</div>
            ${isSubState ? `<div class="auth-nav-back" onclick="window.history.back()"><i class="fas fa-arrow-left" style="margin-right:8px;"></i>Back</div>` : ''}
        </div>
    `;
}

// --- Phone Auth (SMS OTP) ---

function renderPhoneLoginContent() {
    return `
        ${renderAuthNavbar()}
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Login with Phone</h2>
                <p style="color: grey; font-size: 0.85rem; margin-bottom: 20px;">Enter your phone number with country code (e.g. +1...)</p>
                <form id="phone-login-form">
                    <input type="tel" id="phoneNumber" placeholder="+1..." required>
                    <button type="submit" id="btn-send-otp">Send OTP</button>
                    <a href="#login">Back to Login</a>
                    <div id="error-msg" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
}

window.switchPhoneLogin = () => {
    window.location.hash = '#login/phone';
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

// --- Email Magic Link Auth ---

function renderEmailLoginContent() {
    return `
        ${renderAuthNavbar()}
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Login with Email</h2>
                <p style="color: grey; font-size: 0.85rem; margin-bottom: 20px;">We'll send a magic link to your email.</p>
                <form id="email-login-form">
                    <input type="email" id="emailInput" placeholder="name@example.com" required>
                    <button type="submit" id="btn-send-email">Send Link</button>
                    <a href="#login">Back to Login</a>
                    <div id="error-msg" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
}

window.switchEmailLogin = () => {
    window.location.hash = '#login/email';
};

async function handleSendEmailLink(e) {
    e.preventDefault();
    await initFirebaseClient();
    const email = document.getElementById('emailInput').value;
    const btn = document.getElementById('btn-send-email');
    const errorMsg = document.getElementById('error-msg');

    btn.disabled = true;
    btn.innerText = "Sending...";

    const actionCodeSettings = {
        // Use clean origin URL to avoid hash-based parameter issues
        url: (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')
            ? (Capacitor.isNativePlatform() ? 'http://localhost:3000/' : window.location.origin + window.location.pathname)
            : 'https://oma-chat-app-pho0.onrender.com/',
        handleCodeInApp: true
    };

    try {
        await firebase.auth().sendSignInLinkToEmail(email, actionCodeSettings);
        window.localStorage.setItem('emailForSignIn', email);

        document.querySelector('.auth-box').innerHTML = `
            <h2>Check your Email</h2>
            <p style="margin: 20px 0;">We sent a login link to <b>${email}</b><br>Click it to finish logging in.</p>
            <p style="font-size:0.8rem; color:grey;">(You can close this tab)</p>
            <a href="#login">Back to Login</a>
        `;
    } catch (error) {
        console.error("Email Link Error:", error);
        errorMsg.innerText = error.message;
        btn.disabled = false;
        btn.innerText = "Send Link";
    }
}

async function handleEmailLinkSignIn(overrideUrl = null) {
    await initFirebaseClient();

    // Use the override URL (from Deep Link) or current window URL (PC/Web)
    const linkUrl = overrideUrl || window.location.href;

    let email = window.localStorage.getItem('emailForSignIn');
    if (!email) {
        email = window.prompt('Please provide your email for confirmation');
    }

    try {
        const result = await firebase.auth().signInWithEmailLink(email, linkUrl);
        window.localStorage.removeItem('emailForSignIn');

        const idToken = await result.user.getIdToken();
        const res = await api.verifyEmail(idToken);

        localStorage.setItem('oma_user', JSON.stringify(res));
        state.user = res;
        initSocket();

        // Remove query params to clean URL
        window.history.replaceState({}, document.title, window.location.pathname + window.location.hash);

        if (res.isNew) window.renderNameSetup();
        else { window.location.hash = '#chat'; render(); }

    } catch (error) {
        console.error("Email Sign-in Error:", error);
        window.showCustomAlert(error.message, 'error');
        render(); // Fallback to login
    }
}

// Global for Firebase confirmation
let confirmationResult = null;

async function initFirebaseClient() {
    if (window.firebase && firebase.apps.length > 0) return;

    // IMPORTANT: For Web/PWA, these keys are public and required.
    // In a production app, these should be securely managed or injected during build.
    // If you are testing locally, these can be found in your Firebase Console Project Settings (Web App).
    const config = {
        apiKey: "AIzaSyDFUVWEfVEDdaT0iDA7_6EqqU6X3377fIE",
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
    const phone = document.getElementById('phoneNumber').value;
    const errorMsg = document.getElementById('error-msg');
    const btn = document.getElementById('btn-send-otp');

    btn.disabled = true;
    btn.innerText = 'Sending...';

    // RATE LIMIT CHECK (Internal via api.js)
    try {
        await api.checkSmsLimit(phone);
    } catch (e) {
        btn.disabled = false;
        btn.innerText = 'Send OTP';
        errorMsg.innerText = e.message || "Daily SMS limit reached.";
        return;
    }

    try {
        await api.sendSmsOTP(phone);
        state.pendingPhone = phone; // Store for verification
        window.renderOTPVerify();
    } catch (error) {
        console.error("SMS Send Error:", error);
        errorMsg.innerText = error.message || "Failed to send OTP";
        btn.disabled = false;
        btn.innerText = 'Send OTP';
    }
}

async function handleVerifyOTP(e) {
    e.preventDefault();
    const code = document.getElementById('otpCode').value;
    const errorMsg = document.getElementById('error-msg');
    const btn = e.target.querySelector('button');

    // Use stored phone number
    const phone = state.pendingPhone;
    if (!phone) {
        errorMsg.innerText = "Session expired. Please request a new OTP.";
        return;
    }

    if (btn) {
        btn.disabled = true;
        btn.innerText = "Verifying...";
    }

    try {
        const res = await api.verifySmsOTP(phone, code);
        loginUser(res);
    } catch (error) {
        console.error("OTP Verify Error:", error);
        errorMsg.innerText = error.message || "Invalid OTP";
        if (btn) {
            btn.disabled = false;
            btn.innerText = "Verify & Login";
        }
    }
}

function renderNameSetupContent() {
    return `
        ${renderAuthNavbar()}
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
}

window.renderNameSetup = () => {
    window.location.hash = '#setup';
};

async function handleNameSetup(e) {
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
}

function renderRegister() {
    return `
        ${renderAuthNavbar()}
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Sign Up</h2>
                <form id="signup-form">
                    <input type="text" id="username" placeholder="Username" required>
                    <input type="text" id="name" placeholder="Full Name" required>
                    <input type="tel" id="signup-phone" placeholder="Phone Number (for recovery)" required>
                    <input type="password" id="password" placeholder="Password (min 8 chars)" required minlength="8">
                    <button type="submit">Sign Up</button>
                    <a href="#login" style="display:block; margin-top:15px;">Already have an account?</a>
                     <div id="error-msg" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;
}

async function handleLogin(e) {
    e.preventDefault();
    const errorEl = document.getElementById('error-msg');
    try {
        const u = document.getElementById('username').value;
        const p = document.getElementById('password').value;
        errorEl.innerText = "Connecting...";
        const res = await api.login(u, p);
        loginUser(res);
    } catch (err) {
        console.error("Login Error:", err);
        // Better error message for the <!DOCTYPE error (which happens if API_BASE is wrong)
        if (err.message && err.message.includes("Unexpected token '<'")) {
            errorEl.innerText = "Connection Error: Backend unreachable (Check API URL).";
        } else {
            errorEl.innerText = err.message;
        }
    }
}

window.handleGoogleLogin = async () => {
    const errorEl = document.getElementById('error-msg');
    errorEl.style.color = 'orange';
    errorEl.innerText = "Initializing Firebase...";

    try {
        await initFirebaseClient();
    } catch (e) {
        console.error("Firebase Init Failed:", e);
        errorEl.style.color = 'red';
        errorEl.innerText = "Firebase Error: Check Internet or Config.";
        return;
    }

    try {
        let idToken;
        errorEl.innerText = "Opening Google Sign-In...";

        if (window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform()) {
            // Native Google Auth (Using capacitor-firebase-auth)
            // Ensure no credentials available error is explained
            try {
                const result = await FirebaseAuthentication.signInWithGoogle();
                
                if (!result.credential || !result.credential.idToken) {
                    throw new Error("No Google ID token received. Ensure your SHA-1 is in Firebase Console.");
                }

                errorEl.innerText = "Exchanging Token...";
                const credential = firebase.auth.GoogleAuthProvider.credential(result.credential.idToken);
                const userCredential = await firebase.auth().signInWithCredential(credential);
                idToken = await userCredential.user.getIdToken();
            } catch (nativeErr) {
                if (nativeErr.code === 'no-credentials-available' || nativeErr.message.includes('credentials')) {
                    throw new Error("Google Error: No accounts found or SHA-1 mismatch in Firebase Console.");
                }
                throw nativeErr;
            }
        } else {
            // Web Google Auth
            const provider = new firebase.auth.GoogleAuthProvider();
            const result = await firebase.auth().signInWithPopup(provider);
            idToken = await result.user.getIdToken();
        }

        errorEl.innerText = "Verifying with OMA...";
        const res = await api.verifyGoogle(idToken);

        errorEl.style.color = 'green';
        errorEl.innerText = "Success! Redirecting...";

        if (res.isNew) {
            window.renderGoogleOnboarding(res.user, res.token);
        } else {
            loginUser(res);
        }

    } catch (e) {
        console.error("Google Login Error:", e);
        errorEl.style.color = 'red';
        errorEl.innerText = 'Login Failed: ' + (e.message || "Unknown Error");
    }
};

window.renderGoogleOnboarding = (partialUser, tempToken) => {
    const app = document.getElementById('app');
    // Save token temporarily so API calls work (completeProfile needs auth)
    // We construct a temporary user object since api.js methods use localStorage
    const tempState = { token: tempToken, user: partialUser };
    localStorage.setItem('oma_user', JSON.stringify(tempState));

    app.innerHTML = `
        <div class="centered-view">
            <div class="auth-box animate__animated animate__fadeIn">
                <h2>Almost Done!</h2>
                <p style="color: grey; font-size: 0.85rem; margin-bottom: 20px;">Please complete your profile to continue.</p>
                <form id="google-setup-form">
                    <label style="display:block; text-align:left; font-size:0.8rem; margin-bottom:5px;">Your Name (from Google)</label>
                    <input type="text" value="${partialUser.name}" disabled style="background:#f0f0f0; border:1px solid #ccc; color:#555;">
                    
                    <label style="display:block; text-align:left; font-size:0.8rem; margin-bottom:5px;">Choose Username *</label>
                    <input type="text" id="setup-username" placeholder="Unique Username" required>
                    
                    <label style="display:block; text-align:left; font-size:0.8rem; margin-bottom:5px;">Phone Number *</label>
                    <input type="tel" id="setup-phone" placeholder="Your Phone Number" required>

                    <button type="submit" id="btn-complete-setup">Complete Signup</button>
                    <div id="setup-error" class="error-msg"></div>
                </form>
            </div>
        </div>
    `;

    document.getElementById('google-setup-form').onsubmit = async (e) => {
        e.preventDefault();
        const username = document.getElementById('setup-username').value;
        const phone = document.getElementById('setup-phone').value;
        const btn = document.getElementById('btn-complete-setup');
        const err = document.getElementById('setup-error');

        btn.disabled = true;
        btn.innerText = "Saving...";
        err.innerText = "";

        try {
            const finalRes = await api.completeProfile(username, phone);
            // On success, finalRes contains the updated token and user
            loginUser(finalRes);
        } catch (error) {
            console.error("Setup Error:", error);
            err.innerText = error.message;
            btn.disabled = false;
            btn.innerText = "Complete Signup";
        }
    };
};

async function handleSignup(e) {
    e.preventDefault();
    try {
        const u = document.getElementById('username').value;
        const n = document.getElementById('name').value;
        const phone = document.getElementById('signup-phone').value;
        const p = document.getElementById('password').value;
        const res = await api.signup(u, p, n, phone);
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

    // Start Status Carousel if active chat
    if (state.activeChatId && state.activeChatId !== 'general') {
        const chat = state.chats.find(c => c.id === state.activeChatId) || state.searchResults.find(c => c.id === state.activeChatId);
        if (chat) {
            setTimeout(() => window.startStatusCarousel(chat), 100);
        }
    }
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
    if (chat.avatar && (chat.avatar.startsWith('http') || chat.avatar.startsWith('data:'))) {
        return chat.avatar;
    }
    const seed = chat.name || chat.username || 'User';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(seed)}&background=random`;
}

// window.handleImageError moved to index.html head for early failure protection

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
        const isEdit = state.isEditingProfile;
        const headerContent = isEdit
            ? `<div style="flex:1;"><button class="icon-btn" onclick="window.toggleProfileEdit(false)"><i class="fas fa-times"></i></button></div>
               <h3 style="flex:2; text-align:center; margin:0;">Edit Profile</h3>
               <div style="flex:1; text-align:right;"><button id="header-save-btn" style="background:transparent; border:none; color:var(--primary-color); font-weight:600; font-size:1rem; cursor:pointer;" onclick="window.saveProfile()">Save</button></div>`
            : `<h3>My Profile</h3>`;

        content = `
            <div class="sidebar-header" style="${isEdit ? 'justify-content:space-between;' : ''}">
                ${headerContent}
            </div>
            <div class="settings-list" style="flex: 1; overflow-y: auto; padding-bottom: 120px; min-height: 0;">
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
        // WhatsApp Style: Sort EVERYTHING by timestamp descending
        chatList = [...state.chats].sort((a, b) => {
            const timeA = a.timestamp || 0;
            const timeB = b.timestamp || 0;
            return timeB - timeA;
        });
    }

    return `
        <div class="sidebar-header">
            <div class="user-pill" onclick="window.switchTab('profile')">
                <img src="${getAvatarUrl(state.user?.user || {})}" class="avatar-small">
                 <span style="font-weight:600;">${state.user?.user.name}</span>
            </div>
            <div style="display:flex;gap:4px;align-items:center;">
                 <button class="icon-btn" onclick="window.location.hash='#updates'" title="What's New" style="color:var(--primary-color);"><i class="fas fa-sparkles"></i></button>
                 <button class="icon-btn" onclick="window.openGroupModal()" title="New Group"><i class="fas fa-plus-square"></i></button>
                 <button class="icon-btn" onclick="window.startNewChat()" title="Start New Chat" style="color:var(--primary-color); background:rgba(79, 70, 229, 0.1);"><i class="fas fa-comment-medical"></i></button>
                 <button class="icon-btn" onclick="window.openSettings('main')" title="Settings"><i class="fas fa-cog"></i></button>
            </div>
        </div>
        <div class="sidebar-search">
             <div id="poda">
                <div class="glow"></div>
                <div class="darkBorderBg"></div>
                <div class="white"></div>
                <div class="border"></div>
                <div id="main">
                    <input placeholder="Search chats..." type="text" class="input" id="user-search" oninput="window.handleSearch(this.value)" value="${state.lastSearchQuery || ''}">
                    <div id="input-mask"></div>
                    <div id="pink-mask"></div>
                    <div id="search-icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" height="18" width="18">
                            <circle cx="11" cy="11" r="8"></circle>
                            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                        </svg>
                    </div>
                    <div id="filter-icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" height="16" width="16">
                            <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"></polygon>
                        </svg>
                    </div>
                    <div class="filterBorder"></div>
                </div>
            </div>
        </div>
        
        <!-- Filter Bubbles -->
        <div class="filter-bubbles">
            <div class="filter-chip ${!state.chatFilter || state.chatFilter === 'global' ? 'active' : ''}" onclick="window.setChatFilter('global')">Global</div>
            <div class="filter-chip ${state.chatFilter === 'nearby' ? 'active' : ''}" onclick="window.setChatFilter('nearby')">Nearby</div>
        </div>

        <div class="chat-list" id="chat-list">
             <div class="pull-indicator" id="pull-indicator"><i class="fas fa-spinner"></i></div>
            ${renderChatListContent(chatList)}
        </div>
    `;
}

function renderChatListContent(chatList) {
    if (state.isSearching && chatList.length === 0) {
        return '<div style="padding:20px;text-align:center;color:grey;">No users found</div>';
    }

    return chatList.map(chat => {
        const unread = chat.unread || chat.unreadCount || 0;
        const isUnread = unread > 0;
        const clickAction = chat.isNearby ? `window.nearby.connect('${chat.id}')` : `window.openChat('${chat.id}')`;
        const avatarUrl = chat.isNearby ? chat.avatar : getAvatarUrl(chat);

        return `
            <div class="chat-item ${chat.id === state.activeChatId ? 'active' : ''}" onclick="${clickAction}">
                <div class="avatar-wrapper">
                    <img src="${avatarUrl}" onerror="window.handleImageError(this, '${(chat.name || chat.username || 'User').replace(/'/g, "\\'")}')">
                    ${state.onlineUsers.has(chat.id) ? '<div class="status-dot"></div>' : ''}
                </div>
                <div class="chat-info">
                    <div style="display:flex;justify-content:space-between;align-items:center;">
                        <h4 style="${isUnread ? 'font-weight: 800; color: var(--text-primary);' : ''}">${chat.name || chat.username}</h4>
                        <span style="font-size:0.75rem; color: ${isUnread ? 'var(--primary-color)' : 'var(--text-secondary)'};">
                           ${chat.time || ''}
                        </span>
                    </div>
                    <div style="display:flex;justify-content:space-between;align-items:center;">
                        <p style="${isUnread ? 'font-weight: 700; color: var(--text-primary);' : 'color: var(--text-secondary);'}">
                            ${chat.lastMsg || (chat.username ? '@' + chat.username : '')}
                        </p>
                        ${isUnread ? `<div style="background:var(--primary-color);color:white;border-radius:50%;padding:2px 6px;font-size:0.7rem;">${unread}</div>` : ''}
                    </div>
                </div>
            </div>
        `;
    }).join('');
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
                         <img src="${getAvatarUrl(u)}" onerror="window.handleImageError(this, '${(u.name || u.username || 'User').replace(/'/g, "\\'")}')">
                            <div class="msg-image-container" onclick="window.openMediaViewer('${u.avatar}', 'image')">
                                <img src="${u.avatar}" alt="Image" style="cursor:pointer;">
                            </div>
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

// Profile Edit State Management
window.toggleProfileEdit = (isEdit) => {
    console.log("toggleProfileEdit Called:", isEdit);
    state.isEditingProfile = isEdit;
    render();
};

window.refreshSidebar = () => {
    const sidebar = document.getElementById('sidebar');
    if (sidebar) {
        if (state.settingsView) {
            sidebar.innerHTML = renderSettings();
        } else {
            sidebar.innerHTML = renderSidebarMain();
        }
    }
};

// Reuse the Profile Content logic from Settings
window.saveProfile = async (btn) => {
    // If called from header, btn might be null or icon, handle UI feedback elsewhere or generic
    const saveBtn = document.getElementById('header-save-btn');
    if (saveBtn) { saveBtn.disabled = true; saveBtn.innerText = "Saving..."; }

    try {
        const name = document.getElementById('settings-name').value;
        const bio = document.getElementById('settings-bio').value;

        const updatedUser = await api.updateProfile({ name, bio });

        // Update Local State
        state.user.user = { ...state.user.user, ...updatedUser };
        localStorage.setItem('oma_user', JSON.stringify(state.user));

        window.showCustomAlert('Profile Saved!', 'success');

        // Reset Edit State
        state.isEditingProfile = false;

        // Navigation Logic
        if (state.settingsView === 'profile') {
            window.openSettings('main');
        } else {
            render();
        }

    } catch (e) {
        console.error("Save Profile Failed:", e);
        window.showCustomAlert('Failed: ' + e.message);
        if (saveBtn) { saveBtn.disabled = false; saveBtn.innerText = "Save"; }
    }
};

window.setupProfileSync = () => {
    // Wait for socket to be initialized
    if (typeof socket === 'undefined' || !socket) {
        setTimeout(window.setupProfileSync, 1000);
        return;
    }

    socket.on('profile_update', (data) => {
        console.log("Received Profile Update:", data);

        // 1. Update Chats List (Sidebar)
        const chat = state.chats.find(c => c.id === data.userId);
        if (chat) {
            if (data.name) chat.name = data.name;
            if (data.avatar) chat.avatar = data.avatar;
            if (data.username) chat.username = data.username;

            localStorage.setItem('oma_chats', JSON.stringify(state.chats));
            window.refreshSidebar();
        }

        // 2. Update Active Chat Header
        if (state.activeChatId === data.userId) {
            const headerName = document.getElementById('header-name');
            const headerAvatar = document.getElementById('header-avatar');
            if (headerName && data.name) headerName.innerText = data.name;
            if (headerAvatar && data.avatar) headerAvatar.src = data.avatar;
        }
    });
};

function renderProfileContent() {
    const u = state.user?.user || {};
    const isEdit = state.isEditingProfile;

    return `
        <div class="profile-card animate__animated animate__fadeIn">
            <div class="profile-card__img">
                <svg xmlns="http://www.w3.org/2000/svg" width="100%" height="100%">
                    <rect fill="#ffffff" width="540" height="450"></rect>
                    <defs>
                        <linearGradient id="a" gradientUnits="userSpaceOnUse" x1="0" x2="0" y1="0" y2="100%" gradientTransform="rotate(222,648,379)">
                            <stop offset="0" stop-color="#ffffff"></stop>
                            <stop offset="1" stop-color="#FC726E"></stop>
                        </linearGradient>
                        <pattern patternUnits="userSpaceOnUse" id="b" width="300" height="250" x="0" y="0" viewBox="0 0 1080 900">
                             <g fill-opacity="0.5">
                                <polygon fill="#444" points="90 150 0 300 180 300"></polygon>
                                <polygon points="90 150 180 0 0 0"></polygon>
                                <polygon fill="#AAA" points="270 150 360 0 180 0"></polygon>
                                <polygon fill="#DDD" points="450 150 360 300 540 300"></polygon>
                                <polygon fill="#999" points="450 150 540 0 360 0"></polygon>
                                <polygon points="630 150 540 300 720 300"></polygon>
                                <polygon fill="#DDD" points="630 150 720 0 540 0"></polygon>
                                <polygon fill="#444" points="810 150 720 300 900 300"></polygon>
                                <polygon fill="#FFF" points="810 150 900 0 720 0"></polygon>
                                <polygon fill="#DDD" points="990 150 900 300 1080 300"></polygon>
                                <polygon fill="#444" points="990 150 1080 0 900 0"></polygon>
                                <polygon fill="#DDD" points="90 450 0 600 180 600"></polygon>
                                <polygon points="90 450 180 300 0 300"></polygon>
                                <polygon fill="#666" points="270 450 180 600 360 600"></polygon>
                                <polygon fill="#AAA" points="270 450 360 300 180 300"></polygon>
                                <polygon fill="#DDD" points="450 450 360 600 540 600"></polygon>
                                <polygon fill="#999" points="450 450 540 300 360 300"></polygon>
                                <polygon fill="#999" points="630 450 540 600 720 600"></polygon>
                                <polygon fill="#FFF" points="630 450 720 300 540 300"></polygon>
                                <polygon points="810 450 720 600 900 600"></polygon>
                                <polygon fill="#DDD" points="810 450 900 300 720 300"></polygon>
                                <polygon fill="#AAA" points="990 450 900 600 1080 600"></polygon>
                                <polygon fill="#444" points="990 450 1080 300 900 300"></polygon>
                                <polygon fill="#222" points="90 750 0 900 180 900"></polygon>
                                <polygon points="270 750 180 900 360 900"></polygon>
                                <polygon fill="#DDD" points="270 750 360 600 180 600"></polygon>
                                <polygon points="450 750 540 600 360 600"></polygon>
                                <polygon points="630 750 540 900 720 900"></polygon>
                                <polygon fill="#444" points="630 750 720 600 540 600"></polygon>
                                <polygon fill="#AAA" points="810 750 720 900 900 900"></polygon>
                                <polygon fill="#666" points="810 750 900 600 720 600"></polygon>
                                <polygon fill="#999" points="990 750 900 900 1080 900"></polygon>
                                <polygon fill="#999" points="180 0 90 150 270 150"></polygon>
                                <polygon fill="#444" points="360 0 270 150 450 150"></polygon>
                                <polygon fill="#FFF" points="540 0 450 150 630 150"></polygon>
                                <polygon points="900 0 810 150 990 150"></polygon>
                                <polygon fill="#222" points="0 300 -90 450 90 450"></polygon>
                                <polygon fill="#FFF" points="0 300 90 150 -90 150"></polygon>
                                <polygon fill="#FFF" points="180 300 90 450 270 450"></polygon>
                                <polygon fill="#666" points="180 300 270 150 90 150"></polygon>
                                <polygon fill="#222" points="360 300 270 450 450 450"></polygon>
                                <polygon fill="#FFF" points="360 300 450 150 270 150"></polygon>
                                <polygon fill="#444" points="540 300 450 450 630 450"></polygon>
                                <polygon fill="#222" points="540 300 630 150 450 150"></polygon>
                                <polygon fill="#AAA" points="720 300 630 450 810 450"></polygon>
                                <polygon fill="#666" points="720 300 810 150 630 150"></polygon>
                                <polygon fill="#FFF" points="900 300 810 450 990 450"></polygon>
                                <polygon fill="#999" points="900 300 990 150 810 150"></polygon>
                                <polygon points="0 600 -90 750 90 750"></polygon>
                                <polygon fill="#666" points="0 600 90 450 -90 450"></polygon>
                                <polygon fill="#AAA" points="180 600 90 750 270 750"></polygon>
                                <polygon fill="#444" points="180 600 270 450 90 450"></polygon>
                                <polygon fill="#444" points="360 600 270 750 450 750"></polygon>
                                <polygon fill="#999" points="360 600 450 450 270 450"></polygon>
                                <polygon fill="#666" points="540 600 630 450 450 450"></polygon>
                                <polygon fill="#222" points="720 600 630 750 810 750"></polygon>
                                <polygon fill="#FFF" points="900 600 810 750 990 750"></polygon>
                                <polygon fill="#222" points="900 600 990 450 810 450"></polygon>
                                <polygon fill="#DDD" points="0 900 90 750 -90 750"></polygon>
                                <polygon fill="#444" points="180 900 270 750 90 750"></polygon>
                                <polygon fill="#FFF" points="360 900 450 750 270 750"></polygon>
                                <polygon fill="#AAA" points="540 900 630 750 450 750"></polygon>
                                <polygon fill="#FFF" points="720 900 810 750 630 750"></polygon>
                                <polygon fill="#222" points="900 900 990 750 810 750"></polygon>
                                <polygon fill="#222" points="1080 300 990 450 1170 450"></polygon>
                                <polygon fill="#FFF" points="1080 300 1170 150 990 150"></polygon>
                                <polygon points="1080 600 990 750 1170 750"></polygon>
                                <polygon fill="#666" points="1080 600 1170 450 990 450"></polygon>
                                <polygon fill="#DDD" points="1080 900 1170 750 990 750"></polygon>
                            </g>
                        </pattern>
                    </defs>
                    <rect x="0" y="0" fill="url(#a)" width="100%" height="100%"></rect>
                    <rect x="0" y="0" fill="url(#b)" width="100%" height="100%"></rect>
                </svg>
            </div>
            <div class="profile-card__avatar">
                <img src="${getAvatarUrl(u)}" alt="Avatar">
            </div>
            
            <div class="profile-card__title">${u.name || 'Anonymous'}</div>
            <div class="profile-card__subtitle">@${u.username || 'user'}</div>
            
            ${!isEdit ? `
                <div class="profile-info-container">
                    <div class="profile-info-item">
                        <i class="fas fa-info-circle"></i>
                        <span>${u.bio || 'Available'}</span>
                    </div>
                    <div class="profile-info-item">
                        <i class="fas fa-phone"></i>
                        <span>${u.phone || 'No phone linked'}</span>
                    </div>
                    <div style="text-align:center; padding-top: 10px;">
                        <div class="app-version-badge">v2.8.5</div>
                    </div>
                </div>
                <div class="profile-card__wrapper">
                     <button class="profile-card__btn profile-card__btn-solid" onclick="window.toggleProfileEdit(true)">Edit Profile</button>
                    <button class="profile-card__btn" onclick="window.logout()">Logout</button>
                </div>
            ` : `
                <div id="profile-edit-section" style="padding: 0 20px 150px 20px; width: 100%; margin-top:10px;">
                    <div class="input-group" style="text-align:left;">
                        <label>Name</label>
                        <input type="text" id="settings-name" value="${u.name || ''}" style="background:#0f172a; color:white; border:1px solid #334155; padding:8px; border-radius:8px; width:100%;">
                    </div>
                    <div class="input-group" style="text-align:left;">
                        <label>Bio</label>
                        <input type="text" id="settings-bio" value="${u.bio || ''}" placeholder="Add a bio" style="background:#0f172a; color:white; border:1px solid #334155; padding:8px; border-radius:8px; width:100%;">
                    </div>
                    <div style="text-align:center; margin-top:10px; color:#aaa; font-size:0.8rem;">
                        Save button is in the top right corner.
                    </div>
                </div>
            `}
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
        // Admin Dashboard is now a full-page overlay, not rendered in sidebar
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

                <div class="settings-item" onclick="window.showDiagnostics(); window.closeSettings()">
                    <div class="settings-icon-container" style="background:linear-gradient(135deg, #6b7280, #4b5563);"><i class="fas fa-bug" style="color:white;"></i></div>
                    <div class="settings-text">
                        <h4>Diagnostics & Debug</h4>
                    </div>
                    <i class="fas fa-chevron-right settings-arrow"></i>
                </div>

                <div class="settings-item" onclick="window.location.hash='#updates'">
                    <div class="settings-icon-container" style="background:linear-gradient(135deg, #a855f7, #7e22ce);"><i class="fas fa-sparkles" style="color:white;"></i></div>
                    <div class="settings-text">
                        <h4>What's New</h4>
                        <p>Features & Change Log</p>
                    </div>
                </div>

                ${state.user?.user?.isAdmin ? `
                    <div class="settings-item" onclick="window.openSettings('admin')">
                        <div class="settings-icon-container" style="background:linear-gradient(135deg, #f43f5e, #e11d48);"><i class="fas fa-user-shield" style="color:white;"></i></div>
                        <div class="settings-text">
                            <h4>Admin Panel</h4>
                            <p>Platform Management</p>
                        </div>
                        <i class="fas fa-chevron-right settings-arrow"></i>
                    </div>
                ` : ''}

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

let _adminStats = null;
let _adminUsers = [];
let _adminLoading = false;
let _adminSearch = "";

window.fetchAdminData = async () => {
    if (_adminLoading) return;
    _adminLoading = true;
    try {
        [_adminStats, _adminUsers] = await Promise.all([
            api.getAdminStats(),
            api.getAdminUsers()
        ]);
        renderAdminDashboard(); // Refresh full-page content
    } catch (e) {
        console.error("Admin data fetch failed", e);
    } finally {
        _adminLoading = false;
        const syncBtn = document.querySelector('.admin-sync-btn i');
        if (syncBtn) syncBtn.classList.remove('fa-spin');
    }
}

window.renderAdminDashboard = () => {
    const container = document.getElementById('admin-dashboard-container');
    if (!container) return;

    if (!_adminStats && !_adminLoading) {
        fetchAdminData();
        container.innerHTML = `<div class="p-8 text-center"><i class="fas fa-spinner fa-spin fa-2x"></i><br>Loading Dashboard...</div>`;
        return;
    }

    const stats = _adminStats || { totalUsers: 0, onlineUsers: 0, totalMessages: 0, totalCalls: 0 };
    const users = (_adminUsers || []).filter(u => 
        u.name?.toLowerCase().includes(_adminSearch) || 
        u.username?.toLowerCase().includes(_adminSearch)
    );

    container.innerHTML = `
        <div class="admin-dashboard-full animate__animated animate__fadeIn">
            <header class="admin-header">
                <div class="admin-header-left">
                    <button class="icon-btn admin-close-btn" onclick="window.closeAdminDashboard()"><i class="fas fa-times"></i></button>
                    <div class="admin-title">
                        <h1>Admin Dashboard</h1>
                        <p>Manage OMA platform and users</p>
                    </div>
                </div>
                <div class="admin-header-right">
                    <div class="admin-search-container">
                        <i class="fas fa-search"></i>
                        <input type="text" id="admin-user-search" placeholder="Search users..." value="${_adminSearch}" oninput="window.handleAdminSearch(this.value)">
                    </div>
                    <button class="icon-btn admin-sync-btn" onclick="fetchAdminData()"><i class="fas fa-sync-alt"></i></button>
                </div>
            </header>

            <div class="admin-scroll-content">
                <div class="admin-stats-row">
                    <div class="admin-stat-card glass">
                        <div class="stat-icon users"><i class="fas fa-users-medical"></i></div>
                        <div class="stat-content">
                            <span class="stat-value">${stats.totalUsers}</span>
                            <span class="stat-label">Total Registered</span>
                        </div>
                    </div>
                    <div class="admin-stat-card glass online">
                        <div class="stat-icon online"><i class="fas fa-satellite-dish"></i></div>
                        <div class="stat-content">
                            <span class="stat-value pulse">${stats.onlineUsers}</span>
                            <span class="stat-label">Online Now</span>
                        </div>
                    </div>
                    <div class="admin-stat-card glass messages">
                        <div class="stat-icon msgs"><i class="fas fa-paper-plane"></i></div>
                        <div class="stat-content">
                            <span class="stat-value">${stats.totalMessages}</span>
                            <span class="stat-label">Messages Sent</span>
                        </div>
                    </div>
                    <div class="admin-stat-card glass calls">
                        <div class="stat-icon call"><i class="fas fa-phone-laptop"></i></div>
                        <div class="stat-content">
                            <span class="stat-value">${stats.totalCalls || 0}</span>
                            <span class="stat-label">Total Calls</span>
                        </div>
                    </div>
                </div>

                <div class="admin-main-grid">
                    <div class="admin-users-section glass">
                        <div class="section-header">
                            <h2>User Management</h2>
                            <span class="user-count">${users.length} users found</span>
                        </div>
                        <div class="admin-user-grid">
                            ${users.map(u => `
                                <div class="admin-user-tile">
                                    <div class="user-main">
                                        <img src="${getAvatarUrl(u)}" class="user-avatar-large">
                                        <div class="user-details">
                                            <h3>${u.name}</h3>
                                            <p>@${u.username}</p>
                                            <span class="badge ${u.isAdmin ? 'admin' : 'user'}">${u.isAdmin ? 'Administrator' : 'User'}</span>
                                        </div>
                                    </div>
                                    <div class="user-footer">
                                        <div class="user-meta">
                                            <span><i class="fas fa-clock"></i> ${u.lastSeen ? new Date(u.lastSeen).toLocaleDateString() : 'Never'}</span>
                                        </div>
                                        <div class="user-actions">
                                            <button class="admin-btn delete" onclick="window.deleteUserAdmin('${u.id}')">
                                                <i class="fas fa-user-minus"></i>
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            `).join('')}
                            ${users.length === 0 ? '<div class="no-results">No users match your search.</div>' : ''}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;
}

window.handleAdminSearch = (val) => {
    _adminSearch = val.toLowerCase();
    renderAdminDashboard();
};

window.deleteUserAdmin = async (userId) => {
    if (!confirm('Are you ABSOLUTELY sure? This will permanently delete the user and all their chat history.')) return;
    try {
        await api.adminAction('delete', userId);
        showCustomAlert('User deleted successfully', 'success');
        fetchAdminData();
    } catch (e) {
        showCustomAlert(e.message || 'Failed to delete user', 'error');
    }
};

function renderSettingsProfile() {
    return `
        <div class="sidebar-header">
             <button class="icon-btn" onclick="window.openSettings('main')"><i class="fas fa-arrow-left"></i></button>
             <h3>Edit Profile</h3>
        </div>
        <div class="settings-content settings-slide-in">
             <div class="profile-section">
                <div style="position:relative;cursor:pointer;" onclick="window.openMediaViewer('${getAvatarUrl(state.user?.user || {})}', 'image')">
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
                
                <div class="settings-section-header">Visibility</div>
                <div class="settings-item">
                    <div class="settings-text" style="flex-direction:row; justify-content:space-between; display:flex; align-items:center; width:100%;">
                        <div>
                            <h4>Last Seen</h4>
                            <p style="font-size:0.8rem; opacity:0.7;">Who can see your last seen status</p>
                        </div>
                        <select id="privacy-lastseen" onchange="window.savePrivacy()" class="privacy-select">
                            <option value="everyone" ${s.lastSeenPrivacy === 'everyone' ? 'selected' : ''}>Everyone</option>
                            <option value="contacts" ${s.lastSeenPrivacy === 'contacts' ? 'selected' : ''}>My Contacts</option>
                            <option value="nobody" ${s.lastSeenPrivacy === 'nobody' ? 'selected' : ''}>Nobody</option>
                        </select>
                    </div>
                </div>

                <div class="settings-item">
                    <div class="settings-text" style="flex-direction:row; justify-content:space-between; display:flex; align-items:center; width:100%;">
                        <div>
                            <h4>Profile Photo</h4>
                            <p style="font-size:0.8rem; opacity:0.7;">Who can see your profile picture</p>
                        </div>
                        <select id="privacy-profilephoto" onchange="window.savePrivacy()" class="privacy-select">
                            <option value="everyone" ${s.profilePhotoPrivacy === 'everyone' ? 'selected' : ''}>Everyone</option>
                            <option value="contacts" ${s.profilePhotoPrivacy === 'contacts' ? 'selected' : ''}>My Contacts</option>
                            <option value="nobody" ${s.profilePhotoPrivacy === 'nobody' ? 'selected' : ''}>Nobody</option>
                        </select>
                    </div>
                </div>

                <div class="settings-item">
                    <div class="settings-text" style="flex-direction:row; justify-content:space-between; display:flex; align-items:center; width:100%;">
                        <div>
                            <h4>About / Bio</h4>
                            <p style="font-size:0.8rem; opacity:0.7;">Who can see your bio</p>
                        </div>
                        <select id="privacy-about" onchange="window.savePrivacy()" class="privacy-select">
                            <option value="everyone" ${s.aboutPrivacy === 'everyone' ? 'selected' : ''}>Everyone</option>
                            <option value="contacts" ${s.aboutPrivacy === 'contacts' ? 'selected' : ''}>My Contacts</option>
                            <option value="nobody" ${s.aboutPrivacy === 'nobody' ? 'selected' : ''}>Nobody</option>
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
                        <input type="checkbox" id="privacy-readreceipts" ${s.readReceipts !== false ? 'checked' : ''} onchange="window.savePrivacy()">
                        <span class="slider"></span>
                    </label>
                </div>

                <div class="settings-section-header">Live Status</div>
                <div class="settings-item">
                    <div class="settings-text">
                        <h4>Share Battery Status</h4>
                        <p>Allow others to see your battery level.</p>
                    </div>
                    <label class="switch">
                        <input type="checkbox" ${localStorage.getItem('oma_share_battery') !== 'false' ? 'checked' : ''} onchange="localStorage.setItem('oma_share_battery', this.checked); window.checkAndSendBattery(true);">
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
    const currentWallpaper = localStorage.getItem('oma_wallpaper') || 'default';

    const wallpapers = [
        { id: 'default', label: 'Default' },
        { id: 'bubble', label: 'Bubble' },
        { id: 'choco', label: 'Choco' },
        { id: 'chocolite', label: 'Choco Lite' },
        { id: 'crossbox', label: 'Cross Box' },
        { id: 'dots', label: 'Dots' },
        { id: 'eyes', label: 'Eyes' },
        { id: 'japan', label: 'Japan Grid' },
        { id: 'japan-matrix', label: 'Japan Matrix' },
        { id: 'matrix', label: 'Matrix' },
        { id: 'strips', label: 'Strips' }
    ];

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
                
                <div class="settings-section-header">Chat Wallpaper</div>
                <div class="wallpaper-grid">
                    ${wallpapers.map(w => `
                        <div onclick="window.setWallpaper('${w.id}')" class="wallpaper-btn" style="
                            padding: 8px 4px; 
                            border-radius: 10px; 
                            text-align: center; 
                            cursor: pointer;
                            border: 1px solid ${currentWallpaper === w.id ? 'var(--primary-color)' : 'var(--border-color)'};
                            background: var(--sidebar-bg);
                            color: var(--text-primary);
                            font-size: 0.75rem;
                            font-weight: 600;
                            transition: all 0.2s;
                        ">
                            ${w.label}
                        </div>
                    `).join('')}
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

    // Check Nearby Peers
    if (!activeChat && window.nearby && window.nearby.peers.has(state.activeChatId)) {
        const peer = window.nearby.peers.get(state.activeChatId);
        activeChat = {
            id: peer.id,
            name: peer.name,
            avatar: 'https://ui-avatars.com/api/?name=' + encodeURIComponent(peer.name) + '&background=random',
            isNearby: true,
            status: 'Nearby'
        };
    }

    if (!activeChat) {
        // Fallback since we might have opened via URL
        activeChat = { name: 'Chat', avatar: 'https://ui-avatars.com/api/?name=?' };
    }

    return `
        <div class="chat-header">
            <div class="chat-header-user" onclick="window.openUserProfile('${activeChat.id}')" style="cursor:pointer;">
                <button class="back-btn" onclick="event.stopPropagation(); window.closeChat()"><i class="fas fa-arrow-left"></i></button>
                    <img src="${getAvatarUrl(activeChat)}" id="header-avatar" onerror="window.handleImageError(this, '${(activeChat.name || activeChat.username || 'User').replace(/'/g, "\\'")}')">
                    <div class="chat-header-info">
                    <h4 id="header-name">${activeChat.name || activeChat.username}</h4>
                <p id="header-status">
                    ${getHeaderStatusText(activeChat)}
                </p>
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
                <button class="icon-btn desktop-only" onclick="window.toggleChatSearch()" title="Search"><i class="fas fa-search"></i></button>
                <button class="icon-btn" onclick="window.startCall('audio')"><i class="fas fa-phone"></i></button>
                <button class="icon-btn" onclick="window.startCall('video')"><i class="fas fa-video"></i></button>
                
                <div class="chat-menu" style="position:relative;">
                    <button class="icon-btn" onclick="window.toggleChatMenu()" id="btn-chat-menu"><i class="fas fa-ellipsis-v"></i></button>
                    <div id="chat-menu-dropdown" class="hidden" style="position:absolute; top:40px; right:0; background:var(--sidebar-bg); border:1px solid var(--border-color); border-radius:12px; min-width:180px; box-shadow:0 10px 30px rgba(0,0,0,0.5); z-index:100; overflow:hidden;">
                        <div class="menu-item mobile-only" onclick="window.toggleChatSearch(); window.toggleChatMenu();" style="padding:12px 16px; cursor:pointer; color:var(--text-primary); display:flex; gap:10px; align-items:center; transition:background 0.2s;">
                            <i class="fas fa-search" style="width:20px;"></i> Search
                        </div>
                        ${activeChat.type === 'group' ? `
                             <div class="menu-item" onclick="window.openGroupInfo()" style="padding:12px 16px; cursor:pointer; color:var(--text-primary); display:flex; gap:10px; align-items:center; transition:background 0.2s;">
                                <i class="fas fa-users" style="width:20px;"></i> Group Info
                            </div>
                        ` : ''}
                        
                         <div class="menu-item" onclick="window.toggleChatMenu(); window.openSettings('appearance')" style="padding:12px 16px; cursor:pointer; color:var(--text-primary); display:flex; gap:10px; align-items:center; transition:background 0.2s;">
                            <i class="fas fa-image" style="width:20px;"></i> Wallpaper
                        </div>

                        ${activeChat.id !== 'general' ? `
                             <div class="menu-item" onclick="window.deleteCurrentChat()" style="padding:12px 16px; cursor:pointer; color:#ef4444; display:flex; gap:10px; align-items:center; transition:background 0.2s;">
                                <i class="fas fa-trash" style="width:20px;"></i> Delete Chat
                            </div>
                            <div class="menu-item" onclick="window.blockCurrentUser('${activeChat.id}')" style="padding:12px 16px; cursor:pointer; color:#ef4444; display:flex; gap:10px; align-items:center; transition:background 0.2s;">
                                <i class="fas fa-ban" style="width:20px;"></i> Block
                            </div>
                            <div class="menu-item" onclick="window.reportCurrentUser('${activeChat.id}')" style="padding:12px 16px; cursor:pointer; color:#f59e0b; display:flex; gap:10px; align-items:center; transition:background 0.2s;">
                                <i class="fas fa-flag" style="width:20px;"></i> Report
                            </div>
                        ` : ''}
                    </div>
                </div>
            </div>
        </div>
        
        <div id="messages-container" class="messages-container"></div>
        
        <emoji-picker class="hidden" id="emoji-picker"></emoji-picker>
        
        <!-- Attachment Menu (Siblings approach) -->
        <div id="attachment-menu" class="attachment-menu hidden">
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
             <!-- Input Area -->
            <input type="file" id="input-media" style="display:none;" accept="image/*,video/*" onchange="window.handleMedia(this)">
            <input type="file" id="input-file" style="display:none;" accept="*" onchange="window.handleMedia(this)">
            
            <button class="icon-btn" onclick="window.toggleAttachmentMenu(event)"><i class="fas fa-paperclip"></i></button>
            <button class="icon-btn" onclick="window.toggleEmojiPicker(event)"><i class="far fa-smile"></i></button>
            <button class="icon-btn" onclick="window.toggleGifPicker(event)" title="GIFs"><span style="font-weight:800;font-size:0.75rem;letter-spacing:-0.5px;">GIF</span></button>
            <form id="msg-form">
                <input type="text" id="msg-input" placeholder="Message..." autocomplete="off" oninput="window.updateSendBtn()">
                <button type="submit" id="send-or-mic-btn" style="background:var(--primary-gradient);color:#fff;border:none;width:40px;height:40px;border-radius:50%;display:flex;align-items:center;justify-content:center;cursor:pointer;font-size:1.1rem;transition:all 0.25s ease;box-shadow:0 2px 8px rgba(79,70,229,0.3);flex-shrink:0;"><i class="fas fa-microphone"></i></button>
            </form>
        </div>
        <!-- Voice Recording Indicator -->
        <div id="voice-recording-bar" class="voice-recording-bar hidden">
            <div class="recording-pulse"></div>
            <span id="recording-timer">0:00</span>
            <div style="flex:1;"></div>
            <button class="icon-btn" id="cancel-recording-btn" onclick="window.cancelVoiceRecording()" title="Cancel"><i class="fas fa-trash" style="color:#ef4444;"></i></button>
            <button class="icon-btn" id="send-recording-btn" onclick="window.stopVoiceRecording()" title="Send"><i class="fas fa-paper-plane" style="color:var(--primary-color);"></i></button>
        </div>
        <!-- GIF Picker Panel -->
        <div id="gif-picker" class="gif-picker hidden">
            <div class="gif-search-bar">
                <input type="text" id="gif-search-input" placeholder="Search GIFs..." oninput="window.searchGifs(this.value)">
            </div>
            <div id="gif-results" class="gif-results">
                <div class="gif-loading">Search for GIFs or browse trending</div>
            </div>
        </div>
    `;
}

let pollingInterval = null;
let lastTimestamp = 0;

async function setupChatLogic() {
    if (pollingInterval) clearInterval(pollingInterval);
    if (state.settingsView) return;

    // Start Polling (with Adaptive Interval)
    const container = document.getElementById('messages-container');
    const form = document.getElementById('msg-form');
    window.adjustPolling(container);

    const inputEl = document.getElementById('msg-input');
    if (inputEl) {
        inputEl.addEventListener('input', () => {
            // Emit Typing
            if (state.activeChatId !== 'general') {
                socket.emit('typing', { receiverId: state.activeChatId, senderId: state.user.user.id });

                // Debounce Stop
                clearTimeout(window.typingTimeout);
                window.typingTimeout = setTimeout(() => {
                    socket.emit('stop_typing', { receiverId: state.activeChatId, senderId: state.user.user.id });
                }, 3000);
            }
        });
    }

    if (!form || !container) return; // If no chat UI, stop here (don't bind form)
    
    // 1. INSTANT RENDER FROM CACHE
    const cached = loadChatFromCache(state.activeChatId);
    if (cached && cached.length > 0) {
        state.messages = cached;
        container.innerHTML = '';
        state.messages.forEach(msg => appendMessage(msg, container));
        scrollToBottom(container);
        window.logToDebug("Chat: Loaded from cache (" + cached.length + ")");
    }

    // 2. Initial Fetch (already happens in setupChatLogic usually, but optimized here if needed)
    // Actually, pollMessages will run immediately and sync the rest.

    scrollToBottom(container);

    let isSending = false; // Prevents double sending

    form.onsubmit = async (e) => {
        e.preventDefault();
        if (isSending) return; // Already sending, ignore

        const input = document.getElementById('msg-input');
        const content = input.value.trim();

        // Stop Typing immediately upon send
        if (state.activeChatId !== 'general') {
            socket.emit('stop_typing', { receiverId: state.activeChatId, senderId: state.user.user.id });
        }

        // If input is empty, start voice recording instead
        if (!content) {
            window.startVoiceRecording();
            return;
        }

        isSending = true; // Set flag
        input.value = ''; // Clear immediately
        window.updateSendBtn(); // Reset to mic icon

        // Nearby Chat Interception
        if (window.nearby && window.nearby.peers.has(state.activeChatId)) {
            try {
                await window.nearby.sendMessage(content);
            } catch (err) {
                console.error("Nearby Send Error", err);
            }
            isSending = false;
            return;
        }

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

        // Add data-temp-id for reliable socket matching
        const tempEl = document.getElementById(`msg-${tempId}`);
        if (tempEl) tempEl.dataset.tempId = tempId;

        scrollToBottom(container);

        // Disable button to prevent double-send
        const btn = form.querySelector('button[type="submit"]');
        if (btn) btn.disabled = true;

        try {
            const replyToId = state.replyingTo ? state.replyingTo.id : null;
            const realMsg = await api.sendMessage(content, 'text', state.activeChatId, replyToId, tempId);

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
            // Update Cache
            saveChatToCache(state.activeChatId, state.messages);
        } catch (e) {
            console.error("Send failed", e);
            // Optionally remove temp message or show error
        } finally {
            isSending = false; // Reset flag
            if (btn) btn.disabled = false;
            // Re-focus input
            input.focus();
        }
    };

    // 1. CLEAR ONLY IF EMPTY (Reset state for the new chat, unless we have cache)
    lastTimestamp = 0;
    let lastRenderedDate = '';

    // If we have messages already (from Cache-First at line 2417), we DON'T clear the container.
    // If we DON'T have messages, we show the Fetching spinner.
    if (state.messages.length === 0) {
        container.innerHTML = `
            <div class="chat-loading-container animate__animated animate__fadeIn">
                <i class="fas fa-spinner fa-spin"></i>
                <p>Fetching messages...</p>
            </div>`;
    }

    // Initial Load: Specific Chat History
    try {
        const initialMessages = await api.getHistory(0, state.activeChatId);
        const shouldAnimate = !state.animatedChats.has(state.activeChatId);

        // ALWAYS clear the spinner if we got a response or it's been handled
        if (state.messages.length === 0) container.innerHTML = '';

        if (Array.isArray(initialMessages)) {
            initialMessages.forEach(msg => {
                // Ensure we don't duplicate if poller beat us
                if (state.messages.find(m => m.id === msg.id)) return;

                state.messages.push(msg);

                const msgDate = new Date(msg.timestamp).toDateString();
                const header = msgDate !== lastRenderedDate ? getChatDateHeader(msg.timestamp) : null;
                if (header) lastRenderedDate = msgDate; // Update tracker

                appendMessage(msg, container, header, shouldAnimate);

                if (msg.timestamp > lastTimestamp) lastTimestamp = msg.timestamp;
            });
            // Update Cache
            saveChatToCache(state.activeChatId, state.messages);
        }

        if (initialMessages.length > 0) {
            state.animatedChats.add(state.activeChatId);
        }

        scrollToBottom(container);
    } catch (e) {
        console.error("Initial load failed", e);
        if (state.messages.length === 0) {
            container.innerHTML = `<div style="padding:20px;text-align:center;color:red;">Error loading messages</div>`;
        }
    }

    // Start Polling for New Updates (Global)
    // Removed duplicate setInterval here, moved to top of function


    // Bind emoji listener (guarded — only binds once, even though setupChatLogic runs on every chat open)
    if (typeof bindEmojiListener === 'function') bindEmojiListener();

    // Typing Listener
    const msgInput = document.getElementById('msg-input');
    if (msgInput) {
        let typingTimeout;
        msgInput.addEventListener('input', () => {
            if (state.activeChatId && state.user) {
                // 1. Emit Typing (Throttled)
                if (!window.lastTypingEmit || Date.now() - window.lastTypingEmit > 2000) {
                    window.lastTypingEmit = Date.now();

                    if (socket && socket.connected) {
                        socket.emit('typing', {
                            senderId: state.user.user.id,
                            receiverId: state.activeChatId
                        });
                    }
                }

                // 2. Debounce Stop Typing
                if (typingTimeout) clearTimeout(typingTimeout);
                typingTimeout = setTimeout(() => {
                    if (socket && socket.connected) {
                        socket.emit('stop_typing', {
                            senderId: state.user.user.id,
                            receiverId: state.activeChatId
                        });
                    }
                }, 3000);
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
            // INCREMENTAL SYNC: Only fetch messages newer than what we have.
            // If we have no messages, fetch everything (since=0).
            const since = (state.messages.length > 0) ? Math.max(...state.messages.map(m => m.timestamp)) : 0;

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
                        let header = null;
                        const msgIndex = state.messages.findIndex(m => m.id === msg.id);
                        if (msgIndex > 0) {
                            const prevMsg = state.messages[msgIndex - 1];
                            const prevDate = new Date(prevMsg.timestamp).toDateString();
                            const currDate = new Date(msg.timestamp).toDateString();
                            if (prevDate !== currDate) {
                                header = getChatDateHeader(msg.timestamp);
                            }
                        } else if (msgIndex === 0) {
                            header = getChatDateHeader(msg.timestamp);
                        }

                        appendMessage(msg, activeContainer, header);
                    }

                    // 3. Read Acknowledgment Logic
                    if (msg.receiverId == state.user.user.id && msg.status !== 'seen') {
                        needsReadAck = true;
                    }
                });
                
                if (activeUpdates && activeUpdates.length > 0) {
                    // Update Cache
                    saveChatToCache(state.activeChatId, state.messages);
                }

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
                // [DEPRECATED] Moved to dedicated Active Poll for strict context protection.
                // We NO LONGER append to messages-container here to prevent cross-chat leakage.

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

        // 4. Typing Status Poll REMOVED
        // We now use Socket events (typing/stop_typing) to show a Bubble at the bottom.
        // This prevents overwriting the Header Status Carousel.
    } catch (e) {
        if (e.message === 'Unauthorized' || e.message === 'Invalid Token') {
            console.warn("[Polling] Auth error detected. Persistent errors will eventually require re-login.");
            // REMOVED: immediate window.logout() to prevent logout during server restarts
        }
    }
}

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

function getChatDateHeader(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const isToday = date.toDateString() === now.toDateString();

    // Check Yesterday
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    const isYesterday = date.toDateString() === yesterday.toDateString();

    if (isToday) return 'Today';
    if (isYesterday) return 'Yesterday';

    // Default: DD/MM/YY
    return date.toLocaleDateString([], { day: '2-digit', month: '2-digit', year: '2-digit' });
}

function appendMessage(msg, container, dateHeader = null, animate = true) {
    if (!container) return;

    // [STRICT CONTEXT] Ensure we don't leak messages between chats
    // Compare sender/receiver against active state
    const isGeneral = state.activeChatId === 'general';
    const msgBelongsHere = isGeneral ? (msg.receiverId === 'general') : (msg.senderId === state.activeChatId || msg.receiverId === state.activeChatId);
    
    if (!msgBelongsHere) {
        console.warn("[Context] Prevented cross-chat leakage for msg:", msg.id);
        return;
    }

    // Render Date Header
    if (dateHeader) {
        const headerDiv = document.createElement('div');
        headerDiv.className = 'date-header animate__animated animate__fadeIn';
        headerDiv.innerHTML = `<span>${dateHeader}</span>`;
        container.appendChild(headerDiv);
    }

    const isMe = msg.senderId === state.user.user.id;
    const div = document.createElement('div');
    div.id = `msg-${msg.id}`;
    // Conditional Animation
    const animClass = animate ? 'animate__animated animate__fadeInUp' : '';
    div.className = `message-bubble ${isMe ? 'message-sent' : 'message-received'} ${animClass}`;

    // Use LIVE avatar for self, otherwise use message avatar
    const avatarToUse = isMe ? state.user.user.avatar : msg.avatar;

    let contentHtml = `<div class="msg-content">${msg.content}</div>`;
    if (msg.type === 'image') {
        const cachedUrl = window.getCachedMediaUrlSync ? window.getCachedMediaUrlSync(msg.content) : msg.content;
        contentHtml = `<img src="${cachedUrl}" data-original="${msg.content}" class="msg-image cached-media" onclick="window.openMediaViewer('${msg.content}', 'image')" style="cursor:pointer;">`;
    } else if (msg.type === 'video') {
        const cachedUrl = window.getCachedMediaUrlSync ? window.getCachedMediaUrlSync(msg.content) : msg.content;
        contentHtml = `<video src="${cachedUrl}" data-original="${msg.content}" controls class="msg-video cached-media"></video>`;
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
    } else if (msg.type === 'audio') {
        // Voice Message
        const audioId = 'audio-' + msg.id;
        const cachedUrl = window.getCachedMediaUrlSync ? window.getCachedMediaUrlSync(msg.content) : msg.content;
        contentHtml = `
            <div class="voice-msg" id="vm-${msg.id}">
                <button class="play-voice-btn" onclick="window.playVoice('${audioId}', '${msg.id}')">
                    <i class="fas fa-play"></i>
                </button>
                <div class="voice-progress-wrap">
                    <div class="voice-progress-bar" id="vp-${msg.id}"></div>
                </div>
                <span class="voice-duration" id="vd-${msg.id}">0:00</span>
                <audio id="${audioId}" src="${cachedUrl}" data-original="${msg.content}" preload="metadata" class="cached-media"
                    onloadedmetadata="window.setVoiceDuration('${msg.id}', this.duration)"></audio>
            </div>
        `;
    } else if (msg.type === 'gif') {
        // GIF Message
        const cachedUrl = window.getCachedMediaUrlSync ? window.getCachedMediaUrlSync(msg.content) : msg.content;
        contentHtml = `<img src="${cachedUrl}" data-original="${msg.content}" class="msg-gif cached-media" onclick="window.openMediaViewer('${msg.content}', 'image')" style="cursor:pointer;">`;
    } else if (msg.type === 'call_log') {
        // Call Status Message — render as centered system-style message with icon
        let callIcon = 'fa-phone';
        let callColor = '#94a3b8';
        let callLabel = msg.content;

        if (msg.content.includes('Answered')) {
            callIcon = 'fa-phone';
            callColor = '#22c55e';
        } else if (msg.content.includes('Declined')) {
            callIcon = 'fa-phone-slash';
            callColor = '#ef4444';
        } else if (msg.content.includes('No Answer') || msg.content.includes('Missed')) {
            callIcon = 'fa-phone-slash';
            callColor = '#f59e0b';
        }

        const callDate = new Date(msg.timestamp);
        const callTime = callDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        div.className = 'call-log-message animate__animated animate__fadeIn';
        div.innerHTML = `
            <i class="fas ${callIcon}" style="color:${callColor};font-size:0.85rem;"></i>
            <span>${callLabel}</span>
            <span class="call-log-time">${callTime}</span>
        `;
        container.appendChild(div);
        return;
    } else if (msg.type === 'system') {
        // System Message Style
        div.className = 'message-bubble system-message animate__animated animate__fadeIn';
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
            <div class="msg-menu-btn" onclick="event.stopPropagation(); window.handleMessageLongPress('${msg.id}')"><i class="fas fa-chevron-down"></i></div>
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
    // Desktop Hover Reply Button (PC only)
    if (!('ontouchstart' in window)) {
        const replyShortcut = document.createElement('button');
        replyShortcut.className = 'hover-reply-btn';
        replyShortcut.innerHTML = '<i class="fas fa-reply"></i>';
        replyShortcut.title = 'Reply';
        replyShortcut.onclick = (e) => {
            e.stopPropagation();
            window.replyToMessage(msg.id);
        };
        div.appendChild(replyShortcut);
    }

    // Add touch events for swipe-to-reply (Mobile)
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

window.toggleEmojiPicker = () => {
    const picker = document.getElementById('emoji-picker');
    if (picker) {
        if (picker.classList.contains('hidden')) {
            picker.classList.remove('hidden');
            // Hide attachment menu if open
            const attachMenu = document.getElementById('attachment-menu');
            if (attachMenu && !attachMenu.classList.contains('hidden')) {
                attachMenu.classList.add('hidden');
                attachMenu.classList.remove('animate__animated', 'animate__fadeInUp');
            }
        } else {
            picker.classList.add('hidden');
        }
    }
};

// ===== ATTACHMENT MENU TOGGLE =====
window.toggleAttachmentMenu = function (e) {
    if (e && e.stopPropagation) e.stopPropagation();

    const menu = document.getElementById('attachment-menu');
    if (!menu) return;

    if (menu.classList.contains('hidden')) {
        menu.classList.remove('hidden');
        // Close emoji picker if open
        const emoji = document.getElementById('emoji-picker');
        if (emoji && !emoji.classList.contains('hidden')) {
            emoji.classList.add('hidden');
        }
    } else {
        menu.classList.add('hidden');
    }
};

// ===== EMOJI PICKER TOGGLE =====
window.toggleEmojiPicker = function (e) {
    if (e && e.stopPropagation) e.stopPropagation();

    const picker = document.getElementById('emoji-picker');
    if (!picker) return;

    if (picker.classList.contains('hidden')) {
        picker.classList.remove('hidden');
        // Close attachment menu if open
        const menu = document.getElementById('attachment-menu');
        if (menu && !menu.classList.contains('hidden')) {
            menu.classList.add('hidden');
        }
    } else {
        picker.classList.add('hidden');
    }
};

// ===== UNIFIED CLICK-OUTSIDE HANDLER (WhatsApp Style) =====
// Single listener in bubble phase. Closes any open menu/picker when clicking outside.
document.addEventListener('click', function (e) {
    // --- Attachment Menu ---
    const menu = document.getElementById('attachment-menu');
    if (menu && !menu.classList.contains('hidden')) {
        const paperclipBtn = e.target.closest('button[onclick*="toggleAttachmentMenu"]');
        if (!menu.contains(e.target) && !paperclipBtn) {
            menu.classList.add('hidden');
        }
    }

    // --- Emoji Picker ---
    const picker = document.getElementById('emoji-picker');
    if (picker && !picker.classList.contains('hidden')) {
        const emojiBtn = e.target.closest('button[onclick*="toggleEmojiPicker"]');
        if (!picker.contains(e.target) && !emojiBtn) {
            picker.classList.add('hidden');
        }
    }

    // --- Chat Menu Dropdown ---
    const chatMenu = document.getElementById('chat-menu-dropdown');
    if (chatMenu && !chatMenu.classList.contains('hidden')) {
        const chatMenuBtn = e.target.closest('button[onclick*="toggleChatMenu"]');
        if (!chatMenu.contains(e.target) && !chatMenuBtn) {
            chatMenu.classList.add('hidden');
        }
    }
});

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


window.startNewChat = () => {
    const searchInput = document.getElementById('user-search');
    if (searchInput) {
        searchInput.focus();
        // Optional: clear it
        searchInput.value = '';
        state.isSearching = false;
        render(); // Force sidebar to search mode
    }
};

window.handleSearch = async (query) => {
    state.lastSearchQuery = query;
    state.isSearching = query.length >= 2;
    const listContainer = document.getElementById('chat-list');

    if (!state.isSearching) {
        state.searchResults = [];
        if (window.refreshSidebar) window.refreshSidebar();
        return;
    }

    try {
        const results = await api.searchUsers(query);
        state.searchResults = results;
        if (window.refreshSidebar) window.refreshSidebar();
    } catch (e) {
        console.error(e);
    }
};

window.openSettings = async (view = 'main') => {
    // Admin is a special full-page view
    if (view === 'admin') {
        window.closeSettings();
        document.getElementById('admin-dashboard-overlay').classList.remove('hidden');
        renderAdminDashboard();
        return;
    }

    state.settingsView = view;
    render();

    // Background Sync: Re-fetch profile to pick up changes like isAdmin status
    try {
        const freshUser = await api.getMe();
        if (freshUser && freshUser.id) {
            state.user.user = freshUser;
            localStorage.setItem('oma_user', JSON.stringify(state.user));
            // If we are in main settings, re-render to show/hide Admin Panel based on fresh data
            if (state.settingsView === 'main') render();
        }
    } catch (e) {
        console.warn("[Settings] Profile sync failed", e);
    }
};

window.closeAdminDashboard = () => {
    document.getElementById('admin-dashboard-overlay').classList.add('hidden');
};

window.closeSettings = () => {
    state.settingsView = null;
    render();
};

window.setWallpaper = (id) => {
    localStorage.setItem('oma_wallpaper', id);
    // Update active chat area if visible
    const chatContainer = document.getElementById('messages-container');
    if (chatContainer) {
        chatContainer.className = `messages-container wallpaper-${id}`;
        // Force background update if using CSS var or class-based logic
        // For now, let's assume classes are handled in CSS or we just re-render settings to show active state
    }
    const currentView = state.settingsView;
    if (currentView === 'appearance') {
        render(); // Re-render settings to show selection border
    }
};

window.toggleChatMenu = () => {
    const menu = document.getElementById('chat-menu-dropdown');
    if (menu) {
        menu.classList.toggle('hidden');
    }
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
        const res = await api.updateProfile({ name, bio });
        const userData = res.user || res;
        state.user.user = { ...state.user.user, ...userData };
        localStorage.setItem('oma_user', JSON.stringify(state.user));
        render(); // Refresh UI
        showCustomAlert('Profile Updated!', 'success');
    } catch (e) {
        showCustomAlert('Update failed', 'error');
    }
};

window.savePrivacy = async () => {
    const lastSeen = document.getElementById('privacy-lastseen').value;
    const profilePhoto = document.getElementById('privacy-profilephoto').value;
    const about = document.getElementById('privacy-about').value;
    const readReceipts = document.getElementById('privacy-readreceipts').checked;

    const newSettings = {
        lastSeenPrivacy: lastSeen,
        profilePhotoPrivacy: profilePhoto,
        aboutPrivacy: about,
        readReceipts: readReceipts
    };

    try {
        // Optimistic UI Update
        state.user.user.settings = { ...state.user.user.settings, ...newSettings };
        
        // Sync to local storage
        localStorage.setItem('oma_user', JSON.stringify(state.user));

        await api.updateProfile({ settings: newSettings });
        console.log("[Client] Privacy settings updated successfully");
    } catch (e) {
        console.error("Privacy update failed", e);
        showCustomAlert('Failed to save privacy settings', 'error');
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
                            <div id="recaptcha-container" style="margin: 15px 0; display: flex; justify-content: center;"></div>
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

        // --- NATIVE FLOW ---
        if (Capacitor.isNativePlatform()) {
            console.log("Starting Native Link flow...");

            if (!window.linkAuthListenersInitialized) {
                await FirebaseAuthentication.addListener('phoneCodeSent', (event) => {
                    console.log("Native Link phoneCodeSent:", event);
                    window.nativeLinkVerificationId = event.verificationId;
                    document.getElementById('link-phone-input-group').style.display = 'none';
                    document.getElementById('link-otp-group').style.display = 'block';
                    document.getElementById('link-error').innerText = '';
                });

                await FirebaseAuthentication.addListener('phoneVerificationFailed', (event) => {
                    console.error("Native Link phoneVerificationFailed:", event);
                    document.getElementById('link-error').innerText = `Native Link Error: ${event.message}`;
                    const btn = document.getElementById('btn-send-link-otp');
                    if (btn) {
                        btn.disabled = false;
                        btn.innerText = 'Link Phone';
                    }
                });

                await FirebaseAuthentication.addListener('phoneVerificationCompleted', async (event) => {
                    console.log("Native Link phoneVerificationCompleted (Auto-verify):", event);
                    try {
                        const tokenResult = await FirebaseAuthentication.getIdToken();
                        const idToken = tokenResult.token;

                        if (idToken) {
                            const res = await api.linkPhone(idToken);
                            state.user.user.phone = res.phoneNumber;
                            state.user.user.settings.phoneLinked = true;
                            localStorage.setItem('oma_user', JSON.stringify(state.user));
                            alert("Phone number linked automatically!");
                            render();
                        }
                    } catch (e) {
                        console.error("Auto-link backend error:", e);
                    }
                });

                window.linkAuthListenersInitialized = true;
            }

            await FirebaseAuthentication.signInWithPhoneNumber({ phoneNumber: phone });
            return;
        }

        // --- WEB FLOW ---
        // Fix for auth/argument-error: Clear previous verifier instance
        if (window.recaptchaVerifier) {
            try { window.recaptchaVerifier.clear(); } catch (e) { }
            window.recaptchaVerifier = null;
        }
        const container = document.getElementById('recaptcha-container');
        if (container) container.innerHTML = '';

        window.recaptchaVerifier = new firebase.auth.RecaptchaVerifier('recaptcha-container', {
            'size': 'normal'
        });

        window.linkConfirmationResult = await firebase.auth().signInWithPhoneNumber(phone, window.recaptchaVerifier);

        document.getElementById('link-phone-input-group').style.display = 'none';
        document.getElementById('link-otp-group').style.display = 'block';
        errorMsg.innerText = '';
    } catch (error) {
        console.error("Link SMS Send Error:", error);
        errorMsg.innerText = `Error: ${error.code} - ${error.message} (Origin: ${window.location.origin})`;
        btn.disabled = false;
        btn.innerText = 'Link Phone';
    }
};

window.handleVerifyLinkOTP = async () => {
    const code = document.getElementById('linkOtpCode').value;
    const errorMsg = document.getElementById('link-error');

    try {
        let idToken;
        const btn = document.querySelector('#link-otp-group button');
        if (btn) { btn.disabled = true; btn.innerText = 'Verifying...'; }

        if (Capacitor.isNativePlatform()) {
            console.log("Verifying Native Link OTP...");
            await FirebaseAuthentication.confirmVerificationCode({
                verificationId: window.nativeLinkVerificationId,
                verificationCode: code
            });
            const tokenResult = await FirebaseAuthentication.getIdToken();
            idToken = tokenResult.token;
        } else {
            console.log("Verifying Web Link OTP...");
            if (!window.linkConfirmationResult) {
                throw new Error("No active linking request found. Please try sending OTP again.");
            }
            const result = await window.linkConfirmationResult.confirm(code);
            idToken = await result.user.getIdToken();
        }

        console.log("Sending linking request to OMA backend...");
        const res = await api.linkPhone(idToken);

        // Update local state
        state.user.user.phone = res.phoneNumber;
        state.user.user.settings = state.user.user.settings || {};
        state.user.user.settings.phoneLinked = true;
        localStorage.setItem('oma_user', JSON.stringify(state.user));

        alert("Phone number linked successfully!");
        window.openSettings('account'); // Refresh view
    } catch (error) {
        console.error("Link OTP Verification Error:", error);
        let msg = error.message || 'Verification failed';
        if (error.response?.data?.error) msg = error.response.data.error;
        errorMsg.innerText = msg;
        const btn = document.querySelector('#link-otp-group button');
        if (btn) { btn.disabled = false; btn.innerText = 'Verify & Link'; }
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

// NOTE: toggleAttachmentMenu defined at ~line 2867.
// NOTE: toggleEmojiPicker defined at ~line 2886.
// NOTE: Click-outside handler defined at ~line 2907.
// DO NOT re-define them here.

// Handle Emoji Selection (SINGLE global listener)
let _emojiListenerBound = false;
function bindEmojiListener() {
    if (_emojiListenerBound) return;
    const picker = document.querySelector('emoji-picker');
    if (picker) {
        picker.addEventListener('emoji-click', event => {
            const input = document.getElementById('msg-input');
            if (input) {
                input.value += event.detail.unicode;
                input.focus();
            }
        });
        _emojiListenerBound = true;
    }
}
// Try to bind now, and also on DOM ready
bindEmojiListener();
document.addEventListener('DOMContentLoaded', bindEmojiListener);

window.toggleChatMenu = () => {
    const menu = document.getElementById('chat-menu-dropdown');
    if (menu) menu.classList.toggle('hidden');
};

window.deleteCurrentChat = async () => {
    const chatId = state.activeChatId;
    if (!chatId || chatId === 'general') return;

    if (!confirm('Are you sure you want to delete this chat for everyone? This cannot be undone.')) return;

    try {
        await api.deleteChat(chatId);
        window.closeChat();

        // Remove from list locally
        state.chats = state.chats.filter(c => c.id !== chatId);
        localStorage.setItem('oma_chats', JSON.stringify(state.chats));

        // Remove from messages
        state.messages = [];

        render(); // Refresh Sidebar
        alert('Chat deleted.');
    } catch (e) {
        alert('Failed to delete chat: ' + (e.error || e.message));
    }
};


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

// NOTE: toggleEmojiPicker is defined at ~line 2886. DO NOT re-define here.

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

// ===== DYNAMIC SEND / MIC BUTTON =====
window.updateSendBtn = () => {
    const input = document.getElementById('msg-input');
    const btn = document.getElementById('send-or-mic-btn');
    if (!input || !btn) return;
    const icon = btn.querySelector('i');
    if (input.value.trim().length > 0) {
        icon.className = 'fas fa-paper-plane';
        icon.style.transform = 'rotate(-30deg)';
        btn._isRecordMode = false;
    } else {
        icon.className = 'fas fa-microphone';
        icon.style.transform = '';
        btn._isRecordMode = true;
    }
};

// ===== VOICE RECORDING =====
let _mediaRecorder = null;
let _audioChunks = [];
let _recordingTimerInterval = null;
let _recordingSeconds = 0;

window.startVoiceRecording = async () => {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        // Try webm first, fallback to ogg, then default
        const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
            ? 'audio/webm;codecs=opus'
            : MediaRecorder.isTypeSupported('audio/ogg;codecs=opus')
                ? 'audio/ogg;codecs=opus'
                : '';

        _mediaRecorder = new MediaRecorder(stream, mimeType ? { mimeType } : {});
        _audioChunks = [];
        _recordingSeconds = 0;

        _mediaRecorder.ondataavailable = (e) => {
            if (e.data.size > 0) _audioChunks.push(e.data);
        };

        _mediaRecorder.onstop = () => {
            // Stop all tracks to release mic
            stream.getTracks().forEach(t => t.stop());
        };

        _mediaRecorder.start(100); // collect in 100ms chunks

        // Show recording bar, hide input area
        const inputArea = document.querySelector('.input-area');
        const recordBar = document.getElementById('voice-recording-bar');
        if (inputArea) inputArea.classList.add('hidden');
        if (recordBar) recordBar.classList.remove('hidden');

        // Start timer
        const timerEl = document.getElementById('recording-timer');
        _recordingTimerInterval = setInterval(() => {
            _recordingSeconds++;
            const m = Math.floor(_recordingSeconds / 60);
            const s = (_recordingSeconds % 60).toString().padStart(2, '0');
            if (timerEl) timerEl.textContent = `${m}:${s}`;
        }, 1000);

    } catch (e) {
        console.error('Mic access denied', e);
        alert('Microphone access is required for voice messages.');
    }
};

window.stopVoiceRecording = async () => {
    if (!_mediaRecorder || _mediaRecorder.state === 'inactive') return;
    if (window._voiceSending) return; // Guard against double sends
    window._voiceSending = true;

    return new Promise((resolve) => {
        const recorder = _mediaRecorder;
        const chunks = [..._audioChunks]; // Snapshot chunks
        _audioChunks = []; // Clear immediately to prevent re-use
        _mediaRecorder = null; // Prevent re-entry

        recorder.onstop = async () => {
            // Stop mic tracks
            recorder.stream.getTracks().forEach(t => t.stop());

            // Clear timer
            clearInterval(_recordingTimerInterval);
            _recordingTimerInterval = null;

            // Restore UI
            const inputArea = document.querySelector('.input-area');
            const recordBar = document.getElementById('voice-recording-bar');
            if (inputArea) inputArea.classList.remove('hidden');
            if (recordBar) recordBar.classList.add('hidden');

            if (chunks.length === 0) { window._voiceSending = false; resolve(); return; }

            // Convert to base64
            const blob = new Blob(chunks, { type: recorder.mimeType || 'audio/webm' });
            const reader = new FileReader();
            reader.onloadend = async () => {
                const base64 = reader.result;
                try {
                    await api.sendMessage(base64, 'audio', state.activeChatId);
                    const container = document.getElementById('messages-container');
                    if (container) pollMessages(container);
                } catch (err) {
                    console.error('Voice send failed', err);
                    alert('Failed to send voice message.');
                }
                window._voiceSending = false;
                resolve();
            };
            reader.readAsDataURL(blob);
        };
        recorder.stop();
    });
};

window.cancelVoiceRecording = () => {
    if (_mediaRecorder && _mediaRecorder.state !== 'inactive') {
        _mediaRecorder.stream.getTracks().forEach(t => t.stop());
        _mediaRecorder.stop();
    }
    _audioChunks = [];
    clearInterval(_recordingTimerInterval);
    _recordingTimerInterval = null;

    // Restore UI
    const inputArea = document.querySelector('.input-area');
    const recordBar = document.getElementById('voice-recording-bar');
    if (inputArea) inputArea.classList.remove('hidden');
    if (recordBar) recordBar.classList.add('hidden');
};

// ===== VOICE PLAYBACK =====
let _currentPlayingAudio = null;
let _currentPlayingId = null;

window.playVoice = (audioId, msgId) => {
    const audio = document.getElementById(audioId);
    if (!audio) return;

    const btn = document.querySelector(`#vm-${msgId} .play-voice-btn i`);
    const progressBar = document.getElementById(`vp-${msgId}`);

    // If already playing this one, pause it
    if (_currentPlayingId === msgId && !audio.paused) {
        audio.pause();
        if (btn) btn.className = 'fas fa-play';
        return;
    }

    // Stop any other playing voice
    if (_currentPlayingAudio && _currentPlayingAudio !== audio) {
        _currentPlayingAudio.pause();
        _currentPlayingAudio.currentTime = 0;
        const oldBtn = document.querySelector(`#vm-${_currentPlayingId} .play-voice-btn i`);
        if (oldBtn) oldBtn.className = 'fas fa-play';
        const oldBar = document.getElementById(`vp-${_currentPlayingId}`);
        if (oldBar) oldBar.style.width = '0%';
    }

    _currentPlayingAudio = audio;
    _currentPlayingId = msgId;
    if (btn) btn.className = 'fas fa-pause';
    audio.play();

    audio.ontimeupdate = () => {
        if (audio.duration && progressBar) {
            progressBar.style.width = `${(audio.currentTime / audio.duration) * 100}%`;
        }
    };
    audio.onended = () => {
        if (btn) btn.className = 'fas fa-play';
        if (progressBar) progressBar.style.width = '0%';
        _currentPlayingAudio = null;
        _currentPlayingId = null;
    };
};

window.setVoiceDuration = (msgId, duration) => {
    const el = document.getElementById(`vd-${msgId}`);
    if (el && duration && isFinite(duration)) {
        const m = Math.floor(duration / 60);
        const s = Math.floor(duration % 60).toString().padStart(2, '0');
        el.textContent = `${m}:${s}`;
    }
};

// ===== GIF PICKER =====
let _gifSearchTimeout = null;
const TENOR_API_KEY = 'AIzaSyAyimkuYQYF_FXVALexPuGQctUWRURdCYQ'; // Free Tenor API key

window.toggleGifPicker = (e) => {
    if (e) e.stopPropagation();
    const picker = document.getElementById('gif-picker');
    if (!picker) return;

    const isHidden = picker.classList.contains('hidden');
    picker.classList.toggle('hidden');

    // Load trending on first open
    if (isHidden && !picker._loaded) {
        picker._loaded = true;
        window.fetchGifs('trending');
    }
};

window.fetchGifs = async (query) => {
    const resultsDiv = document.getElementById('gif-results');
    if (!resultsDiv) return;
    resultsDiv.innerHTML = '<div class="gif-loading"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';

    try {
        const endpoint = query === 'trending'
            ? `https://tenor.googleapis.com/v2/featured?key=${TENOR_API_KEY}&limit=30&media_filter=tinygif`
            : `https://tenor.googleapis.com/v2/search?q=${encodeURIComponent(query)}&key=${TENOR_API_KEY}&limit=30&media_filter=tinygif`;

        const res = await fetch(endpoint);
        const data = await res.json();

        if (!data.results || data.results.length === 0) {
            resultsDiv.innerHTML = '<div class="gif-loading">No GIFs found</div>';
            return;
        }

        resultsDiv.innerHTML = data.results.map(gif => {
            const url = gif.media_formats?.tinygif?.url || gif.media_formats?.gif?.url;
            if (!url) return '';
            return `<img src="${url}" class="gif-item" onclick="window.sendGif('${url}')" alt="GIF" loading="lazy">`;
        }).join('');

    } catch (err) {
        console.error('GIF fetch failed', err);
        resultsDiv.innerHTML = '<div class="gif-loading">Failed to load GIFs</div>';
    }
};

window.searchGifs = (query) => {
    clearTimeout(_gifSearchTimeout);
    if (!query || query.trim().length < 2) {
        window.fetchGifs('trending');
        return;
    }
    _gifSearchTimeout = setTimeout(() => {
        window.fetchGifs(query.trim());
    }, 400);
};

window.sendGif = async (url) => {
    try {
        await api.sendMessage(url, 'gif', state.activeChatId);
        const container = document.getElementById('messages-container');
        if (container) pollMessages(container);
        // Close GIF picker
        const picker = document.getElementById('gif-picker');
        if (picker) picker.classList.add('hidden');
    } catch (err) {
        console.error('GIF send failed', err);
    }
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
    localStorage.removeItem('oma_chats');
    window.location.hash = '#login';
    window.location.reload();
};



window.loginUser = (data) => {
    state.user = data;
    localStorage.setItem('oma_user', JSON.stringify(data));
    window.location.hash = '#chat';
    initSocket();

    // Unified Push Registration for Native
    if (window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform()) {
        if (typeof registerPush === 'function') {
            registerPush().catch(e => console.error("Push Init Failed on Login:", e));
        }
    }
};

window.clearChats = async () => {
    if (confirm('Are you sure you want to clear your recent chats list?')) {
        try { if (window.db) await window.db.clear(); } catch (e) { }
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

// --- Group Info Logic ---
window.openGroupInfo = async () => {
    const groupId = state.activeChatId;
    if (!groupId) return;
    const group = state.chats.find(c => c.id === groupId);
    if (!group) return;

    // We need fresh details usually, but let's assume Members are in group obj?
    // Actually our chat object in list might count members but not list IDs if optimized.
    // Let's assume we need to fetch group details.
    // For now, let's look at what we have.
    // Group API response usually includes 'members' array of IDs.

    // Fallback: If no members, fetch.
    let members = group.members || [];
    // If we only have IDs, we need to fetch User Objects.
    let memberDetails = [];
    if (members.length > 0) {
        memberDetails = await api.batchGetUsers(members);
    }

    const isAdmin = group.adminIds && group.adminIds.includes(state.user.user.id);
    const creatorId = group.createdBy;

    const modalHtml = `
        <div id="group-info-modal" style="position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.6);backdrop-filter:blur(5px);z-index:9999;display:flex;justify-content:center;align-items:center;" onclick="if(event.target.id==='group-info-modal') document.getElementById('group-info-modal').remove()">
            <div style="background:var(--sidebar-bg); border:1px solid var(--border-color); width:90%; max-width:400px; border-radius:16px; padding:24px; box-shadow:0 20px 50px rgba(0,0,0,0.3); color:var(--text-primary);">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
                    <h3 style="margin:0;">Group Info</h3>
                    <button onclick="document.getElementById('group-info-modal').remove()" class="icon-btn"><i class="fas fa-times"></i></button>
                </div>
                
                <div style="text-align:center;margin-bottom:20px;">
                    <img src="${getAvatarUrl(group)}" style="width:80px;height:80px;border-radius:50%;margin-bottom:10px;">
                    <h2>${group.name}</h2>
                    <p style="color:var(--text-secondary);">${members.length} members</p>
                </div>

                <div style="margin-bottom:20px;">
                    <label style="display:block;margin-bottom:10px;color:var(--text-secondary);font-size:0.9rem;">Actions</label>
                    ${isAdmin ? `<button onclick="window.promptAddMember('${groupId}')" class="primary" style="width:100%;margin-bottom:10px;padding:10px;">Add Member</button>` : ''}
                    <button onclick="window.leaveGroup('${groupId}')" style="width:100%;padding:10px;background:#ef4444;color:white;border:none;border-radius:8px;font-weight:600;">Leave Group</button>
                </div>

                <div style="max-height:200px;overflow-y:auto;border-top:1px solid var(--border-color);">
                    ${memberDetails.map(u => {
        const isSelf = u.id === state.user.user.id;
        const isMemberAdmin = (group.adminIds || [group.adminId]).includes(u.id);

        return `
                        <div style="padding:10px; display:flex; align-items:center; gap:10px; border-bottom:1px solid var(--border-color);">
                            <img src="${getAvatarUrl(u)}" class="avatar-small">
                            <div style="flex:1;">
                                <div style="font-weight:600;">${u.name}</div>
                                <div style="font-size:0.8rem;color:var(--text-secondary);">
                                    @${u.username} 
                                    ${isMemberAdmin ? '<span style="color:var(--primary-color); border:1px solid var(--primary-color); border-radius:4px; padding:0 4px; font-size:0.7rem;">Admin</span>' : ''}
                                </div>
                            </div>
                            
                            <!-- Admin Actions -->
                            ${isAdmin && !isSelf ? `
                                <div style="display:flex; gap:5px;">
                                    ${!isMemberAdmin ? `
                                        <button onclick="window.manageGroupMember('${groupId}', '${u.id}', 'promote')" title="Make Admin" class="icon-btn-small" style="color:#10b981;"><i class="fas fa-crown"></i></button>
                                    ` : `
                                        <button onclick="window.manageGroupMember('${groupId}', '${u.id}', 'demote')" title="Remove Admin" class="icon-btn-small" style="color:#f59e0b;"><i class="fas fa-user-shield"></i></button>
                                    `}
                                    <button onclick="window.manageGroupMember('${groupId}', '${u.id}', 'remove')" title="Remove Member" class="icon-btn-small" style="color:#ef4444;"><i class="fas fa-times"></i></button>
                                </div>
                            ` : ''}
                        </div>
                        `;
    }).join('')}
                </div>
            </div>
        </div>
    `;

    const div = document.createElement('div');
    div.innerHTML = modalHtml;
    document.body.appendChild(div);
};

window.manageGroupMember = async (groupId, memberId, action) => {
    if (!confirm(`Are you sure you want to ${action} this member?`)) return;
    try {
        await api.manageGroup(groupId, memberId, action);
        document.getElementById('group-info-modal').remove();
        alert(`Member ${action}d successfully`);
        // Re-open info to refresh? Or just close.
        // Let's re-open
        setTimeout(window.openGroupInfo, 500);
    } catch (e) {
        alert('Action failed: ' + e.message);
    }
};

window.leaveGroup = async (groupId) => {
    if (!confirm('Are you sure you want to leave this group?')) return;
    try {
        await api.manageGroup(groupId, null, 'leave');
        document.getElementById('group-info-modal').remove();
        window.closeChat();

        // Remove from list locally
        state.chats = state.chats.filter(c => c.id !== groupId);
        localStorage.setItem('oma_chats', JSON.stringify(state.chats));
        render(); // Refresh Sidebar

        alert('You left the group');
    } catch (e) {
        alert('Failed to leave group');
    }
};

window.promptAddMember = async (groupId) => {
    const input = prompt("Enter User ID to add (Temporary):");
    // Ideally we show a user search modal. For speed, using prompt or we can reuse search.
    if (input) {
        try {
            await api.manageGroup(groupId, input, 'add');
            alert('Member added!');
            document.getElementById('group-info-modal').remove();
            // Reload info?
        } catch (e) {
            alert('Failed: ' + e.message);
        }
    }
};

window.addEventListener('hashchange', render);
window.addEventListener('DOMContentLoaded', () => {
    const user = localStorage.getItem('oma_user');
    if (user) {
        state.user = JSON.parse(user);
        // Resume session
        if (location.hash === '#login' || location.hash === '#register' || location.hash === '') {
            // Check if we have a valid token?
            // Let's assume yes and go to chat.
            location.hash = '#chat';
        }

        // Start Battery Service
        window.initBatteryService();
        initSocket(); // Connect immediately on load

        // Init Push (if native) - Non-blocking
        if (typeof registerPush === 'function') {
            registerPush().catch(e => console.error("Push Init Failed (Non-fatal):", e));
        }

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
            if (err.message === 'Unauthorized' || err.message === 'Invalid Token') {
                console.error("[Sync] Valid session could not be verified. Logging out...");
                window.logout();
            }
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
        const statusEl = document.getElementById('restoration-status');
        if (statusEl) statusEl.textContent = "Connecting to secure message vault...";

        Promise.all([api.getGroups(), api.getRecentChats()])
            .then(([groups, recentDMs]) => {
                if (statusEl) statusEl.textContent = "Decrypting conversation headers...";
                const allFetched = [];
                
                // Add "General Group" as a baseline if it doesn't exist
                allFetched.push({ 
                    id: 'general', 
                    name: 'General Group', 
                    lastMsg: 'Tap to chat', 
                    avatar: 'https://ui-avatars.com/api/?name=General+Group&background=random', 
                    timestamp: 0,
                    type: 'group'
                });

                if (Array.isArray(groups)) {
                    groups.forEach(g => allFetched.push({ ...g, type: 'group' }));
                }
                if (Array.isArray(recentDMs)) {
                    recentDMs.forEach(d => allFetched.push({ ...d, type: 'user' }));
                }

                // Standardize fields: ensure timestamp is a number and lastMsg is set
                const standardized = allFetched.map(c => ({
                    ...c,
                    timestamp: Number(c.timestamp || c.lastTimestamp || c.created || 0),
                    lastMsg: c.lastMsg || (c.type === 'group' ? 'Group created' : ''),
                    time: c.time || (c.timestamp ? new Date(Number(c.timestamp)).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '')
                }));

                updateStateChats(standardized);
                
                // Final sort and save
                state.chats.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
                localStorage.setItem('oma_chats', JSON.stringify(state.chats));
                if (window.refreshSidebar) window.refreshSidebar();
            })
            .catch(e => {
                console.error("Failed to sync chats:", e);
                // Even on error, ensure General is there
                updateStateChats([{ 
                    id: 'general', 
                    name: 'General Group', 
                    lastMsg: 'Chat online', 
                    avatar: 'https://ui-avatars.com/api/?name=General+Group&background=random', 
                    timestamp: 0,
                    type: 'group'
                }]);
                render();
            });
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
        if (typeof registerPush === 'function') {
            registerPush().catch(e => console.error("Push Init Failed (Non-fatal):", e));
        }
    }
});

// --- Media Viewer Logic ---
window.openMediaViewer = (src, type = 'image') => {
    const modal = document.getElementById('media-viewer-modal');
    const content = document.getElementById('media-viewer-content');
    if (!modal || !content) return;

    if (type === 'image') {
        content.innerHTML = `<img src="${src}" style="max-width:100%; max-height:80vh; border-radius:8px; box-shadow:0 0 20px rgba(0,0,0,0.5);">`;
    } else if (type === 'video') {
        content.innerHTML = `<video src="${src}" controls autoplay style="max-width:100%; max-height:80vh; border-radius:8px;"></video>`;
    }

    modal.classList.remove('hidden');
    modal.style.display = 'flex';
};

window.closeMediaViewer = () => {
    const modal = document.getElementById('media-viewer-modal');
    const content = document.getElementById('media-viewer-content');
    if (modal) {
        modal.classList.add('hidden');
        modal.style.display = 'none';
        content.innerHTML = ''; // Stop video playback
    }
};

// --- Back Button Handling (Updated for Media Viewer) ---

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
        // MUST point to the Render Backend (or Dev IP) for Native platforms!
        const getSocketUrl = () => {
            const manualIp = localStorage.getItem('oma_dev_ip'); // e.g., 'http://192.168.1.10:5000'
            if (manualIp) return manualIp;

            const isLocalWeb = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
            const isNative = window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform();

            if (isNative) {
                return 'https://oma-chat-app-pho0.onrender.com'; // Native must use absolute prod URL by default
            }
            
            return isLocalWeb ? 'http://localhost:3000' : 'https://oma-chat-app-pho0.onrender.com';
        };

        const socketUrl = getSocketUrl();
        console.log("[Client] Connecting Socket to:", socketUrl);

        socket = io(socketUrl, {
            reconnection: true,
            reconnectionAttempts: 5,
            reconnectionDelay: 1000,
            transports: ['websocket']
        });

        socket.on('connect', () => {
            console.log(`[Client] *** SOCKET CONNECTED: ${socket.id} ***`);
            document.documentElement.style.setProperty('--connection-status', '#22c55e'); // Green

            // NUKE GHOST SERVICE WORKERS (Fixing database.js error)
            if ('serviceWorker' in navigator) {
                navigator.serviceWorker.getRegistrations().then(registrations => {
                    for (let registration of registrations) {
                        // 🛡️ PROTECTION: Do NOT unregister our active notification service worker
                        if (registration.active && registration.active.scriptURL.includes('sw.js')) {
                            console.log("[Client] 🛡️ Preserving Active Notification Service Worker");
                            continue;
                        }
                        registration.unregister();
                        console.log("[Client] 🧹 Unregistered Ghost Service Worker:", registration);
                    }
                });
            }

            if (state.user) {
                console.log('[Client] Joining Room:', state.user.user.id);
                socket.emit('join', state.user.user.id);
            }
            
            // Adaptive Polling: Slow down when connected
            window.adjustPolling();
        });

        // Online Status Events
        socket.on('online_users', (users) => {
            console.log(`[Client] Received online_users list:`, users);
            state.onlineUsers = new Set(users);

            // Re-render sidebar if in messages view
            if (state.activeTab === 'messages' && window.refreshSidebar) {
                window.refreshSidebar();
            }
        });

        socket.on('user_status', (data) => {
            const { userId, online, lastSeen, battery } = data;

            // FIX: Only update online status if explicitly provided (handle partial updates)
            if (online !== undefined) {
                if (online) {
                    state.onlineUsers.add(userId);
                } else {
                    state.onlineUsers.delete(userId);
                }
            }

            // Update Cache
            state.userStatuses[userId] = {
                ...state.userStatuses[userId],
                ...(lastSeen !== undefined ? { lastSeen } : {}),
                ...(battery ? { battery } : {})
            };

            // CRITICAL: Update State Chat Object so Carousel sees it
            const chatIdx = state.chats.findIndex(c => c.id === userId);
            if (chatIdx !== -1) {
                if (online !== undefined) state.chats[chatIdx].status = online ? 'online' : 'offline';
                if (lastSeen !== undefined) state.chats[chatIdx].lastSeen = lastSeen;
                if (battery) state.chats[chatIdx].battery = battery;
            }

            // Update UI dynamically
            updateUserStatusUI(userId, online, lastSeen);
        });

        socket.on('typing', (data) => {
            console.log('[DEBUG] Socket Typing Event:', data);
            // New Visuals: Bubble
            if (state.activeChatId === data.senderId) {
                console.log('[DEBUG] Triggering Visuals for:', data.senderId);
                window.handleTypingVisuals(data.senderId, true);
            } else {
                console.log('[DEBUG] Mismatch:', state.activeChatId, '!==', data.senderId);
            }
        });

        socket.on('stop_typing', (data) => {
            if (state.activeChatId === data.senderId) {
                window.handleTypingVisuals(data.senderId, false);
            }
        });

        socket.on('disconnect', () => {
            console.log('Socket Disconnected');
            document.documentElement.style.setProperty('--connection-status', '#ef4444'); // Red
            
            // Adaptive Polling: Speed up for recovery
            window.adjustPolling();
        });

        socket.on('connect_error', (err) => {
            console.error('Socket Connection Error:', err);
            document.documentElement.style.setProperty('--connection-status', '#f59e0b'); // Orange
        });

        // Incoming Offer (Receive Call)
        window.handleIncomingCall = async (data) => {
            console.log('Handling Incoming Call:', data);

            // Prevent duplicate handling if already ringing for this call
            if (window.activeRingtoneCallId === data.callerId) return;
            window.activeRingtoneCallId = data.callerId;

            currentCallTargetId = data.callerId;
            soundManager.play('ringtone'); // Start Ringing

            // VIBRATE (Mobile Haptics)
            if (navigator.vibrate) {
                navigator.vibrate([1000, 1000, 1000, 1000, 1000, 1000, 1000]);
            }

            const popup = document.getElementById('incoming-call-popup');
            const nameEl = document.getElementById('caller-name');
            const avatarEl = document.getElementById('caller-avatar');
            if (popup) {
                popup.classList.remove('hidden');
                nameEl.textContent = data.callerName || 'Unknown';
                const avatarUrl = getAvatarUrl({ name: data.callerName, avatar: data.callerAvatar });
                avatarEl.src = avatarUrl;
                avatarEl.onerror = () => window.handleImageError(avatarEl, data.callerName || 'User');
            }
            window.pendingOffer = (typeof data.offer === 'string') ? JSON.parse(data.offer) : data.offer;
            window.pendingCallType = data.callType || data.type || 'video';

            // OS Notification with Actions (Web/PWA backup)
            if (!window.Capacitor?.isNativePlatform()) {
                window.showCallNotification(data.callerName, data.callerAvatar, window.pendingCallType, data.callerId);
            }
        };

        socket.on('offer', async (data) => {
            window.handleIncomingCall(data);
        });

        // Call Answered
        socket.on('answer', async (data) => {
            console.log('Call Answered:', data);
            soundManager.stop('calling'); // Stop Calling Tone
            if (peerConnection) {
                await peerConnection.setRemoteDescription(new RTCSessionDescription(data.answer));

                // Process Queued Candidates (Caller Side)
                while (window.iceCandidateQueue && window.iceCandidateQueue.length > 0) {
                    const c = window.iceCandidateQueue.shift();
                    peerConnection.addIceCandidate(c).catch(e => console.error("Queued ICE Error (Caller)", e));
                }

                startCallTimer(); // Start timer for caller
                wasConnected = true;
            }
        });

        // ICE Candidate
        socket.on('ice-candidate', (data) => {
            const candidate = new RTCIceCandidate(data.candidate);
            if (peerConnection && peerConnection.remoteDescription) {
                peerConnection.addIceCandidate(candidate).catch(e => console.error("ICE Error", e));
            } else {
                if (!window.iceCandidateQueue) window.iceCandidateQueue = [];
                window.iceCandidateQueue.push(candidate);
                // console.log("ICE Candidate Queued (Remote desc not ready)");
            }
        });

        // End Call
        socket.on('end-call', () => {
            endCallCleanup(true);
        });

        socket.on('receive_message', (msg) => {
            console.log('[Client] Received Message:', msg);

            // 1. Play Sound (Only for others)
            if (msg.senderId !== state.user.user.id) {
                soundManager.play('message');
            }

            // 2. Active Chat Update
            if (state.activeChatId === msg.receiverId || state.activeChatId === msg.senderId || (msg.receiverId === 'general' && state.activeChatId === 'general')) {
                // We are looking at this chat
                if (container) {
                    // [STRICT CONTEXT] Verify this message actually belongs to the OPEN chat
                    const isForActiveChat = 
                        (state.activeChatId === 'general' && msg.receiverId === 'general') ||
                        (state.activeChatId !== 'general' && (
                            (msg.senderId == state.activeChatId && msg.receiverId == state.user.user.id) ||
                            (msg.senderId == state.user.user.id && msg.receiverId == state.activeChatId) ||
                            (msg.receiverId === state.activeChatId) // Group support
                        ));

                    if (!isForActiveChat) {
                        console.log("[Socket] Ignoring message for inactive chat:", msg.senderId);
                        return;
                    }
                    // DUPLICATION FIX:
                    // If I sent this, check if we have a pending/optimistic message for it via Content/Timestamp linkage?
                    // Or simpler: If I am the sender, and I see a "sending" bubble, assumes it's this one.

                    if (msg.senderId === state.user.user.id) {
                        // 1. Precise Match via tempId
                        if (msg.tempId) {
                            const pending = document.querySelector(`.message-bubble[data-temp-id="${msg.tempId}"]`);
                            if (pending) {
                                // Transition Temp -> Real
                                pending.id = `msg-${msg.id}`;
                                pending.removeAttribute('data-temp-id');
                                pending.classList.remove('message-sending');
                                // Update check icon
                                const tick = pending.querySelector('.tick-icon');
                                if (tick) tick.innerHTML = '<i class="fas fa-check" style="color:rgba(255,255,255,0.5);"></i>';

                                console.log("Socket echo matched via tempId");
                                return; // Stop processing (don't append duplicate)
                            }
                        }

                        // 2. Fallback Heuristic (Legacy)
                        const pendingLegacy = Array.from(document.querySelectorAll('.message.sending')).find(el => {
                            return el.innerText.includes(msg.content);
                        });

                        if (pendingLegacy) {
                            console.log("Ignoring socket echo for own pending message (Legacy Match)");
                            return;
                        }
                    }

                    // Check if already exists (my own message via optimism that already finished)
                    if (!existing) {
                        state.messages.push(msg);
                        saveChatToCache(state.activeChatId, state.messages); // Persist immediately
                        
                        appendMessage(msg, container);
                        scrollToBottom(container);

                        // Mark as Read immediately (only if from others)
                        if (msg.senderId !== state.user.user.id) {
                            api.markAsRead(state.activeChatId).catch(console.error);
                        }
                    }
                }
            } else {
                // 3. Background/Sidebar Update
                // Increment Unread Count locally
                // Logic: If msg is from 'general', update 'general'. If from User, update User.
                const chatIdToUpdate = (msg.receiverId === 'general') ? 'general' : msg.senderId;

                // Don't mark my own messages as unread if they arrive on another device (optional preference)
                if (msg.senderId === state.user.user.id && msg.receiverId !== 'general') {
                    // Determine if I should update "Last Message" for my own chat in list? Yes.
                    // But unread count? No.
                    const sentStatsTarget = msg.receiverId;
                    const targetChat = state.chats.find(c => c.id === sentStatsTarget);
                    if (targetChat) {
                        targetChat.lastMsg = `You: ${msg.type === 'text' ? msg.content : 'Media'}`;
                        targetChat.time = new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                        refreshSidebar();
                    }
                    return;
                }

                const existingChat = state.chats.find(c => c.id === chatIdToUpdate);
                const updatedChat = {
                    id: chatIdToUpdate,
                    lastMsg: msg.type === 'text' ? msg.content : (msg.type === 'image' ? 'Photo' : 'File'),
                    time: new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
                    timestamp: msg.timestamp,
                    unread: (existingChat ? (existingChat.unread || 0) : 0) + (msg.senderId !== state.user.user.id ? 1 : 0)
                };

                updateStateChats(updatedChat);
                refreshSidebar();

                // Show Toast only if from others
                if (msg.senderId !== state.user.user.id) {
                    window.showCustomAlert(`New message from ${msg.senderName}`, 'info');
                }
            }
        });

    } catch (e) {
        console.error("Socket Init Failed", e);
    }
}

// Adaptive Polling Logic for Mobile Performance
window.adjustPolling = (specificContainer = null) => {
    if (pollingInterval) clearInterval(pollingInterval);
    
    const isSocketConnected = socket && socket.connected;
    const interval = isSocketConnected ? 30000 : 3000; // 30s if connected, 3s if disconnected
    
    console.log(`[Sync] Adjusting polling interval to ${interval}ms (Socket connected: ${isSocketConnected})`);

    pollingInterval = setInterval(() => {
        const container = specificContainer || document.getElementById('messages-container');
        pollMessages(container);
    }, interval);
};

// --- Notification Manager ---
window.initNotificationManager = async () => {
    if (!('serviceWorker' in navigator) || !('Notification' in window)) {
        console.warn("Notifications or Service Workers not supported.");
        return;
    }

    try {
        const registration = await navigator.serviceWorker.register('sw.js');
        console.log('Service Worker registered for notifications:', registration);

        if (Notification.permission === 'default') {
            await Notification.requestPermission();
        }

        // Listen for messages from SW
        navigator.serviceWorker.addEventListener('message', (event) => {
            if (event.data && event.data.type === 'CALL_ACTION') {
                console.log("Received Call Action from SW:", event.data);
                if (event.data.action === 'answer') {
                    window.answerCall();
                } else if (event.data.action === 'reject') {
                    window.rejectCall();
                }
            }
        });
    } catch (e) {
        console.error("SW Registration failed:", e);
    }
};

window.showCallNotification = async (callerName, avatar, type, callerId) => {
    if (Notification.permission !== 'granted') return;

    const registration = await navigator.serviceWorker.ready;
    const title = `Incoming ${type === 'video' ? 'Video' : 'Audio'} Call`;
    const options = {
        body: `${callerName} is calling you...`,
        icon: avatar || 'https://ui-avatars.com/api/?name=U',
        vibrate: [200, 100, 200, 100, 200, 100, 200],
        tag: 'call-notification',
        renotify: true,
        data: { callerId },
        actions: [
            { action: 'answer', title: 'Answer', icon: 'https://cdn-icons-png.flaticon.com/512/190/190411.png' },
            { action: 'reject', title: 'Reject', icon: 'https://cdn-icons-png.flaticon.com/512/753/753345.png' }
        ]
    };

    registration.showNotification(title, options);
};

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
    // Clear legacy status to prevent overlap
    const statusEl = document.getElementById('call-status');
    if (statusEl) {
        statusEl.textContent = '';
        statusEl.classList.add('hidden');
    }

    soundManager.play('calling'); // Start Calling Tone

    // UI Toggle
    const wrapper = document.querySelector('.video-wrapper');

    // Set Avatar for Placeholder (Video & Audio)
    const chat = state.chats.find(c => c.id === state.activeChatId);
    const targetAvatar = getAvatarUrl(chat || { name: 'User' });

    const placeholder = document.getElementById('video-placeholder');
    if (placeholder && type === 'video') {
        placeholder.classList.remove('hidden');
        placeholder.querySelector('.placeholder-text').textContent = "Calling...";
        const placeholderAvatar = document.getElementById('placeholder-avatar');
        if (placeholderAvatar) placeholderAvatar.src = targetAvatar;
    } else if (placeholder) {
        placeholder.classList.add('hidden');
    }

    if (type === 'audio') {
        wrapper.classList.add('audio-mode');
        // Set Audio Avatar 
        const avatarImg = document.getElementById('audio-avatar-img');
        if (avatarImg) {
            avatarImg.src = targetAvatar;
        }
    } else {
        wrapper.classList.remove('audio-mode');
    }

    // Toggle Button Icon (Video vs Speaker) & Camera Flip & Flashlight
    const toggleBtn = document.getElementById('btn-toggle-video');
    const flipBtn = document.getElementById('btn-flip-camera');
    const flashBtn = document.getElementById('btn-flashlight');
    if (toggleBtn) {
        if (type === 'audio') {
            toggleBtn.innerHTML = '<i class="fas fa-volume-up"></i>';
            toggleBtn.onclick = window.toggleSpeaker; // Set to speaker toggle
            if (flipBtn) flipBtn.style.display = 'none';
            if (flashBtn) flashBtn.style.display = 'none';
        } else {
            toggleBtn.innerHTML = '<i class="fas fa-video"></i>';
            toggleBtn.onclick = window.toggleVideo; // Set to video toggle
            if (flipBtn) flipBtn.style.display = 'flex'; // Show camera flip for video calls
            if (flashBtn) flashBtn.style.display = 'flex'; // Show flashlight for video calls
        }
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
    if (!chat || !chat.id) return '';
    if (chat.id === 'general') return 'Tap to view info';

    // Online status with green dot
    if (state.onlineUsers.has(chat.id)) {
        return '<span style="display:flex;align-items:center;gap:4px;"><span style="width:8px;height:8px;border-radius:50%;background:#22c55e;box-shadow:0 0 6px rgba(34,197,94,0.5);display:inline-block;"></span>Online</span>';
    }

    if (chat.type === 'group') {
        const memberCount = chat.members ? chat.members.length : 0;
        return `${memberCount} members`;
    }

    // Battery Status Logic
    let batteryHtml = '';
    if (chat.battery && chat.battery.level !== undefined) {
        const { level, charging } = chat.battery;
        let icon = 'empty';
        if (level > 90) icon = 'full';
        else if (level > 60) icon = 'three-quarters';
        else if (level > 30) icon = 'half';
        else if (level > 10) icon = 'quarter';

        batteryHtml = ` <span style="opacity:0.8; margin-left:8px;"> • <i class="fas fa-battery-${icon}"></i> ${level}%${charging ? ' <i class="fas fa-bolt" style="color:#f59e0b;"></i>' : ''}</span>`;
    }

    if (chat.lastSeen === 'online') return '<span style="display:flex;align-items:center;gap:4px;"><span style="width:8px;height:8px;border-radius:50%;background:#22c55e;box-shadow:0 0 6px rgba(34,197,94,0.5);display:inline-block;"></span>Online</span>' + batteryHtml;
    if (!chat.lastSeen) return '<span style="display:flex;align-items:center;gap:4px;"><span style="width:8px;height:8px;border-radius:50%;background:#94a3b8;display:inline-block;"></span>Offline</span>' + batteryHtml;

    return `Last seen ${timeAgo(chat.lastSeen)}` + batteryHtml;
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
    // 1. Dynamic Sidebar Update
    if (state.activeTab === 'messages' && window.refreshSidebar) {
        window.refreshSidebar();
    }

    // 2. Header Update
    if (state.activeChatId === userId) {
        const headerStatus = document.getElementById('header-status');
        if (headerStatus) {
            const newText = getHeaderStatusText({ id: userId });
            headerStatus.innerHTML = newText;
        }
    }
}

// Redundant savePrivacy removed (already defined at ~line 3360)



// Answer Call (Callee Side)
window.answerCall = async () => {
    soundManager.stop('ringtone'); // Stop Ringing
    if (navigator.vibrate) navigator.vibrate(0); // Stop Vibration immediately
    document.getElementById('incoming-call-popup').classList.add('hidden');
    document.getElementById('video-call-modal').classList.remove('hidden');
    // Clear legacy call status
    const statusEl = document.getElementById('call-status');
    if (statusEl) {
        statusEl.textContent = '';
        statusEl.classList.add('hidden');
    }

    // Reset Placeholder
    const placeholder = document.getElementById('video-placeholder');

    // Determine Type first
    const type = window.pendingCallType || 'video';
    const isVideo = type === 'video';

    if (placeholder && isVideo) {
        placeholder.classList.remove('hidden');
        placeholder.querySelector('.placeholder-text').textContent = "Connecting...";

        // Set Avatar from Caller Info
        const callerAvatarEl = document.getElementById('caller-avatar'); // From popup
        const placeholderAvatar = document.getElementById('placeholder-avatar');
        if (placeholderAvatar && callerAvatarEl && callerAvatarEl.src) {
            placeholderAvatar.src = callerAvatarEl.src;
        }
    } else if (placeholder) {
        placeholder.classList.add('hidden');
    }

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

    // Process Queued Candidates (Callee Side)
    while (window.iceCandidateQueue && window.iceCandidateQueue.length > 0) {
        const c = window.iceCandidateQueue.shift();
        peerConnection.addIceCandidate(c).catch(e => console.error("Queued ICE Error (Callee)", e));
    }

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
    if (navigator.vibrate) navigator.vibrate(0); // Stop Vibration immediately
    document.getElementById('incoming-call-popup').classList.add('hidden');
    socket.emit('end-call', { targetId: currentCallTargetId });

    // Log "Declined" (guard against duplicate from endCallCleanup)
    if (currentCallTargetId && !window._callLogSent) {
        window._callLogSent = true;
        api.sendMessage(`Declined`, 'call_log', currentCallTargetId).catch(console.error);
    }

    currentCallTargetId = null;
    window.pendingOffer = null;
    setTimeout(() => { window._callLogSent = false; }, 2000);
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

    // Stop all media tracks (Camera & Mic) — thorough cleanup
    if (localStream) {
        try {
            localStream.getTracks().forEach(track => {
                track.stop();
                track.enabled = false;
            });
        } catch (e) { console.error("Track stop error", e); }
        localStream = null;
    }

    // Clear Video Elements and force release
    const localVideo = document.getElementById('local-video');
    const remoteVideo = document.getElementById('remote-video');
    if (localVideo) {
        localVideo.srcObject = null;
        localVideo.load(); // Force browser to release camera handle
    }
    if (remoteVideo) {
        remoteVideo.srcObject = null;
        remoteVideo.load();
    }

    // Log the Call (skip if already logged by rejectCall)
    if (target && !window._callLogSent) {
        window._callLogSent = true;
        if (wasConnected && callSeconds > 0) {
            if (!isRemote) {
                const mins = Math.floor(callSeconds / 60).toString().padStart(2, '0');
                const secs = (callSeconds % 60).toString().padStart(2, '0');
                api.sendMessage(`Answered (${mins}:${secs})`, 'call_log', target).catch(console.error);
            }
        } else {
            if (!isRemote) {
                let logMessage = "";
                if (window.isCaller) {
                    logMessage = "No Answer";
                } else {
                    logMessage = "Declined";
                }
                if (logMessage) {
                    api.sendMessage(logMessage, 'call_log', target).catch(console.error);
                }
            }
        }
        setTimeout(() => { window._callLogSent = false; }, 2000);
    }

    currentCallTargetId = null;
    window.pendingOffer = null;
    stopCallTimer();
    wasConnected = false;
    window.isCaller = false;
    window.isCaller = false;
    window.iceCandidateQueue = []; // Clear Queue
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
}
// End toggleVideo

window.currentFacingMode = 'user'; // Default front camera

window.switchCamera = async () => {
    if (!localStream) return;

    // Toggle Mode
    const nextMode = (window.currentFacingMode === 'user') ? 'environment' : 'user';
    console.log("Switching camera to:", nextMode);

    // Get current video track
    const oldVideoTrack = localStream.getVideoTracks()[0];

    // STOP OLD TRACK FIRST (Fix for Android resource lock)
    if (oldVideoTrack) {
        oldVideoTrack.stop();
    }

    try {
        // Get new stream with new constraint
        const newStream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: { ideal: nextMode } }, // Use ideal for better compatibility
            audio: false
        });
        const newVideoTrack = newStream.getVideoTracks()[0];

        // Replace track in PeerConnection (Sender)
        if (peerConnection) {
            const sender = peerConnection.getSenders().find(s => s.track && s.track.kind === 'video');
            if (sender) {
                await sender.replaceTrack(newVideoTrack);
            }
        }

        // Replace track in Local Stream (keep audio)
        // Note: We already stopped the old one, so just removing it from object representation
        if (oldVideoTrack) localStream.removeTrack(oldVideoTrack);
        localStream.addTrack(newVideoTrack);

        // Update Local Video Element
        const localVideo = document.getElementById('local-video');
        localVideo.srcObject = null; // Clear first
        localVideo.srcObject = localStream;

        // Update State
        window.currentFacingMode = nextMode;

    } catch (e) {
        console.error("Camera switch failed:", e);
        alert("Could not switch camera. (Device might not have back camera or permission denied)");

        // Attempt to Revert (Restart previous camera)
        try {
            const revertStream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: window.currentFacingMode },
                audio: false
            });
            const revertTrack = revertStream.getVideoTracks()[0];
            if (oldVideoTrack) localStream.removeTrack(oldVideoTrack);
            localStream.addTrack(revertTrack);
            document.getElementById('local-video').srcObject = localStream;

            if (peerConnection) {
                const sender = peerConnection.getSenders().find(s => s.track && s.track.kind === 'video');
                if (sender) await sender.replaceTrack(revertTrack);
            }
        } catch (revertErr) {
            console.error("Critical: Could not revert camera:", revertErr);
            alert("Camera error. Please restart call.");
        }
    }
};

// --- Flashlight Toggle (Torch for mobile rear camera) ---
window._flashlightOn = false;
window.toggleFlashlight = async () => {
    if (!localStream) return;
    const videoTrack = localStream.getVideoTracks()[0];
    if (!videoTrack) return;

    try {
        const capabilities = videoTrack.getCapabilities ? videoTrack.getCapabilities() : {};
        if (!capabilities.torch) {
            alert('Flashlight not available on this device/camera.');
            return;
        }

        window._flashlightOn = !window._flashlightOn;
        await videoTrack.applyConstraints({ advanced: [{ torch: window._flashlightOn }] });

        // UI Feedback
        const btn = document.getElementById('btn-flashlight');
        if (btn) {
            if (window._flashlightOn) {
                btn.classList.add('active-control');
                btn.querySelector('i').style.color = '#f59e0b';
            } else {
                btn.classList.remove('active-control');
                btn.querySelector('i').style.color = '';
            }
        }
    } catch (e) {
        console.error('Flashlight toggle failed:', e);
    }
};

// --- Audio Speaker Toggle ---
window.toggleSpeaker = () => {
    // Visual update
    const btn = document.getElementById('btn-toggle-video');
    const icon = btn.querySelector('i');

    if (icon.classList.contains('fa-volume-up')) {
        icon.classList.remove('fa-volume-up');
        icon.classList.add('fa-volume-mute');
        console.log("Switched to Earpiece (Simulated)");
    } else {
        icon.classList.remove('fa-volume-mute');
        icon.classList.add('fa-volume-up');
        console.log("Switched to Speaker (Simulated)");
    }
    // Note: Actual hardware toggle requires native plugin or specific browser behaviors not available in standard Web API.
};

async function setupLocalMedia(videoEnabled = true) {
    try {
        // Use currentFacingMode for constraints
        const constraints = {
            video: videoEnabled ? { facingMode: { ideal: window.currentFacingMode || 'user' } } : false,
            audio: true
        };

        localStream = await navigator.mediaDevices.getUserMedia(constraints);

        // Show/Hide Flip Button based on device count
        const flipBtn = document.getElementById('btn-flip-camera');
        if (flipBtn) {
            if (videoEnabled) {
                try {
                    const devices = await navigator.mediaDevices.enumerateDevices();
                    const videoInputs = devices.filter(device => device.kind === 'videoinput');
                    flipBtn.style.display = (videoInputs.length > 1) ? 'flex' : 'none';
                } catch (err) {
                    console.warn("Could not enumerate devices:", err);
                    flipBtn.style.display = 'flex'; // Default to show if unsure
                }
            } else {
                flipBtn.style.display = 'none';
            }
        }

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

        const placeholder = document.getElementById('video-placeholder');
        if (placeholder) placeholder.classList.add('hidden');
    };
}

// Hook into Login
// Hook into Login
// (Combined into main loginUser function above)
// --- Diagnostics Logging ---
window.omaLogs = window.omaLogs || [];
window.logToDebug = (msg) => {
    const entry = `[${new Date().toLocaleTimeString()}] ${msg}`;
    window.omaLogs.push(entry);
    console.log(entry);
    const list = document.getElementById('dev-log-list');
    if (list) {
        list.insertAdjacentHTML('beforeend', `<div style="padding:4px 0; border-bottom:1px solid #333; font-size:0.75rem; font-family:monospace;">${entry}</div>`);
        list.scrollTop = list.scrollHeight;
    }
};

async function registerPush() {
    window.logToDebug("Push: registerPush() called");
    
    // Fuzzy Plugin Discovery
    const allPlugins = (window.Capacitor && window.Capacitor.Plugins) ? Object.keys(window.Capacitor.Plugins) : [];
    const findPlugin = (query) => {
        const key = allPlugins.find(k => k.toLowerCase() === query.toLowerCase());
        return key ? window.Capacitor.Plugins[key] : null;
    };

    let Push = PushNotifications || findPlugin('PushNotifications') || findPlugin('PushNotification');
    
    // Even if Keys are empty, check if register is a function (could be a Proxy)
    const hasRegister = !!(Push && typeof Push.register === 'function');
    
    const hasCap = !!window.Capacitor;
    const isNative = hasCap && window.Capacitor.isNativePlatform();
    window.logToDebug(`Push: isNative=${isNative}, DetectedPlugin=${!!Push}, HasRegister=${hasRegister}`);

    if (isNative && Push) {
        // If it's a proxy with hidden keys, try to call methods anyway
        window.logToDebug("Push: Attempting registration sequence...");
        // Individual Try/Catch blocks for resilience
        try {
            await Push.removeAllListeners();
            window.logToDebug("Push: Listeners cleared.");
        } catch (e) { window.logToDebug("Push: removeAllListeners skipped: " + e.message); }

        try {
            await Push.setPresentationOptions({
                presentationOptions: ['badge', 'sound', 'alert'],
            });
            window.logToDebug("Push: Presentation options set.");
        } catch (e) { window.logToDebug("Push: setPresentationOptions failed: " + e.message); }

        try {
            await Push.createChannel({
                id: 'call_channel',
                name: 'Call Notifications',
                importance: 5,
                visibility: 1,
                sound: 'calling.mp3',
                vibration: true
            });
            await Push.createChannel({
                id: 'message_channel',
                name: 'Message Notifications',
                importance: 5,
                visibility: 1,
                sound: 'message.mp3',
                vibration: true
            });
            window.logToDebug("Push: Channels created.");
        } catch (e) { window.logToDebug("Push: createChannel failed: " + e.message); }

        // Setup Result Listeners
        try {
            await Push.addListener('registration', async ({ value }) => {
                window.logToDebug('Push Token Received: ' + value.substring(0, 15) + '...'); 
                localStorage.setItem('oma_push_token', value);
                try {
                    await api.updatePushToken(value);
                    window.logToDebug('Push Token synced to server');
                } catch (e) { window.logToDebug('Failed to sync token: ' + e.message); }
            });

            await Push.addListener('registrationError', (error) => {
                window.logToDebug('Push: Registration Error: ' + JSON.stringify(error, Object.getOwnPropertyNames(error)));
                alert('Push ERROR: ' + (error.message || JSON.stringify(error)));
            });

            await Push.addListener('pushNotificationReceived', async (notification) => {
                window.logToDebug('Push Received: ' + (notification.title || "Message"));
                const data = notification.data || (notification.notification ? notification.notification.data : null);

                // CROSS-PLATFORM FOREGROUND BANNERS:
                // Android doesn't show banners natively when app is in foreground.
                // We use LocalNotifications plugin to "relay" the push into a visible banner.
                try {
                    const Loc = window.Capacitor.Plugins.LocalNotifications;
                    if (Loc) {
                        await Loc.schedule({
                            notifications: [{
                                title: notification.title || "New Message",
                                body: notification.body || "You have a new message",
                                id: Math.floor(Date.now() / 1000),
                                schedule: { at: new Date(Date.now() + 100) }, // basically now
                                extra: data,
                                channelId: 'message_channel',
                                smallIcon: 'ic_stat_name' // or similar
                            }]
                        });
                        window.logToDebug('Push: Foreground banner scheduled.');
                    }
                } catch (e) { window.logToDebug('Push: Local relay failed: ' + e.message); }

                if (data?.type === 'call_offer') {
                    if (window.handleIncomingCall) window.handleIncomingCall(data);
                    return;
                }
                if (state.activeChatId !== data?.chatId) soundManager.play('message');
            });
            window.logToDebug("Push: Listeners attached.");
        } catch (e) { window.logToDebug("Push: addListener failed: " + e.message); }

        // Final Permission & Registration
        try {
            window.logToDebug("Push: Checking permissions..."); 
            let permStatus = await Push.checkPermissions();
            if (permStatus.receive === 'prompt') {
                window.logToDebug("Push: Requesting permissions...");
                permStatus = await Push.requestPermissions();
            }

            if (permStatus.receive === 'granted') {
                window.logToDebug("Push: Calling register()...");
                await Push.register();
                window.logToDebug("Push: register() called. Awaiting token...");

                // Also Ensure LocalNotifications permit for foreground banners
                try {
                    const Loc = window.Capacitor.Plugins.LocalNotifications;
                    if (Loc) await Loc.requestPermissions();
                } catch (pe) { window.logToDebug("Push: Loc perm request failed: " + pe.message); }

            } else {
                window.logToDebug("Push: Permission denied (" + permStatus.receive + ")");
            }
        } catch (e) {
            window.logToDebug("Push: Final Stage Failure: " + e.message);
        }
    } else {
        if (isNative && (!Push || !Push.register)) {
            window.logToDebug("Push: CRITICAL - Plugin object found but register() method is missing.");
        }
        window.logToDebug("Push: Skipping registration.");
    }
}

window.checkPluginStatus = () => {
    try {
        const allPlugins = (window.Capacitor && window.Capacitor.Plugins) ? Object.keys(window.Capacitor.Plugins) : [];
        const Push = (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.PushNotifications) || {};
        
        const status = {
            keys: Object.keys(Push),
            type: typeof Push,
            hasRegister: typeof Push.register,
            hasAddListener: typeof Push.addListener,
            allInRegistry: allPlugins
        };
            
        window.logToDebug("FULL PLUGIN STATUS: " + JSON.stringify(status));
        alert(`PLUGINS: ${allPlugins.join(', ')}\n\nPush Plugin Info:\n- Type: ${status.type}\n- keys: [${status.keys.join(',')}]\n- .register: ${status.hasRegister}\n- .addListener: ${status.hasAddListener}`);
    } catch (e) {
        window.logToDebug("Check failed: " + e.message);
        alert("Check failed: " + e.message);
    }
};


// Expose checks and function for manual debugging
window.registerPush = registerPush;
window.checkCapacitor = () => {
    alert(`Capacitor: ${!!window.Capacitor}\nNative: ${window.Capacitor ? window.Capacitor.isNativePlatform() : 'N/A'}`);
};


// --- Diagnostics & Debug Tool (Global Scope) ---
window.showDiagnostics = () => {
    const userId = state.user?.user?.id || 'Not Logged In';
    const apiBase = api.getApiBase();
    const pushToken = localStorage.getItem('oma_push_token') || 'None';
    const logsHtml = window.omaLogs.map(l => `<div style="padding:4px 0; border-bottom:1px solid #333; font-size:0.75rem; font-family:monospace;">${l}</div>`).join('');
    
    const menu = `
        <div id="dev-menu-modal" style="position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.95); z-index:20000; color:white; padding:20px; box-sizing:border-box; overflow-y:auto; font-family: 'Inter', sans-serif; backdrop-filter: blur(10px);">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
                <h2 style="color:#3b82f6; margin:0;">OMA Diagnostics</h2>
                <button onclick="document.getElementById('dev-menu-modal').remove()" style="background:none; border:none; color:white; font-size:1.5rem;"><i class="fas fa-times"></i></button>
            </div>

            <div style="background:#1e1e1e; padding:15px; border-radius:12px; margin-bottom:20px; border:1px solid #333;">
                <p style="margin-bottom:8px; font-size:0.9rem;"><b>User ID:</b> <span style="opacity:0.8; float:right;">${userId}</span></p>
                <p style="margin-bottom:8px; font-size:0.9rem;"><b>API Base:</b> <span style="opacity:0.8; float:right;">${apiBase}</span></p>
                <div style="margin-top:10px;">
                    <b>Push Token:</b>
                    <div style="font-size:0.7rem; color:#10b981; word-break:break-all; background:#000; padding:10px; border-radius:8px; margin-top:5px; border:1px solid #222;">${pushToken}</div>
                </div>
            </div>

            <div style="margin-bottom:20px;">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
                    <h4 style="margin:0; opacity:0.7;">Event Logs</h4>
                    <button onclick="window.omaLogs=[]; document.getElementById('dev-log-list').innerHTML=''" style="background:none; border:none; color:#3b82f6; font-size:0.8rem; cursor:pointer;">Clear Logs</button>
                </div>
                <div id="dev-log-list" style="height:200px; background:#000; border-radius:12px; border:1px solid #333; padding:10px; overflow-y:auto; color:#aaa;">
                    ${logsHtml || '<div style="opacity:0.4; text-align:center; padding-top:80px;">No logs yet...</div>'}
                </div>
            </div>
            
            <div style="display:grid; grid-template-columns: 1fr 1fr; gap:10px;">
                <button onclick="window.setDevIp()" style="padding:15px; border-radius:10px; background:#333; color:white; border:none; font-weight:600; font-size:0.85rem;">Set API IP</button>
                <button onclick="window.forcePushRegister()" style="padding:15px; border-radius:10px; background:#333; color:white; border:none; font-weight:600; font-size:0.85rem;">Refresh Push</button>
                <button onclick="window.checkPluginStatus()" style="grid-column: span 2; padding:15px; border-radius:10px; background:#111; color:#aaa; border:1px solid #333; font-weight:600; font-size:0.85rem; margin-top:5px;">Check Plugin Status</button>
                <button onclick="window.sendDiagnosticPush()" style="grid-column: span 2; padding:15px; border-radius:10px; background:#3b82f6; color:white; border:none; font-weight:600; font-size:0.9rem; margin-top:5px;">Send Test Notification</button>
            </div>

            <p style="margin-top:30px; font-size:0.75rem; opacity:0.4; text-align:center;">OMA Engineering v1.1.0 • Ready</p>
        </div>
    `;
    document.body.insertAdjacentHTML('beforeend', menu);
    // Scroll logs to bottom
    const list = document.getElementById('dev-log-list');
    if (list) list.scrollTop = list.scrollHeight;
};

window.handleLogoClick = () => {
    window.logoClicks = (window.logoClicks || 0) + 1;
    if (window.logoClicks >= 5) {
        window.logoClicks = 0;
        window.showDiagnostics();
    } else {
        clearTimeout(window.logoClickTimeout);
        window.logoClickTimeout = setTimeout(() => { window.logoClicks = 0; }, 2000);
    }
};

window.setDevIp = () => {
    const current = localStorage.getItem('oma_dev_ip') || "";
    const ip = prompt("Dev Mode: Set Backend IP (e.g. http://192.168.1.10:5000)", current);
    if (ip !== null) {
        localStorage.setItem('oma_dev_ip', ip);
        location.reload();
    }
};

window.forcePushRegister = async () => {
    if (window.registerPush) {
        alert("Triggering Push Registration...");
        await window.registerPush();
        alert("Done! Check Push Token above.");
        location.reload();
    }
};

window.sendDiagnosticPush = async () => {
    try {
        alert("Requesting backend to send push...");
        const res = await api.sendTestNotification();
        alert("Success! Check your notification tray.");
    } catch (e) {
        alert("Failed: " + e.message);
    }
};

// Ensure init is called
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

window.setWallpaper = (type) => {
    localStorage.setItem('oma_wallpaper', type);
    const bg = document.getElementById('app-wallpaper-bg');
    if (bg) {
        document.body.classList.remove('global-wallpaper-active');
        bg.className = '';
        bg.innerHTML = '';
        if (type !== 'default') {
            document.body.classList.add('global-wallpaper-active');
            bg.classList.add(`wallpaper-${type}`);
            window.applyWallpaperElements(type);
        }
    }
    if (state.settingsView === 'appearance') {
        const sidebar = document.getElementById('sidebar');
        // FIX: Re-render specific appearance settings, not the main list
        if (sidebar) sidebar.innerHTML = renderSettingsAppearance();
    }
};

// --- Battery Service ---
window.batteryService = {
    battery: null,
    interval: null,
    lastSent: null
};

window.initBatteryService = async () => {
    if (!navigator.getBattery) {
        console.log("Battery API not supported");
        return;
    }

    try {
        const battery = await navigator.getBattery();
        window.batteryService.battery = battery;

        // Listeners
        battery.addEventListener('levelchange', () => window.checkAndSendBattery());
        battery.addEventListener('chargingchange', () => window.checkAndSendBattery());

        // Generic Interval (3 mins) - Only if active
        if (window.batteryService.interval) clearInterval(window.batteryService.interval);
        window.batteryService.interval = setInterval(() => {
            if (document.visibilityState === 'visible') {
                window.checkAndSendBattery(true); // Force update every 10 seconds
            }
        }, 10000);

        // Visibility Change
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                window.checkAndSendBattery(true);
            }
        });

        // Initial check
        window.checkAndSendBattery();

    } catch (e) {
        console.error("Battery Init Failed", e);
    }
};

window.checkAndSendBattery = async (force = false) => {
    // Default ON: Share unless explicitly 'false'
    const shouldShare = localStorage.getItem('oma_share_battery') !== 'false';
    if (!shouldShare) return;

    if (!window.batteryService.battery) return;

    const { level, charging } = window.batteryService.battery;
    const levelPercent = Math.round(level * 100);

    // De-bounce: Update if level changed > 1% OR charging changed OR Forced
    const last = window.batteryService.lastSent;
    const shouldUpdate = force || !last || last.charging !== charging || Math.abs(last.level - levelPercent) >= 1;

    if (shouldUpdate) {
        try {
            await api.updateProfile({ battery: { level: levelPercent, charging, timestamp: Date.now() } });
            window.batteryService.lastSent = { level: levelPercent, charging };
            console.log("Battery Status Sent:", levelPercent + "%", charging ? "Charging" : "");
        } catch (e) {
            console.error("Failed to send battery status", e);
        }
    }
};

window.toggleBatteryShare = (input) => {
    const enabled = input.checked;
    localStorage.setItem('oma_share_battery', enabled);
    if (enabled) {
        window.initBatteryService();
        window.checkAndSendBattery(true);
    } else {
        // Clear from server? Maybe send null? 
        // For now, just stop sending.
        // Ideally we send 'null' to clear it for others.
        api.updateProfile({ battery: null }).catch(console.error);
    }
};


window.applyWallpaperElements = (type) => {
    const bg = document.getElementById('app-wallpaper-bg');
    if (!bg) return;
    bg.innerHTML = '';
    bg.style.pointerEvents = 'none';

    const isMobile = window.innerWidth < 768;

    if (type === 'bubble') {
        const bubbleContainer = document.createElement('div');
        bubbleContainer.id = 'stars'; // Reusing container id for simplicity
        bubbleContainer.style.pointerEvents = 'none';
        bg.appendChild(bubbleContainer);

        const count = isMobile ? 12 : 40;
        for (let i = 0; i < count; i++) {
            const bubble = document.createElement('div');
            bubble.className = 'bubble-sphere';
            bubble.style.pointerEvents = 'none';
            const size = 15 + Math.random() * 35; // Size between 15px and 50px
            bubble.style.width = `${size}px`;
            bubble.style.height = `${size}px`;
            bubble.style.left = `${Math.random() * 100}%`;
            bubble.style.bottom = `-50px`;
            bubble.style.animationDuration = `${10 + Math.random() * 20}s`;
            bubble.style.animationDelay = `${Math.random() * -30}s`;
            bubble.style.opacity = 0.3 + Math.random() * 0.4;
            bubbleContainer.appendChild(bubble);
        }
    } else if (type === 'japan') {
        const chars = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789";
        const count = isMobile ? 40 : 150;
        for (let i = 0; i < count; i++) {
            const span = document.createElement('span');
            span.style.pointerEvents = 'none';
            span.innerText = chars[Math.floor(Math.random() * chars.length)];
            bg.appendChild(span);
        }
    } else if (type === 'japan-matrix') {
        const chars = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789";
        const count = isMobile ? 20 : 80;
        for (let i = 0; i < count; i++) {
            const span = document.createElement('span');
            span.style.pointerEvents = 'none';
            span.innerText = chars[Math.floor(Math.random() * chars.length)];
            span.style.left = `${Math.random() * 100}%`;
            span.style.animationDuration = `${5 + Math.random() * 8}s`;
            span.style.animationDelay = `${Math.random() * -10}s`;
            bg.appendChild(span);
        }
    } else if (type === 'matrix') {
        // Professional CSS Column approach
        const count = isMobile ? 15 : 40;
        for (let i = 0; i < count; i++) {
            const col = document.createElement('div');
            col.className = 'matrix-column';
            col.style.pointerEvents = 'none';
            bg.appendChild(col);
        }
    }
};

// --- User Profile Modal ---
// --- User Profile Modal ---
window.closeUserProfile = () => {
    const modal = document.getElementById('user-profile-modal');
    if (modal) {
        const content = modal.querySelector('.modal-content');
        if (content) {
            content.classList.remove('animate__zoomIn');
            content.classList.add('animate__zoomOut');
        }
        modal.classList.remove('animate__fadeIn');
        modal.classList.add('animate__fadeOut');
        setTimeout(() => {
            modal.remove();
        }, 400);
    }
};

window.saveProfile = async () => {
    const nameInput = document.getElementById('settings-name');
    const bioInput = document.getElementById('settings-bio');

    const name = nameInput ? nameInput.value.trim() : '';
    const bio = bioInput ? bioInput.value.trim() : '';

    if (!name) return showCustomAlert('Name cannot be empty', 'error');

    try {
        const updated = await api.updateProfile({ name, bio });
        // Update local state
        state.user.user = { ...state.user.user, ...updated };
        localStorage.setItem('oma_user', JSON.stringify(state.user));

        showCustomAlert('Profile updated successfully!');

        // Refresh view
        render();
    } catch (e) {
        console.error("Save Profile Failed", e);
        showCustomAlert('Failed to update profile', 'error');
    }
};

// --- Status Carousel ---
window.startStatusCarousel = (chat) => {
    // Clear previous
    if (state.statusInterval) clearInterval(state.statusInterval);
    const headerStatus = document.getElementById('header-status');
    if (!headerStatus) return;

    // Initial Render
    let standardText = getHeaderStatusText(chat, false);
    headerStatus.innerHTML = `<div class="status-text-container"><span class="status-slide" id="status-slide-1">${standardText}</span></div>`;

    let timer = 0;
    let showBattery = false;

    const update = () => {
        const container = headerStatus.querySelector('.status-text-container');
        if (!container) return;

        // Fetch fresh chat object from state to get latest battery/status
        const freshChat = state.chats.find(c => c.id === chat.id) || chat;
        standardText = getHeaderStatusText(freshChat, false);

        let content = standardText;

        if (showBattery && freshChat.battery && freshChat.battery.level) {
            const { level, charging } = freshChat.battery;
            let icon = 'empty';
            if (level > 90) icon = 'full';
            else if (level > 60) icon = 'three-quarters';
            else if (level > 30) icon = 'half';
            else if (level > 10) icon = 'quarter';

            content = `<span style="color:#10b981; display:flex; align-items:center; gap:5px;"><i class="fas fa-battery-${icon}"></i> ${level}%${charging ? ' <i class="fas fa-bolt" style="color:#f59e0b;"></i>' : ''}</span>`;
        }

        container.innerHTML = `<span class="status-slide status-slide-up">${content}</span>`;
    };

    const cycle = () => {
        timer++;
        // 0-10s: Status. 10s: Switch to Battery. 15s: Switch to Status.
        if (timer === 10) {
            // Only switch to battery if we have data
            const freshChat = state.chats.find(c => c.id === chat.id) || chat;
            if (freshChat.battery && freshChat.battery.level) {
                showBattery = true;
                update();
            } else {
                timer = 0; // Reset if no battery, keep showing status
                // Optional: Update status text in case it changed (Online -> Last seen)
                update();
            }
        } else if (timer === 15) {
            showBattery = false;
            update();
            timer = 0;
        }
    };

    state.statusInterval = setInterval(cycle, 1000);
};

// Override typing handler to use Bubble
window.handleTypingVisuals = (userId, isTyping) => {
    if (userId !== state.activeChatId) return;

    const container = document.getElementById('messages-container');
    const existing = document.getElementById('typing-indicator-bubble');

    if (isTyping) {
        if (!existing) {
            const bubble = document.createElement('div');
            bubble.id = 'typing-indicator-bubble';
            bubble.className = 'typing-bubble animate__animated animate__fadeInUp';
            bubble.innerHTML = `
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
    `;
            container.appendChild(bubble);
            container.scrollTop = container.scrollHeight;
        }
    } else {
        if (existing) existing.remove();
    }
};

// --- Chat UI Toggles ---
window.toggleChatMenu = () => {
    const menu = document.getElementById('chat-menu-dropdown');
    if (menu) menu.classList.toggle('hidden');
};

window.toggleChatSearch = () => {
    const bar = document.getElementById('chat-search-bar');
    const actions = document.getElementById('chat-actions-default');
    const input = document.getElementById('chat-search-input');

    if (bar && actions) {
        if (bar.style.display === 'none') {
            bar.style.display = 'flex';
            actions.style.display = 'none';
            if (input) input.focus();
        } else {
            bar.style.display = 'none';
            actions.style.display = 'flex';
            if (input) input.value = '';
            // Restore messages
            const container = document.getElementById('messages-container');
            if (container) {
                const messages = container.querySelectorAll('.message-wrapper');
                messages.forEach(m => m.style.display = 'flex');
            }
        }
    }
};

window.filterChatMessages = (val) => {
    const container = document.getElementById('messages-container');
    if (!container) return;
    const messages = container.querySelectorAll('.message-wrapper');
    const query = val.toLowerCase();

    messages.forEach(msg => {
        const textEl = msg.querySelector('.message-content p');
        if (textEl) {
            const text = textEl.innerText.toLowerCase();
            msg.style.display = text.includes(query) ? 'flex' : 'none';
        }
    });
};

// Close Menus on Click Outside (Touch & Click)
const closeMenuHandler = (e) => {
    const menu = document.getElementById('chat-menu-dropdown');
    const attachmentinfo = document.getElementById('attachment-menu');
    const btn = document.getElementById('btn-chat-menu');
    const msgModal = document.getElementById('message-options-modal');

    // Close Chat Menu
    if (menu && !menu.classList.contains('hidden')) {
        if (!menu.contains(e.target) && !btn.contains(e.target)) {
            menu.classList.add('hidden');
        }
    }

    // Close Attachment Menu
    if (attachmentinfo && !attachmentinfo.classList.contains('hidden')) {
        if (!attachmentinfo.contains(e.target) && !e.target.closest('#attachment-btn') && !e.target.closest('.menu-icon')) {
            attachmentinfo.classList.add('hidden');
        }
    }

    // Close Message Options Modal (if clicking outside content)
    if (msgModal && !msgModal.classList.contains('hidden') && msgModal.style.display !== 'none') {
        const content = msgModal.querySelector('.modal-content');
        if (content && !content.contains(e.target)) {
            window.closeMessageOptions();
        }
    }
};

// Remove existing listeners if any (to prevent duplication on HMR or re-init if this runs multiple times)
// But essentially just add them once.
document.removeEventListener('click', closeMenuHandler);
document.removeEventListener('touchstart', closeMenuHandler);
document.addEventListener('click', closeMenuHandler);
document.addEventListener('touchstart', closeMenuHandler, { passive: false });

window.openUserProfile = async (userId) => {
    console.log("Opening Profile for:", userId);
    if (!userId) return;
    if (userId === 'general') {
        window.openGroupInfo();
        return;
    }

    // Find user data
    let user = state.chats.find(c => c.id === userId) || state.searchResults.find(c => c.id === userId);

    // If we have an active chat context, use that
    // Always try to fetch fresh data to ensure bio/phone/battery are up to date
    try {
        const fresh = await api.batchGetUsers([userId]);
        console.log("Fresh Profile Data:", fresh);
        if (fresh && fresh.length > 0) {
            user = { ...(user || {}), ...fresh[0] };
        }
    } catch (e) {
        console.error("Failed to fetch fresh profile", e);
    }

    if (!user) return;

    const isMe = userId === state.user.user.id;
    const isBlocked = state.user.user.blockedUsers?.includes(userId);

    const modalHtml = `
        <div id="user-profile-modal" class="modal-overlay animate__animated animate__fadeIn" style="display:flex; background:rgba(0,0,0,0.7); backdrop-filter:blur(8px); -webkit-backdrop-filter:blur(8px);">
            <div class="modal-content animate__animated animate__zoomIn" style="width:92%; max-width:420px; padding:0; overflow:hidden; display:flex; flex-direction:column; max-height:85vh; border-radius:32px; border:1px solid rgba(255,255,255,0.1); box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5);">
                
                <!-- Header Image/Avatar Section -->
                <div style="height:280px; background:var(--sidebar-bg); position:relative; overflow:hidden;">
                   <img src="${getAvatarUrl(user)}" 
                        id="profile-large-avatar"
                        onerror="window.handleImageError(this, '${(user.name || user.username || 'User').replace(/'/g, "\\'")}')"
                        style="width:100%; height:100%; object-fit:cover; cursor:pointer; transition:transform 0.5s ease;" 
                        onclick="window.openMediaViewer(this.src, 'image')"
                        onmouseover="this.style.transform='scale(1.05)'"
                        onmouseout="this.style.transform='scale(1)'">
                   
                   <!-- Gradient Overlay -->
                   <div style="position:absolute; bottom:0; left:0; width:100%; height:120px; background:linear-gradient(to top, rgba(0,0,0,0.8), transparent); pointer-events:none;"></div>
                   
                   <!-- Back Button -->
                   <button class="icon-btn" onclick="window.closeUserProfile()" style="position:absolute; top:20px; left:20px; background:rgba(0,0,0,0.4); backdrop-filter:blur(10px); color:white; border-radius:50%; width:44px; height:44px; border:1px solid rgba(255,255,255,0.2);"><i class="fas fa-arrow-left"></i></button>
                   
                   <!-- User Basic Info Overlaid on Image Bottom -->
                   <div style="position:absolute; bottom:20px; left:25px; right:25px; color:white; text-shadow:0 2px 4px rgba(0,0,0,0.5);">
                        <h2 style="margin:0; font-size:1.8rem; font-weight:800; letter-spacing:-0.5px;">${user.name}</h2>
                        <div style="display:flex; align-items:center; gap:8px; opacity:0.9; margin-top:4px;">
                            <span style="font-size:1rem;">@${user.username || 'unknown'}</span>
                            ${user.battery ? `
                                <span style="background:rgba(16, 185, 129, 0.2); color:#10b981; padding:2px 10px; border-radius:20px; font-size:0.75rem; border:1px solid rgba(16, 185, 129, 0.3); backdrop-filter:blur(5px);">
                                    <i class="fas fa-battery-${user.battery.level > 90 ? 'full' : user.battery.level > 50 ? 'half' : 'quarter'}" style="margin-right:4px;"></i>${user.battery.level}%
                                </span>
                            ` : ''}
                        </div>
                   </div>
                </div>

                <div style="padding:25px; flex:1; overflow-y:auto; background:var(--bg-color);">
                    
                    <!-- Action Buttons -->
                    ${!isMe ? `
                        <div style="display:grid; grid-template-columns:repeat(3, 1fr); gap:15px; margin-bottom:30px;">
                            <div onclick="window.closeUserProfile(); window.openChat('${user.id}')" class="profile-action-btn" style="display:flex; flex-direction:column; align-items:center; gap:8px; cursor:pointer; background:var(--sidebar-bg); padding:15px 10px; border-radius:20px; border:1px solid var(--border-color); transition:all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);">
                                <div style="width:46px; height:46px; border-radius:50%; background:rgba(79, 70, 229, 0.1); color:var(--primary-color); display:flex; align-items:center; justify-content:center; font-size:1.3rem;"><i class="fas fa-comment"></i></div>
                                <span style="font-size:0.8rem; font-weight:700; color:var(--text-secondary);">Message</span>
                            </div>
                            <div onclick="window.startCall('audio')" class="profile-action-btn" style="display:flex; flex-direction:column; align-items:center; gap:8px; cursor:pointer; background:var(--sidebar-bg); padding:15px 10px; border-radius:20px; border:1px solid var(--border-color); transition:all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);">
                                <div style="width:46px; height:46px; border-radius:50%; background:rgba(16, 185, 129, 0.1); color:#10b981; display:flex; align-items:center; justify-content:center; font-size:1.3rem;"><i class="fas fa-phone-alt"></i></div>
                                <span style="font-size:0.8rem; font-weight:700; color:var(--text-secondary);">Audio</span>
                            </div>
                            <div onclick="window.startCall('video')" class="profile-action-btn" style="display:flex; flex-direction:column; align-items:center; gap:8px; cursor:pointer; background:var(--sidebar-bg); padding:15px 10px; border-radius:20px; border:1px solid var(--border-color); transition:all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);">
                                <div style="width:46px; height:46px; border-radius:50%; background:rgba(244, 63, 94, 0.1); color:#f43f5e; display:flex; align-items:center; justify-content:center; font-size:1.3rem;"><i class="fas fa-video"></i></div>
                                <span style="font-size:0.8rem; font-weight:700; color:var(--text-secondary);">Video</span>
                            </div>
                        </div>
                    ` : ''}

                    <!-- Contact Details Card -->
                    <div style="background:var(--sidebar-bg); border-radius:24px; padding:20px; margin-bottom:24px; border:1px solid var(--border-color); box-shadow:var(--shadow-sm);">
                        <div style="margin-bottom:20px;">
                            <h4 style="margin:0 0 8px 0; color:var(--text-secondary); font-size:0.8rem; text-transform:uppercase; letter-spacing:1px; font-weight:700;">About</h4>
                            <p style="margin:0; font-size:1rem; color:var(--text-primary); line-height:1.6;">${user.bio || 'No bio available.'}</p>
                        </div>
                        <div style="padding-top:15px; border-top:1px solid var(--border-color); display:flex; align-items:center; gap:12px;">
                            <div style="width:40px; height:40px; border-radius:12px; background:rgba(var(--primary-color-rgb), 0.1); color:var(--primary-color); display:flex; align-items:center; justify-content:center; font-size:1rem;"><i class="fas fa-phone"></i></div>
                            <div>
                                <p style="margin:0; font-size:0.75rem; color:var(--text-secondary); font-weight:600;">Phone Number</p>
                                <p style="margin:2px 0 0 0; font-size:1rem; color:var(--text-primary); font-weight:500;">${user.phone || 'Private'}</p>
                            </div>
                        </div>
                    </div>

                    <!-- Danger Zone Section -->
                    ${!isMe ? `
                        <div style="background:var(--sidebar-bg); border-radius:24px; overflow:hidden; border:1px solid var(--border-color); box-shadow:var(--shadow-sm);">
                             <div onclick="window.blockCurrentUser('${user.id}')" style="padding:18px 20px; display:flex; align-items:center; gap:15px; cursor:pointer; border-bottom:1px solid var(--border-color); transition:background 0.2s;">
                                <div style="width:36px; height:36px; border-radius:10px; background:rgba(239, 68, 68, 0.1); color:#ef4444; display:flex; align-items:center; justify-content:center;"><i class="fas fa-ban"></i></div>
                                <span style="color:#ef4444; font-weight:700; flex:1;">${isBlocked ? 'Unblock User' : 'Block Current User'}</span>
                                <i class="fas fa-chevron-right" style="font-size:0.8rem; opacity:0.3;"></i>
                            </div>
                            <div onclick="window.reportCurrentUser('${user.id}')" style="padding:18px 20px; display:flex; align-items:center; gap:15px; cursor:pointer; transition:background 0.2s;">
                                <div style="width:36px; height:36px; border-radius:10px; background:rgba(245, 158, 11, 0.1); color:#f59e0b; display:flex; align-items:center; justify-content:center;"><i class="fas fa-flag"></i></div>
                                <span style="color:#f59e0b; font-weight:700; flex:1;">Report Activity</span>
                                <i class="fas fa-chevron-right" style="font-size:0.8rem; opacity:0.3;"></i>
                            </div>
                        </div>
                    ` : ''}
                    
                    <div style="text-align:center; padding: 25px 0 10px; color:var(--text-secondary); font-size:0.75rem; opacity:0.6;">
                        Secure Peer Connection &bull; OMA v2.8.5
                    </div>
                </div>
            </div>
        </div>
    `;

    const div = document.createElement('div');
    div.innerHTML = modalHtml;
    document.body.appendChild(div);
};


// Initialize theme and wallpaper on load
(function () {
    const theme = localStorage.getItem('oma_theme');
    if (theme === 'dark') document.body.classList.add('dark-mode');

    const initWallpaper = () => {
        const wallpaper = localStorage.getItem('oma_wallpaper');
        if (wallpaper && wallpaper !== 'default') {
            const checkExist = setInterval(() => {
                const bg = document.getElementById('app-wallpaper-bg');
                if (bg) {
                    document.body.classList.add('global-wallpaper-active');
                    bg.classList.add(`wallpaper-${wallpaper}`);
                    window.applyWallpaperElements(wallpaper);
                    clearInterval(checkExist);
                }
            }, 100);
        }
    };
    initWallpaper();
})();
console.log("App.js Loaded Successfully (v6)");
console.log("App.js Loaded Successfully (v6)");


