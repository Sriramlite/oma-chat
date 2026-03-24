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

        const totalUsers = await db.collection('users').countDocuments();
        
        const onlineUsersMap = req.app.get('onlineUsers') || new Map();
        const onlineUsers = onlineUsersMap.size;

        const totalMessages = await db.collection('messages').countDocuments();
        const totalCalls = await db.collection('messages').countDocuments({ type: 'call_log' });

        res.status(200).json({
            totalUsers,
            onlineUsers,
            totalMessages,
            totalCalls
        });
    } catch (e) {
        console.error("Admin Stats Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
