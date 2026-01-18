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
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const user = verifyToken(authHeader.split(' ')[1]);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const db = await connectToDatabase();
        const groupsCollection = db.collection('groups');
        const messagesCollection = db.collection('messages');

        // Find groups where user is a member
        const myGroups = await groupsCollection.find({ members: user.id }).toArray();

        // Calculate last message for each group
        const groupsWithMeta = await Promise.all(myGroups.map(async g => {
            // Find specific last message for this group
            const lastMsg = await messagesCollection.find({ receiverId: g.id })
                .sort({ timestamp: -1 })
                .limit(1)
                .next();

            return {
                ...g,
                lastMsg: lastMsg ? (lastMsg.type === 'text' ? lastMsg.content : (lastMsg.type === 'system' ? 'Group created' : 'Media')) : 'No messages yet',
                lastTimestamp: lastMsg ? lastMsg.timestamp : g.created
            };
        }));

        res.status(200).json(groupsWithMeta);
    } catch (e) {
        console.error("Group List Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
