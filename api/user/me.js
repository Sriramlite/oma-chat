const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');

module.exports = async (req, res) => {
    // CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed' });

    try {
        // Auth
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const token = authHeader.split(' ')[1];
        const userPayload = verifyToken(token);
        if (!userPayload) return res.status(401).json({ error: 'Invalid Token' });

        const db = await connectToDatabase();
        const user = await db.collection('users').findOne({ id: userPayload.id });

        if (!user) return res.status(404).json({ error: 'User not found' });

        // Don't return password
        const { password, ...safeUser } = user;

        res.status(200).json(safeUser);
    } catch (e) {
        console.error("Me API Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
