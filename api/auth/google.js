const { connectToDatabase } = require('../utils/db');
const { generateToken } = require('../utils/auth');
const admin = require('firebase-admin');
const crypto = require('crypto');

// Ensure firebase-admin is initialized
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

        initFirebase();

        // Verify Firebase Google ID Token
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        const { email, name, picture, uid } = decodedToken;

        if (!email) {
            return res.status(400).json({ error: 'Invalid Google authentication' });
        }

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        // Check if user exists by email (treat email as username for Google Login)
        let user = await usersCollection.findOne({ username: email });
        let isNew = false;

        if (!user) {
            isNew = true;
            user = {
                id: crypto.randomUUID(),
                username: email,
                email: email,
                name: name || email.split('@')[0],
                avatar: picture || `https://ui-avatars.com/api/?name=${encodeURIComponent(name || email)}&background=random`,
                status: 'online',
                joinedAt: new Date().toISOString(),
                googleId: uid, // Optional: Store Google UID
                settings: {
                    darkMode: false,
                    lastSeenPrivacy: 'everyone',
                    readReceipts: true
                },
                blockedUsers: []
            };
            await usersCollection.insertOne(user);
        } else {
            // Optional: Update avatar/name on login if they changed on Google?
            // For now, let's keep local overrides if user set them.
        }

        const token = generateToken(user);
        res.status(200).json({
            token,
            isNew,
            user: {
                id: user.id,
                username: user.username,
                name: user.name,
                avatar: user.avatar
            }
        });

    } catch (e) {
        console.error("Google Auth Verification Error:", e);
        res.status(401).json({
            error: `Verification failed: ${e.message}`,
            details: e.message
        });
    }
};
