const { connectToDatabase } = require('../utils/db');
const { generateToken } = require('../utils/auth');
const crypto = require('crypto');

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { phone, otp } = req.body;
        if (!phone || !otp) return res.status(400).json({ error: 'Phone and OTP required' });

        const db = await connectToDatabase();
        const otps = db.collection('otps');

        // 1. Find OTP record
        const record = await otps.findOne({ phone, otp });

        if (!record) {
            return res.status(401).json({ error: 'Invalid OTP' });
        }

        // 2. Check expiry
        if (new Date() > record.expiresAt) {
            return res.status(401).json({ error: 'OTP expired' });
        }

        // 3. Clear OTP after verification (Success)
        await otps.deleteOne({ _id: record._id });

        // 4. Find or Create User
        const usersCollection = db.collection('users');
        let user = await usersCollection.findOne({ username: phone });
        let isNew = false;

        if (!user) {
            isNew = true;
            user = {
                id: crypto.randomUUID(),
                username: phone,
                name: "New User",
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

        // 5. Generate and send token
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
        console.error("Fast2SMS Verify OTP Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
