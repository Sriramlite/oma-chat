const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');
const crypto = require('crypto');

module.exports = async (req, res) => {
    // CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        // Auth
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const token = authHeader.split(' ')[1];
        const userPayload = verifyToken(token);
        if (!userPayload) return res.status(401).json({ error: 'Invalid Token' });

        const { password } = req.body;
        if (!password) return res.status(400).json({ error: 'Password required' });

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        const user = await usersCollection.findOne({ id: userPayload.id });

        if (!user) return res.status(404).json({ error: 'User not found' });

        const hashedFn = crypto.createHash('sha256').update(password).digest('hex');

        if (user.password !== hashedFn) {
            return res.status(401).json({ error: 'Incorrect password' });
        }

        // Delete User
        await usersCollection.deleteOne({ id: userPayload.id });

        res.status(200).json({ message: 'Account deleted' });
    } catch (e) {
        console.error("Delete Account Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
