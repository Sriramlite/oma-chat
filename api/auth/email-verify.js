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

        // Verify Firebase ID Token
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        const email = decodedToken.email;

        if (!email) {
            return res.status(400).json({ error: 'Invalid email authentication' });
        }

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        // Check if user exists by email (using username field for now, or add email field)
        // Note: Our schema primarily uses 'username'. 
        // Strategy: If user exists with username=email, log them in.
        // If not, create new user with username=email.

        let user = await usersCollection.findOne({ username: email });
        let isNew = false;

        if (!user) {
            // Check if email is stored in a separate field (for linked accounts)
            // For this implementation, we treat email as the primary username if not found.
            isNew = true;
            user = {
                id: crypto.randomUUID(),
                username: email,
                email: email, // Store explicitly
                name: email.split('@')[0], // Default name
                avatar: `https://ui-avatars.com/api/?name=${encodeURIComponent(email)}&background=random`,
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
            isNew,
            user: {
                id: user.id,
                username: user.username,
                name: user.name,
                avatar: user.avatar
            }
        });

    } catch (e) {
        console.error("Email Auth Verification Error:", e);
        res.status(401).json({
            error: `Verification failed: ${e.message}`,
            details: e.message
        });
    }
};
