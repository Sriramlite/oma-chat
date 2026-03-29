const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');

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

        const { chatId } = req.body;
        if (!chatId) return res.status(400).json({ error: 'Chat ID required' });

        if (chatId === 'general') {
            return res.status(200).json({ success: true, updated: 0 });
        }

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');
        const messagesCollection = db.collection('messages');

        // Privacy Check: Read Receipts Reciprocity
        // 1. Check Me (The reader)
        const me = await usersCollection.findOne({ id: user.id });
        if (me && me.settings && me.settings.readReceipts === false) {
            return res.status(200).json({ success: true, updated: 0, reason: 'privacy_disabled_by_me' });
        }

        // 2. Check Partner (The sender)
        const partner = await usersCollection.findOne({ id: chatId });
        if (partner && partner.settings && partner.settings.readReceipts === false) {
            return res.status(200).json({ success: true, updated: 0, reason: 'privacy_disabled_by_partner' });
        }

        // Update messages sent BY the partner (chatId) TO current user (user.id)
        const result = await messagesCollection.updateMany(
            {
                senderId: chatId,
                receiverId: user.id,
                status: { $ne: 'seen' }
            },
            {
                $set: { status: 'seen' }
            }
        );

        if (result.modifiedCount > 0) {
            console.log(`[Read] Chat ${chatId} marked ${result.modifiedCount} messages as SEEN for ${user.username}`);
        }
        res.status(200).json({ success: true, updated: result.modifiedCount });
    } catch (e) {
        console.error("Read Mark Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
