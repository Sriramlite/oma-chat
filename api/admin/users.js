const { connectToDatabase } = require('../utils/db');
const { verifyToken, verifyAdmin } = require('../utils/auth');

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const userPayload = verifyToken(authHeader.split(' ')[1]);
        if (!userPayload) return res.status(401).json({ error: 'Invalid Token' });

        const db = await connectToDatabase();
        const isAdmin = await verifyAdmin(userPayload.id, db);
        if (!isAdmin) return res.status(403).json({ error: 'Forbidden: Admin access required' });

        const users = await db.collection('users').find({}).toArray();
        // Remove passwords only, keep everything else including lastSeen
        const safeUsers = users.map(({ password, ...rest }) => rest);

        res.status(200).json(safeUsers);
    } catch (e) {
        console.error("Admin Users Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
