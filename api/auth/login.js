const { connectToDatabase } = require('../utils/db');
const { generateToken } = require('../utils/auth');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');

module.exports = async (req, res) => {
    // ... (CORS headers omitted for brevity in thought, but must be included in replacement)
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
    res.setHeader(
        'Access-Control-Allow-Headers',
        'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version'
    );

    if (req.method === 'OPTIONS') {
        res.status(200).end();
        return;
    }

    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { username, password } = req.body;
        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        const user = await usersCollection.findOne({ username });
        if (!user) return res.status(401).json({ error: 'Invalid credentials' });

        let isMatch = false;
        try {
            isMatch = await bcrypt.compare(password, user.password);
        } catch (e) {
            // Not a bcrypt hash, or comparison failed
        }

        if (!isMatch) {
            // Try Legacy SHA256 (for accounts created before migration)
            const oldHash = crypto.createHash('sha256').update(password).digest('hex');
            if (user.password === oldHash) {
                // Verified legacy password! Upgrade to Bcrypt now.
                const newHash = await bcrypt.hash(password, 10);
                await usersCollection.updateOne({ _id: user._id }, { $set: { password: newHash } });
                isMatch = true;
                console.log(`User ${username} migrated to Bcrypt on login.`);
            }
        }

        if (!isMatch) {
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        const token = generateToken(user);
        res.status(200).json({ token, user: { id: user.id, username: user.username, name: user.name, avatar: user.avatar } });
    } catch (e) {
        console.error("Login Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
