const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');
const crypto = require('crypto');

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();

    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const user = verifyToken(authHeader.split(' ')[1]);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const { targetedUserId, reason } = req.body;

        if (!targetedUserId || !reason) return res.status(400).json({ error: 'Missing fields' });

        const db = await connectToDatabase();
        const reportsCollection = db.collection('reports');

        const report = {
            id: crypto.randomUUID(),
            reporterId: user.id,
            targetedUserId,
            reason,
            timestamp: Date.now(),
            status: 'pending'
        };

        await reportsCollection.insertOne(report);

        res.status(200).json({ success: true, message: 'Report submitted' });
    } catch (e) {
        console.error("Report User Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
