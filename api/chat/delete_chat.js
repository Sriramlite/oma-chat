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

        const { chatId } = req.body;
        if (!chatId) return res.status(400).json({ error: 'Chat ID required' });

        const db = await connectToDatabase();
        const messagesCollection = db.collection('messages');
        const groupsCollection = db.collection('groups');

        // Check if it's a group
        const group = await groupsCollection.findOne({ id: chatId });

        // If Group: "Delete" means "Clear History" for me? Or Delete Group?
        // Feature request says "Delete the chat in both sides".
        // For DMs: Delete all messages between these two users.
        // For Groups: Usually admin only deletes group. 
        // Let's implement:
        // Group: Admin -> Destroys Group. Member -> Clears local visible? (Hard in simple backend).
        // DM: Deletes all messages matching (sender=me, receiver=them) OR (sender=them, receiver=me)

        if (group) {
            // Group Deletion (Admin only)
            if (group.adminIds && group.adminIds.includes(user.id)) {
                await groupsCollection.deleteOne({ id: chatId });
                await messagesCollection.deleteMany({ receiverId: chatId }); // Delete all group messages
                return res.status(200).json({ success: true, message: 'Group deleted' });
            } else {
                return res.status(403).json({ error: 'Only admins can delete groups' });
            }
        } else {
            // DM Deletion
            const targetId = chatId;
            // Delete messages where (sender=Me AND receiver=Target) OR (sender=Target AND receiver=Me)
            await messagesCollection.deleteMany({
                $or: [
                    { senderId: user.id, receiverId: targetId },
                    { senderId: targetId, receiverId: user.id }
                ]
            });
            return res.status(200).json({ success: true, message: 'Chat deleted for everyone' });
        }

    } catch (e) {
        console.error(e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
