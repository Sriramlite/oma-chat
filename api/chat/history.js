const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');

module.exports = async (req, res) => {
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

        const { since, chatId, type } = req.query;
        const targetChatId = chatId || 'general';

        const db = await connectToDatabase();
        const messagesCollection = db.collection('messages');
        const groupsCollection = db.collection('groups');

        let query = {};

        if (targetChatId === 'all') {
            // Find groups user is a member of
            const userGroups = await groupsCollection.find({ members: user.id }).toArray();
            const groupIds = userGroups.map(g => g.id);

            query = {
                $or: [
                    { receiverId: 'general' },
                    { receiverId: user.id },
                    { senderId: user.id },
                    { receiverId: { $in: groupIds } }
                ]
            };
        } else if (targetChatId === 'general') {
            query = { receiverId: 'general' };
        } else {
            // Check if group
            const group = await groupsCollection.findOne({ id: targetChatId });
            if (group) {
                // Check membership
                if (!group.members.includes(user.id)) {
                    // Not authorized? For now return empty or allow depending on policy. 
                    // Let's match previous logic: if found group, return messages.
                    // Previous logic implied membership check was comment out? 
                    // "Security: Should check if user is member... Better: Check membership"
                    // Let's enforce membership for better security now that we are migrating.
                    return res.status(403).json({ error: 'Not a member of this group' });
                }
                query = { receiverId: targetChatId };
            } else {
                // DM Logic
                query = {
                    $or: [
                        { senderId: user.id, receiverId: targetChatId },
                        { senderId: targetChatId, receiverId: user.id }
                    ]
                };
            }
        }

        if (type) {
            query.type = type;
        }

        if (since) {
            query.timestamp = { $gte: parseInt(since) };
        }

        // Optimization: Sort by timestamp
        let cursor = messagesCollection.find(query).sort({ timestamp: 1 });

        // Limit if no 'since' provided? (Previous code limited to last 50)
        // MongoDB implementation: if no since, we probably want the *latest* 50.
        // So we might need to sort desc, limit 50, then reverse.
        // Limit if no 'since' provided? (Previous code limited to last 50)
        // MongoDB implementation: if no since, we probably want the *latest* 50.
        // So we might need to sort desc, limit 50, then reverse.
        let messages = [];
        if (!since) {
            const count = await messagesCollection.countDocuments(query);
            if (count > 50) {
                messages = await messagesCollection.find(query).sort({ timestamp: -1 }).limit(50).toArray();
                messages.reverse(); // Standard chronological order
            } else {
                messages = await messagesCollection.find(query).sort({ timestamp: 1 }).toArray();
            }
        } else {
            messages = await messagesCollection.find(query).sort({ timestamp: 1 }).toArray();
        }

        // Population Logic for Replies
        const replyIds = [...new Set(messages.filter(m => m.replyToId && !m.replyTo).map(m => m.replyToId))];
        const replyMap = {};

        if (replyIds.length > 0) {
            const replies = await messagesCollection.find({ id: { $in: replyIds } })
                .project({ id: 1, senderName: 1, content: 1, type: 1 }).toArray();
            replies.forEach(r => replyMap[r.id] = r);
        }

        messages.forEach(m => {
            if (m.replyToId && !m.replyTo && replyMap[m.replyToId]) {
                m.replyTo = replyMap[m.replyToId];
            }
            // Calculate Starred for this user
            m.isStarred = m.starredBy && m.starredBy.includes(user.id);
        });

        res.status(200).json(messages);

    } catch (e) {
        console.error("Chat History Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
