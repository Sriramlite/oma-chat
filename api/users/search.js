const { connectToDatabase } = require('../../api/utils/db');
const { verifyToken } = require('../../api/utils/auth');

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();

    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const user = verifyToken(authHeader.split(' ')[1]);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const { q } = req.query;
        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        let query = { id: { $ne: user.id } }; // Exclude self

        if (q && q.trim() !== '') {
            const regex = new RegExp(q, 'i'); // Case-insensitive regex
            query.$or = [
                { username: regex },
                { name: regex }
            ];
        }

        const results = await usersCollection.find(query)
            .limit(20)
            .project({
                id: 1,
                username: 1,
                name: 1,
                avatar: 1,
                status: 1
            })
            .toArray();

        res.status(200).json(results);
    } catch (e) {
        console.error("Search Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
