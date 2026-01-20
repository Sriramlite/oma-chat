const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');
const admin = require('firebase-admin');
const { initFirebase } = require('../utils/firebase');

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const userPayload = verifyToken(authHeader.split(' ')[1]);
        if (!userPayload) return res.status(401).json({ error: 'Invalid Token' });

        const { idToken } = req.body;
        if (!idToken) return res.status(400).json({ error: 'Firebase ID Token required' });

        // Initialize Firebase
        initFirebase();

        // Verify Firebase ID Token
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        const phoneNumber = decodedToken.phone_number;

        if (!phoneNumber) {
            return res.status(400).json({ error: 'Invalid phone authentication' });
        }

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        // Check if this phone is already linked to ANOTHER user
        const existingUser = await usersCollection.findOne({
            username: phoneNumber,
            id: { $ne: userPayload.id }
        });

        if (existingUser) {
            return res.status(400).json({ error: 'This phone number is already linked to another account' });
        }

        // Link phone to current user
        // We'll store it as 'phone' field, or even allow them to login via phone later by syncing 'username'
        // For now, let's update a dedicated 'phone' field and also set a settings flag
        await usersCollection.updateOne(
            { id: userPayload.id },
            {
                $set: {
                    phone: phoneNumber,
                    'settings.phoneLinked': true
                }
            }
        );

        res.status(200).json({ success: true, phoneNumber });

    } catch (e) {
        console.error("Link Phone Error:", e);
        res.status(401).json({ error: `Verification failed: ${e.message}` });
    }
};
