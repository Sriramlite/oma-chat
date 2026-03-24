const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');
const bcrypt = require('bcryptjs');

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

    if (newPassword.length < 8) {
        return res.status(400).json({ error: 'New password must be at least 8 characters long' });
    }

    try {
        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        const user = await usersCollection.findOne({ id: userPayload.id });
        if (!user) return res.status(404).json({ error: 'User not found' });

        // Verify old password
        let isMatch = false;
        try {
            isMatch = await bcrypt.compare(oldPassword, user.password);
        } catch (e) {}

        if (!isMatch) {
            const oldHash = require('crypto').createHash('sha256').update(oldPassword).digest('hex');
            if (user.password === oldHash) {
                isMatch = true;
                console.log(`User ${user.id} legacy password verified for change.`);
            }
        }

        if (!isMatch) {
            return res.status(401).json({ error: 'Incorrect old password' });
        }

        // Hash and update
        const salt = await bcrypt.genSalt(10);
        const hashedNew = await bcrypt.hash(newPassword, salt);

        await usersCollection.updateOne(
            { id: user.id },
            { $set: { password: hashedNew } }
        );

        res.status(200).json({ message: 'Password updated successfully' });
    } catch (e) {
        console.error("Change Password Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
