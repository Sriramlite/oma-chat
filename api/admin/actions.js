const { connectToDatabase } = require('../utils/db');
const { verifyToken, verifyAdmin } = require('../utils/auth');

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const userPayload = verifyToken(authHeader.split(' ')[1]);
        if (!userPayload) return res.status(401).json({ error: 'Invalid Token' });

        const db = await connectToDatabase();
        const isAdmin = await verifyAdmin(userPayload.id, db);
        if (!isAdmin) return res.status(403).json({ error: 'Forbidden' });

        const { action, userId } = req.body;
        if (!userId) return res.status(400).json({ error: 'User ID required' });

        if (action === 'delete') {
            // Prevent deleting self
            if (userId === userPayload.id) return res.status(400).json({ error: 'Cannot delete yourself' });

            // 1. Delete Messages
            await db.collection('messages').deleteMany({ 
                $or: [{ senderId: userId }, { receiverId: userId }] 
            });

            // 2. Delete User
            const result = await db.collection('users').deleteOne({ id: userId });
            
            if (result.deletedCount === 0) return res.status(404).json({ error: 'User not found' });

            return res.status(200).json({ success: true, message: 'User and their data deleted successfully' });
        }

        res.status(400).json({ error: 'Invalid action' });
    } catch (e) {
        console.error("Admin Action Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
