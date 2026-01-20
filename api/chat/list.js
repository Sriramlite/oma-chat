const { connectToDatabase } = require('../../api/utils/db');
const { verifyToken } = require('../../api/utils/auth');

module.exports = async (req, res) => {
    // 1. CORS & Auth
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

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');
        const groupsCollection = db.collection('groups');
        const messagesCollection = db.collection('messages');

        // 3. Find Recent DM Partners
        // We only want messages where user is sender or receiver
        // And not 'general'
        // And not a group (groups handled by groups/list)

        // Fetch all relevant messages involves user (optimization: limit 500 or use aggregation)
        // Aggregation to find "Last Message" per partner is better.
        // Partner = if sender==me then receiver, else sender.

        // Simplest migration: Fetch recent messages involving user, then process in code
        // (Not performant for 1M messages, but okay for MVP migration)
        const recentMessages = await messagesCollection.find({
            $or: [{ senderId: user.id }, { receiverId: user.id }],
            receiverId: { $ne: 'general' }
        }).sort({ timestamp: 1 }).toArray(); // Get them all to find latest? Or sort desc and uniq?

        // Better: Iterate in reverse (newest first)
        // But let's stick closer to the original logic structure but with async optimization

        // Let's optimize: Get all groups to exclude them from DM list
        const allGroups = await groupsCollection.find({}).project({ id: 1 }).toArray();
        const groupIds = new Set(allGroups.map(g => g.id));

        const recentuserMap = new Map();

        for (const msg of recentMessages) {
            let partnerId = null;

            if (msg.senderId === user.id) {
                partnerId = msg.receiverId;
            } else if (msg.receiverId === user.id) {
                partnerId = msg.senderId;
            }

            if (!partnerId || partnerId === 'general') continue;
            if (groupIds.has(partnerId)) continue; // Skip groups

            const existing = recentuserMap.get(partnerId);
            if (!existing || msg.timestamp > existing.timestamp) {
                recentuserMap.set(partnerId, {
                    id: partnerId,
                    lastMsg: msg.type === 'text' ? msg.content : (msg.type === 'image' ? 'Image' : 'Media'),
                    timestamp: msg.timestamp,
                    type: 'user'
                });
            }
        }

        // 4. Enrich with User Details
        const results = [];
        for (const [id, chatData] of recentuserMap.entries()) {
            const u = await usersCollection.findOne({ id: id });
            if (u) {
                results.push({
                    ...chatData,
                    name: u.name,
                    username: u.username,
                    avatar: u.avatar,
                    status: u.status,
                    lastSeen: u.lastSeen
                });
            }
        }

        // 5. Sort by Time Descending
        results.sort((a, b) => b.timestamp - a.timestamp);

        res.status(200).json(results);
    } catch (e) {
        console.error("Chat List Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
