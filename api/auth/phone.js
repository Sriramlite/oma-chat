const { connectToDatabase } = require('../utils/db');
const { generateToken } = require('../utils/auth');
const admin = require('firebase-admin');
const crypto = require('crypto');

// Ensure firebase-admin is initialized (it might already be via push notification utils)
const { initFirebase } = require('../utils/firebase');

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { idToken } = req.body;
        if (!idToken) return res.status(400).json({ error: 'ID Token required' });

        // Initialize Firebase if needed
        initFirebase();

        // Verify Firebase ID Token
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        const phoneNumber = decodedToken.phone_number;

        if (!phoneNumber) {
            return res.status(400).json({ error: 'Invalid phone authentication' });
        }

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        // Check if user exists by phone
        let user = await usersCollection.findOne({ username: phoneNumber });

        if (!user) {
            // Register new user with phone as username
            user = {
                id: crypto.randomUUID(),
                username: phoneNumber,
                name: phoneNumber, // Can be updated later
                avatar: 'https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png',
                status: 'online',
                joinedAt: new Date().toISOString(),
                settings: {
                    darkMode: false,
                    lastSeenPrivacy: 'everyone',
                    readReceipts: true
                },
                blockedUsers: []
            };
            await usersCollection.insertOne(user);
        }

        const token = generateToken(user);
        res.status(200).json({
            token,
            user: {
                id: user.id,
                username: user.username,
                name: user.name,
                avatar: user.avatar
            }
        });

    } catch (e) {
        console.error("Phone Auth Verification Error:", e);
        res.status(401).json({
            error: 'Verification failed',
            details: e.message,
            code: e.code
        });
    }
};
