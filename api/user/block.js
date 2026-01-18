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

        const { userId, action } = req.body; // action: 'block' or 'unblock'

        if (!userId || !action) return res.status(400).json({ error: 'Missing fields' });

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        let updateOp = {};
        if (action === 'block') {
            updateOp.$addToSet = { blockedUsers: userId };
        } else if (action === 'unblock') {
            updateOp.$pull = { blockedUsers: userId };
        } else {
            return res.status(400).json({ error: 'Invalid action' });
        }

        await usersCollection.updateOne({ id: user.id }, updateOp);

        // Fetch updated list to return
        const updatedUser = await usersCollection.findOne({ id: user.id }, { projection: { blockedUsers: 1 } });

        res.status(200).json({ success: true, blockedUsers: updatedUser.blockedUsers || [] });

    } catch (e) {
        console.error("Block API Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
