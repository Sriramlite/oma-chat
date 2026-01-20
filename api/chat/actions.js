const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');
const { ObjectId } = require('mongodb');

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
        const token = authHeader.split(' ')[1];
        const user = verifyToken(token);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const { action, messageId, ...payload } = req.body;
        // payload can contain: newContent (for edit), mode (for delete: 'me'|'everyone')

        if (!messageId || !action) return res.status(400).json({ error: 'Missing parameters' });

        const db = await connectToDatabase();
        const messagesCollection = db.collection('messages');

        const message = await messagesCollection.findOne({ id: messageId });
        if (!message) return res.status(404).json({ error: 'Message not found' });

        let update = {};

        switch (action) {
            case 'delete':
                if (payload.mode === 'everyone') {
                    // Only sender can delete for everyone
                    if (message.senderId !== user.id) return res.status(403).json({ error: 'Permission denied' });
                    update = { $set: { isDeleted: true, content: '🚫 This message was deleted', type: 'system' } };
                } else {
                    // Delete for me (add to deletedFor array)
                    update = { $addToSet: { deletedFor: user.id } };
                }
                break;

            case 'edit':
                if (message.senderId !== user.id) return res.status(403).json({ error: 'Permission denied' });
                // Check if deleted
                if (message.isDeleted) return res.status(400).json({ error: 'Cannot edit deleted message' });
                update = { $set: { content: payload.newContent, isEdited: true } };
                break;

            case 'star':
                // Toggle Star for user
                // If already starred, remove vs add? The UI should handle toggle logic ideally, but backend toggle is safer or explicit set.
                // Let's assume 'toggle' or explicit state. Let's do simple toggle if no state provided.
                const starredBy = message.starredBy || [];
                if (starredBy.includes(user.id)) {
                    update = { $pull: { starredBy: user.id } };
                } else {
                    update = { $addToSet: { starredBy: user.id } };
                }
                break;

            case 'pin':
                // Pin is global for the chat. Requires admin rights? Or any participant?
                // For DM: Any. For Group: Maybe admin? Feature list doesn't specify. Allow any for now.
                // Toggle Pin
                update = { $set: { isPinned: !message.isPinned } };
                break;

            default:
                return res.status(400).json({ error: 'Invalid action' });
        }

        await messagesCollection.updateOne({ id: messageId }, update);

        // Fetch updated message to return
        // const updatedMessage = await messagesCollection.findOne({ id: messageId });
        // Return 200 OK
        res.status(200).json({ success: true, action });

    } catch (e) {
        console.error("Message Action Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
