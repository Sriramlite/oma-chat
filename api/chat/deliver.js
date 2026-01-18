const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');

module.exports = async (req, res) => {
    // CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const user = verifyToken(authHeader.split(' ')[1]);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const { messageIds } = req.body; // Array of IDs
        if (!messageIds || !Array.isArray(messageIds)) return res.status(400).json({ error: 'Invalid messageIds' });

        const db = await connectToDatabase();
        const messagesCollection = db.collection('messages');

        // Update these messages to 'delivered' IF they are intended for the user
        const result = await messagesCollection.updateMany(
            {
                id: { $in: messageIds },
                receiverId: user.id,
                status: 'sent'
            },
            {
                $set: { status: 'delivered' }
            }
        );

        res.status(200).json({ success: true, count: result.modifiedCount });
    } catch (e) {
        console.error("Deliver Mark Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
