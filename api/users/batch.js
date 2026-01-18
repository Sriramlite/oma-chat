const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');

module.exports = async (req, res) => {
    // CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,POST');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        // Auth
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const token = authHeader.split(' ')[1];
        if (!verifyToken(token)) return res.status(401).json({ error: 'Invalid Token' });

        const { ids } = req.body;
        if (!Array.isArray(ids)) return res.status(400).json({ error: 'Invalid IDs' });

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        const foundUsers = await usersCollection.find({ id: { $in: ids } })
            .project({
                id: 1,
                name: 1,
                username: 1,
                avatar: 1
            })
            .toArray();

        res.status(200).json(foundUsers);
    } catch (e) {
        console.error("Batch Users Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
