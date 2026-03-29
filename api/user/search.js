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
        const authedUser = verifyToken(token);
        if (!authedUser) return res.status(401).json({ error: 'Invalid Token' });

        const { q } = req.query;
        if (!q || q.length < 2) return res.status(400).json({ error: 'Search query too short' });

        const db = await connectToDatabase();
        if (!db) throw new Error('DB Connection Failed');

        // Case-insensitive search on Name or Username
        const regex = new RegExp(q, 'i');
        const users = await db.collection('users').find({
            $or: [
                { name: regex },
                { username: regex }
            ],
            id: { $ne: authedUser.id } // Exclude self
        })
        .limit(20)
        .project({
            id: 1,
            name: 1,
            username: 1,
            avatar: 1,
            lastSeen: 1,
            bio: 1
        })
        .toArray();

        res.status(200).json(users);
    } catch (e) {
        console.error("User Search Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
