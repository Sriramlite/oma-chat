const { readDb, writeDb } = require('../utils/json-db');
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

    // Auth
    const authHeader = req.headers.authorization;
    if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
    const token = authHeader.split(' ')[1];
    const userPayload = verifyToken(token);
    if (!userPayload) return res.status(401).json({ error: 'Invalid Token' });

    const { oldPassword, newPassword } = req.body;
    if (!oldPassword || !newPassword) return res.status(400).json({ error: 'Missing fields' });

    const db = readDb();
    const userIndex = db.users.findIndex(u => u.id === userPayload.id);

    if (userIndex === -1) return res.status(404).json({ error: 'User not found' });

    const user = db.users[userIndex];
    const hashedOld = crypto.createHash('sha256').update(oldPassword).digest('hex');

    if (user.password !== hashedOld) {
        return res.status(401).json({ error: 'Incorrect old password' });
    }

    const hashedNew = crypto.createHash('sha256').update(newPassword).digest('hex');
    db.users[userIndex].password = hashedNew;

    writeDb(db);

    res.status(200).json({ message: 'Password updated successfully' });
};
