const store = require('../utils/memory-store');
const { verifyToken } = require('../utils/auth');

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,GET,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();

    const authHeader = req.headers.authorization;
    if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
    const token = authHeader.split(' ')[1];
    const user = verifyToken(token);
    if (!user) return res.status(401).json({ error: 'Invalid Token' });

    // Handle POST: "I am typing"
    if (req.method === 'POST') {
        const { chatId } = req.body;
        if (!chatId) return res.status(400).json({ error: 'Chat ID required' });

        if (!store.typing[chatId]) store.typing[chatId] = {};
        store.typing[chatId][user.id] = Date.now();

        return res.status(200).json({ success: true });
    }

    // Handle GET: "Who is typing?"
    if (req.method === 'GET') {
        const { chatId } = req.query;
        if (!chatId) return res.status(400).json({ error: 'Chat ID required' });

        const chatTyping = store.typing[chatId] || {};
        const now = Date.now();
        const typingUsers = [];

        // Check for users who typed in the last 3000ms
        for (const [userId, timestamp] of Object.entries(chatTyping)) {
            if (now - timestamp < 3000 && userId !== user.id) {
                // Return userId. Frontend can resolve name from existing cache or just show "User typing"
                typingUsers.push(userId);
            }
        }

        return res.status(200).json({ typingUsers });
    }

    return res.status(405).json({ error: 'Method not allowed' });
};
