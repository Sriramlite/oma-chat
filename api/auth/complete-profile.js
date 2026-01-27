const { connectToDatabase } = require('../utils/db');
const { verifyToken, generateToken } = require('../utils/auth');

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        // Authenticate User (we expect them to have the temporary Google token)
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const userPayload = verifyToken(authHeader.split(' ')[1]);
        if (!userPayload) return res.status(401).json({ error: 'Invalid Token' });

        const { username, phone } = req.body;

        if (!username || !phone) {
            return res.status(400).json({ error: 'Username and Phone are required' });
        }

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        // 1. Check Username Uniqueness (if changed)
        // Note: The temporary username was the email.
        const existingUsername = await usersCollection.findOne({ username });
        if (existingUsername && existingUsername.id !== userPayload.id) {
            return res.status(409).json({ error: 'Username already taken' });
        }

        // 2. Check Phone Uniqueness
        // Phone might not exist on any user yet, or might be linked.
        if (phone) {
            const existingPhone = await usersCollection.findOne({ phone });
            if (existingPhone && existingPhone.id !== userPayload.id) {
                return res.status(409).json({ error: 'Phone number already linked to another account' });
            }
        }

        // 3. Update User
        await usersCollection.updateOne(
            { id: userPayload.id },
            { $set: { username, phone } }
        );

        // 4. Return Updated User & Token (Token claims might need update if username changed)
        const updatedUser = await usersCollection.findOne({ id: userPayload.id });
        const newToken = generateToken(updatedUser);

        res.status(200).json({
            token: newToken,
            user: {
                id: updatedUser.id,
                username: updatedUser.username,
                name: updatedUser.name,
                avatar: updatedUser.avatar,
                phone: updatedUser.phone
            }
        });

    } catch (e) {
        console.error("Complete Profile Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
