const { connectToDatabase } = require('../utils/db');
const { generateToken } = require('../utils/auth');
const crypto = require('crypto');

module.exports = async (req, res) => {
    // CORS Headers
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
        const { username, password } = req.body;
        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        const hashedPassword = crypto.createHash('sha256').update(password).digest('hex');
        const user = await usersCollection.findOne({ username, password: hashedPassword });

        if (!user) {
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        const token = generateToken(user);
        res.status(200).json({ token, user: { id: user.id, username: user.username, name: user.name, avatar: user.avatar } });
    } catch (e) {
        console.error("Login Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
