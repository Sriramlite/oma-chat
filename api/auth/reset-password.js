const { connectToDatabase } = require('../utils/db');
const bcrypt = require('bcryptjs');

module.exports = async (req, res) => {
    // Enable CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { username, otp, newPassword } = req.body;
        if (!username || !otp || !newPassword) return res.status(400).json({ error: 'Missing fields' });

        if (newPassword.length < 8) {
            return res.status(400).json({ error: 'Password must be at least 8 characters long' });
        }

        const db = await connectToDatabase();
        const otpsCollection = db.collection('otps');
        const usersCollection = db.collection('users');

        const record = await otpsCollection.findOne({ username, otp });
        if (!record) {
            return res.status(401).json({ error: 'Invalid or expired OTP' });
        }

        // Check expiry (10 mins)
        const now = new Date();
        const diff = (now - record.createdAt) / 1000 / 60;
        if (diff > 10) {
            await otpsCollection.deleteOne({ username });
            return res.status(401).json({ error: 'OTP expired' });
        }

        // Hash and Update
        const salt = await bcrypt.genSalt(10);
        const hashedPassword = await bcrypt.hash(newPassword, salt);

        await usersCollection.updateOne(
            { username },
            { $set: { password: hashedPassword } }
        );

        // Cleanup
        await otpsCollection.deleteOne({ username });

        res.status(200).json({ message: 'Password reset successfully' });

    } catch (e) {
        console.error("Reset Password Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
