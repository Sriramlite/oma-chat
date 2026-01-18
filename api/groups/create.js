const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');
const crypto = require('crypto');

module.exports = async (req, res) => {
    // CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        // Auth
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const user = verifyToken(authHeader.split(' ')[1]);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const { name, members } = req.body; // members is array of userIds
        if (!name || !members || !Array.isArray(members)) {
            return res.status(400).json({ error: 'Invalid input' });
        }

        const db = await connectToDatabase();
        const groupsCollection = db.collection('groups');
        const messagesCollection = db.collection('messages');

        // Create Group
        const newGroup = {
            id: crypto.randomUUID(),
            name,
            adminId: user.id,
            members: [...new Set([...members, user.id])], // Ensure creator is included and uniques
            avatar: `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=random`,
            created: Date.now()
        };

        await groupsCollection.insertOne(newGroup);

        // Optional: Add a system message "Group created"
        const sysMsg = {
            id: crypto.randomUUID(),
            senderId: 'system',
            senderName: 'System',
            receiverId: newGroup.id,
            content: `Group "${name}" created by ${user.username}`,
            type: 'system',
            timestamp: Date.now(),
            status: 'seen'
        };
        await messagesCollection.insertOne(sysMsg);

        res.status(201).json(newGroup);
    } catch (e) {
        console.error("Create Group Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
