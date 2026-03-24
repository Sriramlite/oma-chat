const { connectToDatabase } = require('../utils/db');
const { generateToken } = require('../utils/auth');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');

module.exports = async (req, res) => {
    // Enable CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
    res.setHeader(
        'Access-Control-Allow-Headers',
        'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version'
    );

    if (req.method === 'OPTIONS') {
        res.status(200).end();
        return;
    }

    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { username, password, name, phone } = req.body;
        if (!username || !password || !phone) return res.status(400).json({ error: 'Missing fields' });

        // Input Validation
        if (password.length < 8) {
            return res.status(400).json({ error: 'Password must be at least 8 characters long' });
        }
        
        // Clean phone number
        const cleanPhone = phone.replace(/\D/g, '');
        if (cleanPhone.length < 10) {
            return res.status(400).json({ error: 'Invalid phone number' });
        }

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        // Check unique username AND phone
        const existing = await usersCollection.findOne({ $or: [{ username }, { phone: cleanPhone }] });
        if (existing) {
            if (existing.username === username) return res.status(409).json({ error: 'Username taken' });
            return res.status(409).json({ error: 'Phone number already registered' });
        }

        const salt = await bcrypt.genSalt(10);
        const hashedHash = await bcrypt.hash(password, salt);

        const newUser = {
            id: crypto.randomUUID(),
            username,
            phone: cleanPhone,
            name: name || username,
            password: hashedHash,
            avatar: 'https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png',
            status: 'online',
            joinedAt: new Date().toISOString(),
            settings: {
                darkMode: false,
                lastSeenPrivacy: 'everyone',
                readReceipts: true
            },
            isAdmin: false,
            blockedUsers: [] // Array of user IDs
        };

        await usersCollection.insertOne(newUser);

        const token = generateToken(newUser);
        res.status(201).json({ token, user: { id: newUser.id, username: newUser.username, name: newUser.name, avatar: newUser.avatar } });

    } catch (e) {
        console.error("Signup Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
