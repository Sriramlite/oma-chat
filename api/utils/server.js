require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const { connectToDatabase } = require('./db'); // Import DB connection

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());
// Serve static files from public directory
app.use(express.static(path.join(__dirname, '../../public')));

// Dynamic Route Handler for Vercel-like functions
console.log('Initializing API routes...');
try {
    // Using simple prefix match for /api
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
                console.log(`API Route not found: ${relativePath}`);
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
                console.error(`Module ${relativePath} does not export a function`);
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

// Expose IO to API Routes
app.set('io', io);

// Signaling Logic
io.on('connection', (socket) => {
    console.log('User Connected:', socket.id);

    // User joins with their ID to receive calls
    socket.on('join', async (userId) => {
        // Ensure String ID
        const roomName = String(userId);
        socket.join(roomName);

        // MongoDB Lookup
        try {
            const db = await connectToDatabase();
            const user = await db.collection('users').findOne({ id: userId });
            const userName = user ? user.name : 'Unknown';

            console.log(`[Server] Socket ${socket.id} joined as ${userName} (${userId})`);

            // Track Online Status
            if (!onlineUsers.has(String(userId))) {
                onlineUsers.set(String(userId), new Set());
            }
            onlineUsers.get(String(userId)).add(socket.id);

            // Cancel any pending disconnect timer
            if (disconnectTimers.has(String(userId))) {
                console.log(`[Server] Cancelled disconnect timer for ${userName}`);
                clearTimeout(disconnectTimers.get(String(userId)));
                disconnectTimers.delete(String(userId));
            }

            // Broadcast Status: ONLINE
            if (onlineUsers.get(String(userId)).size === 1) {
                console.log(`[Server] User ${userName} is now ONLINE`);
                // Update DB to ensure lastSeen is at least "Now" if we crash
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

    // Typing Indicators
    // Typing Indicators
    // Typing Indicators
    socket.on('typing', (data) => {
        const room = String(data.receiverId);
        io.to(room).emit('typing', data);
    });

    socket.on('stop_typing', (data) => {
        io.to(String(data.receiverId)).emit('stop_typing', data);
    });

    // Call Initiation
    socket.on('offer', async (data) => {
        const { targetId, offer } = data;
        const targetRoom = String(targetId);
        io.to(targetRoom).emit('offer', data);

        // Send Push Notification
        try {
            const db = await connectToDatabase();
            const targetUser = await db.collection('users').findOne({ id: targetId });

            if (targetUser && targetUser.pushToken) {
                const { sendPushNotification } = require('./firebase');
                const title = "Incoming Call";
                const body = `${data.callerName || 'Someone'} is calling you...`;

                // Send High Priority Call Notification
                // Note: We avoid sending the full SDP 'offer' in data if it's too large, 
                // but usually it fits. If issues arise, client should fetch offer via socket.
                await sendPushNotification(targetUser.pushToken, title, body, {
                    type: 'call_offer',
                    callerId: data.callerId,
                    callerName: data.callerName,
                    callType: data.type
                }, {
                    android: {
                        priority: 'high',
                        notification: {
                            channelId: 'call_channel',
                            priority: 'max',
                            defaultSound: true,
                            defaultVibrateTimings: true,
                            visibility: 'public'
                        }
                    }
                });
            }
        } catch (e) {
            console.error("Call Push Error:", e);
        }
    });

    // Answer Call
    socket.on('answer', (data) => {
        const { targetId, answer } = data;
        io.to(String(targetId)).emit('answer', data);
    });

    // ICE Candidates
    socket.on('ice-candidate', (data) => {
        const { targetId, candidate } = data;
        io.to(String(targetId)).emit('ice-candidate', data);
    });

    // End Call
    socket.on('end-call', (data) => {
        const { targetId } = data;
        io.to(String(targetId)).emit('end-call', data);
    });

    socket.on('disconnect', () => {
        console.log('User Disconnected:', socket.id);

        try {
            // Find User ID
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
                // Schedule Disconnect
                const timer = setTimeout(async () => {
                    // Check if still empty
                    if (onlineUsers.has(userId) && onlineUsers.get(userId).size === 0) {
                        onlineUsers.delete(userId);

                        const lastSeen = Date.now();

                        // Update DB
                        try {
                            const db = await connectToDatabase();
                            const usersCollection = db.collection('users');
                            const user = await usersCollection.findOne({ id: userId });

                            let privacySetting = 'everyone';
                            if (user) {
                                await usersCollection.updateOne({ id: userId }, { $set: { lastSeen: lastSeen } });
                                privacySetting = user.settings?.lastSeenPrivacy || 'everyone';
                            }

                            // Broadcast Status: OFFLINE
                            let lastSeenPayload = lastSeen;
                            if (privacySetting === 'nobody') {
                                lastSeenPayload = "Recently";
                            }

                            console.log(`[Server] Grace period ended. User ${userId} went OFFLINE.`);

                            socket.broadcast.emit('user_status', {
                                userId: userId,
                                online: false,
                                lastSeen: lastSeenPayload
                            });
                        } catch (e) {
                            console.error("Socket Disconnect DB Error:", e);
                        }
                    }
                    disconnectTimers.delete(userId);
                }, 10000); // 10 seconds grace

                disconnectTimers.set(userId, timer);
            }
        } catch (e) {
            console.error("[Server] Error in disconnect handler:", e);
        }
    });
});

// Graceful Shutdown: Persist lastSeen for online users
const gracefulShutdown = async (signal) => {
    console.log(`\n[Server] Received ${signal}. Shutting down gracefully...`);
    try {
        const db = await connectToDatabase();
        if (db) {
            const now = Date.now();
            const activeIds = Array.from(onlineUsers.keys());
            if (activeIds.length > 0) {
                console.log(`[Server] Persisting lastSeen for ${activeIds.length} online users...`);
                await db.collection('users').updateMany(
                    { id: { $in: activeIds } },
                    { $set: { lastSeen: now } }
                );
            }
        }
    } catch (e) {
        console.error("[Server] Error during shutdown persistence:", e);
    }
    process.exit(0);
};

process.on('SIGINT', () => gracefulShutdown('SIGINT'));
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));

connectToDatabase().then(() => {
    server.listen(PORT, () => {
        console.log(`\nLocal Development Server Running!`);
        console.log(`- Frontend: http://localhost:${PORT}`);
        console.log(`- API:      http://localhost:${PORT}/api/...`);
        console.log(`- MongoDB:  Connected`);
    });
}).catch(err => {
    console.error("Failed to connect to MongoDB, exiting...", err);
    process.exit(1);
});
