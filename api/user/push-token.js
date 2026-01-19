const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');

module.exports = async (req, res) => {
    if (req.method !== 'POST') {
        return res.status(405).json({ error: 'Method not allowed' });
    }

    const token = req.headers.authorization?.split(' ')[1];
    if (!token) return res.status(401).json({ error: 'Unauthorized' });

    try {
        const decoded = verifyToken(token);
        if (!decoded) return res.status(401).json({ error: 'Invalid token' });
        const userId = decoded.id;
        const pushToken = req.body.token;

        if (!pushToken) {
            return res.status(400).json({ error: 'Token required' });
        }

        const db = await connectToDatabase();

        // Update user with push token
        // usage: We might want an array of tokens if they have multiple devices, 
        // but for simplicity let's stick to one (last active device) for now, 
        // or addToSet if we want multi-device. 
        // Let's go with single token 'currentDeviceToken' for MVP to avoid stale tokens.

        await db.collection('users').updateOne(
            { id: userId },
            { $set: { pushToken: pushToken } }
        );

        res.json({ success: true });

    } catch (error) {
        console.error('Push Token Error:', error);
        res.status(500).json({ error: 'Server Error' });
    }
};
