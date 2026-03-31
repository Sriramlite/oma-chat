require('dotenv').config();
// Fix for Node.js v24 DNS bug on Windows 10
require('dns').setServers(['8.8.8.8', '8.8.4.4']);
const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const { connectToDatabase, setupIndexes } = require('./db');
const { sendPushNotification } = require('./firebase');
const rateLimit = require('express-rate-limit');

// --- Admin Status Notifications ---
async function notifyAdmin(title, body) {
    try {
        const db = await connectToDatabase();
        if (!db) return;
        // Notify the primary user or any user with a push token to stay updated
        // Find all users with tokens, but only take unique tokens to prevent double-pings
        const users = await db.collection('users').find({ pushToken: { $exists: true } }).limit(10).toArray();
        const uniqueTokens = [...new Set(users.map(u => u.pushToken))].slice(0, 5);

        for (const token of uniqueTokens) {
            try {
                await sendPushNotification(token, title, body, { type: 'server_status' }, {
                    android: {
                        priority: 'high',
                        notification: {
                            channelId: 'message_channel',
                            priority: 'max',
                            visibility: 'public'
                        }
                    }
                }, db);
            } catch (innerErr) {
                // Silently move to the next admin (bad tokens are already pruned by sendPushNotification)
                console.log(`[Server] Skipped notify for admin: ${innerErr.message}`);
            }
        }
    } catch (e) {
        console.warn("[Server] Failed to notify admin:", e.message);
    }
}

const app = express();
const PORT = process.env.PORT || 3000;

// Rate Limiters
const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 10, // Limit each IP to 10 requests per window
    message: { error: "Too many attempts from this IP, please try again after 15 minutes" },
    standardHeaders: true,
    legacyHeaders: false,
});

// Middleware
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Apply rate limiting to specific auth routes
app.use('/api/auth/login', authLimiter);
app.use('/api/auth/signup', authLimiter);
app.use('/api/auth/forgot-password', authLimiter);

// Serve static files from public directory
app.use(express.static(path.join(__dirname, '../../public')));

// Dynamic Route Handler for Vercel-like functions
console.log('Initializing API routes...');
try {
    app.use('/api', async (req, res) => {
        try {
            let relativePath = req.path;
            if (relativePath.startsWith('/')) {
                relativePath = relativePath.slice(1);
            }
            if (!relativePath) {
                return res.status(404).json({ error: 'Not Found' });
            }

            const apiDir = path.join(__dirname, '..');
            let modulePath = path.join(apiDir, relativePath);

            if (fs.existsSync(modulePath + '.js')) {
                modulePath = modulePath + '.js';
            } else if (fs.existsSync(path.join(modulePath, 'index.js'))) {
                modulePath = path.join(modulePath, 'index.js');
            } else {
                return res.status(404).json({ error: 'API route not found' });
            }

            try {
                const resolvedPath = require.resolve(modulePath);
                delete require.cache[resolvedPath];
            } catch (e) { }

            const handler = require(modulePath);

            if (typeof handler === 'function') {
                await handler(req, res);
            } else {
                res.status(500).json({ error: 'Invalid API handler' });
            }

        } catch (error) {
            console.error('API Execution Error:', error);
            res.status(500).json({ error: 'Internal Server Error' });
        }
    });
} catch (err) {
    console.error("Critical Error registering /api route:", err);
}

const http = require('http');
const { Server } = require("socket.io");

const onlineUsers = new Map(); // userId -> Set<socketId>
const disconnectTimers = new Map(); // userId -> Timeout

const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

// Expose IO and Online State to API Routes
app.set('io', io);
app.set('onlineUsers', onlineUsers);
app.set('disconnectTimers', disconnectTimers);

// Signaling Logic
io.on('connection', (socket) => {
    console.log('User Connected:', socket.id);

    // User joins with their ID to receive calls
    socket.on('join', async (userId) => {
        const roomName = String(userId);
        socket.join(roomName);

        try {
            const db = await connectToDatabase();
            const user = await db.collection('users').findOne({ id: userId });
            const userName = user ? user.name : 'Unknown';

            console.log(`[Server] Socket ${socket.id} joined as ${userName} (${userId})`);

            if (!onlineUsers.has(String(userId))) {
                onlineUsers.set(String(userId), new Set());
            }
            onlineUsers.get(String(userId)).add(socket.id);

            if (disconnectTimers.has(String(userId))) {
                clearTimeout(disconnectTimers.get(String(userId)));
                disconnectTimers.delete(String(userId));
            }

            if (onlineUsers.get(String(userId)).size === 1) {
                console.log(`[Server] User ${userName} is now ONLINE`);
                await db.collection('users').updateOne({ id: userId }, { $set: { lastSeen: Date.now() } });
                socket.broadcast.emit('user_status', { userId: userId, online: true });
            }

            const activeUserIds = [];
            for (const [uid, sockets] of onlineUsers.entries()) {
                if (sockets instanceof Set && sockets.size > 0) {
                    activeUserIds.push(uid);
                }
            }
            socket.emit('online_users', activeUserIds);

        } catch (e) {
            console.error("Socket Join Error:", e);
        }
    });

    socket.on('typing', (data) => {
        const room = String(data.receiverId);
        io.to(room).emit('typing', data);
    });

    socket.on('stop_typing', (data) => {
        io.to(String(data.receiverId)).emit('stop_typing', data);
    });

    socket.on('offer', async (data) => {
        const { targetId } = data;
        io.to(String(targetId)).emit('offer', data);

        try {
            const db = await connectToDatabase();
            const targetUser = await db.collection('users').findOne({ id: targetId });

            if (targetUser && targetUser.pushToken) {
                const title = "Incoming Call";
                const body = `${data.callerName || 'Someone'} is calling you...`;

                await sendPushNotification(targetUser.pushToken, title, body, {
                    type: 'call_offer',
                    callerId: data.callerId,
                    callerName: data.callerName,
                    callType: data.type
                }, {
                    android: {
                        priority: 'high',
                        ttl: 0,
                        notification: {
                            channelId: 'call_channel_v3',
                            priority: 'max',
                            sound: 'calling',
                            defaultVibrateTimings: true,
                            visibility: 'public',
                            fullScreenIntent: true,
                            clickAction: 'CALL_CATEGORY'
                        }
                    }
                }, db);
            }
        } catch (e) {
            console.error("Call Push Error:", e);
        }
    });

    socket.on('answer', (data) => {
        const { targetId } = data;
        io.to(String(targetId)).emit('answer', data);
    });

    socket.on('ice-candidate', (data) => {
        const { targetId } = data;
        io.to(String(targetId)).emit('ice-candidate', data);
    });

    socket.on('end-call', (data) => {
        const { targetId } = data;
        io.to(String(targetId)).emit('end-call', data);
    });

    socket.on('disconnect', () => {
        let userId = null;
        for (const [uid, sockets] of onlineUsers.entries()) {
            if (sockets instanceof Set && sockets.has(socket.id)) {
                sockets.delete(socket.id);
                if (sockets.size === 0) {
                    userId = uid;
                }
                break;
            }
        }

        if (userId) {
            const timer = setTimeout(async () => {
                if (onlineUsers.has(userId) && onlineUsers.get(userId).size === 0) {
                    onlineUsers.delete(userId);
                    const lastSeen = Date.now();
                    try {
                        const db = await connectToDatabase();
                        const user = await db.collection('users').findOne({ id: userId });
                        let privacySetting = user?.settings?.lastSeenPrivacy || 'everyone';
                        await db.collection('users').updateOne({ id: userId }, { $set: { lastSeen } });
                        
                        socket.broadcast.emit('user_status', {
                            userId: userId,
                            online: false,
                            lastSeen: privacySetting === 'nobody' ? "Recently" : lastSeen
                        });
                    } catch (e) { }
                }
                disconnectTimers.delete(userId);
            }, 10000);
            disconnectTimers.set(userId, timer);
        }
    });
});

// Graceful Shutdown
const gracefulShutdown = async (signal) => {
    console.log(`\n[Server] Received ${signal}. Shutting down gracefully...`);
    try {
        const db = await connectToDatabase();
        if (db) {
            const now = Date.now();
            const activeIds = Array.from(onlineUsers.keys());
            if (activeIds.length > 0) {
                await db.collection('users').updateMany(
                    { id: { $in: activeIds } },
                    { $set: { lastSeen: now } }
                );
            }
        }
    } catch (e) { }
    
    await notifyAdmin("🚀 OMA Server", "System is shutting down/restarting...");
    process.exit(0);
};

process.on('SIGINT', () => gracefulShutdown('SIGINT'));
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));

connectToDatabase()
    .then(() => setupIndexes())
    .then(() => {
        server.listen(PORT, () => {
            console.log(`\nLocal Development Server Running!`);
            console.log(`- Frontend: http://localhost:${PORT}`);
            console.log(`- API:      http://localhost:${PORT}/api/...`);
            console.log(`- MongoDB:  Connected`);
            
            // 5-second Grace Period to allow clients to sync new tokens
            setTimeout(() => {
                notifyAdmin("🚀 OMA Server", "System is ONLINE and ready!");
            }, 5000);
        });
    })
    .catch(err => {
        console.error("Critical MongoDB failure:", err);
        process.exit(1);
    });
