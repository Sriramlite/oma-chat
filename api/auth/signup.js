const { connectToDatabase } = require('../utils/db');
const { generateToken } = require('../utils/auth');
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
        const { username, password, name } = req.body;
        if (!username || !password) return res.status(400).json({ error: 'Missing fields' });

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        const existingUser = await usersCollection.findOne({ username });
        if (existingUser) {
            return res.status(409).json({ error: 'Username taken' });
        }

        const newUser = {
            id: crypto.randomUUID(),
            username,
            name: name || username,
            password: crypto.createHash('sha256').update(password).digest('hex'), // Simple hash
            avatar: 'https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png',
            status: 'online',
            joinedAt: new Date().toISOString(),
            settings: {
                darkMode: false,
                lastSeenPrivacy: 'everyone',
                readReceipts: true
            },
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
