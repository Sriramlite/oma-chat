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
        const userPayload = verifyToken(authHeader.split(' ')[1]);
        if (!userPayload) return res.status(401).json({ error: 'Invalid Token' });

        const { name, avatar, bio, privacy, battery } = req.body;

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        const updateFields = {};
        if (name) updateFields.name = name;
        if (avatar) updateFields.avatar = avatar;
        if (bio) updateFields.bio = bio;
        if (battery) updateFields.battery = battery;

        const operations = {};
        if (Object.keys(updateFields).length > 0) {
            operations.$set = updateFields;
        }

        // Merge Settings (Deep merge is tricky in pure mongo 'set', usually requires aggregation pipeline or dot notation if we knew exact keys)
        // For simplicity, we will fetch, merge in code, and set back settings object if settings are provided.
        // OR we can use dot notation if we know the keys.
        // Let's use the fetch-merge-save approach for complex nested objects or just use specific keys if possible.
        // But wait, $set: { "settings.darkMode": ... } works great.

        if (req.body.settings) {
            const settingsUpdates = {};
            for (const key in req.body.settings) {
                settingsUpdates[`settings.${key}`] = req.body.settings[key];
            }
            if (!operations.$set) operations.$set = {};
            Object.assign(operations.$set, settingsUpdates);
        }

        if (Object.keys(operations).length > 0) {
            await usersCollection.updateOne({ id: userPayload.id }, operations);
        }

        const updatedUser = await usersCollection.findOne({ id: userPayload.id });
        if (!updatedUser) return res.status(404).json({ error: 'User not found' });

        // Real-time Broadcast
        const io = req.app.get('io');
        if (io) {
            if (battery) {
                io.emit('user_status', {
                    userId: userPayload.id,
                    battery: battery,
                });
            }

            // Broadcast Generic Profile Update (Name, Bio, Avatar)
            if (name || bio || avatar) {
                io.emit('profile_update', {
                    userId: userPayload.id,
                    name: updatedUser.name,
                    bio: updatedUser.bio,
                    avatar: updatedUser.avatar,
                    username: updatedUser.username
                });
            }
        }
        if (!updatedUser) return res.status(404).json({ error: 'User not found' });

        const { password, ...safeUser } = updatedUser;
        res.status(200).json(safeUser);

    } catch (e) {
        console.error("Update User Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
