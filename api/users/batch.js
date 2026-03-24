const { connectToDatabase } = require('../utils/db');
const { verifyToken, checkPrivacy } = require('../utils/auth');

module.exports = async (req, res) => {
    // ... (CORS headers stay same)
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,POST');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const requesterPayload = verifyToken(authHeader.split(' ')[1]);
        if (!requesterPayload) return res.status(401).json({ error: 'Invalid Token' });

        const { ids } = req.body;
        if (!Array.isArray(ids)) return res.status(400).json({ error: 'Invalid IDs' });

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');

        const rawUsers = await usersCollection.find({ id: { $in: ids } })
            .project({
                id: 1,
                name: 1,
                username: 1,
                avatar: 1,
                lastSeen: 1,
                bio: 1,
                battery: 1,
                phone: 1,
                settings: 1
            })
            .toArray();

        // Apply Privacy Filtering
        const filteredUsers = await Promise.all(rawUsers.map(async (u) => {
            const canSeeAvatar = await checkPrivacy(u, requesterPayload.id, db, 'profilePhoto');
            const canSeeBio = await checkPrivacy(u, requesterPayload.id, db, 'about');
            const canSeeLastSeen = await checkPrivacy(u, requesterPayload.id, db, 'lastSeen');

            return {
                id: u.id,
                name: u.name,
                username: u.username,
                avatar: canSeeAvatar ? u.avatar : 'https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png',
                bio: canSeeBio ? u.bio : '',
                lastSeen: canSeeLastSeen ? u.lastSeen : null,
                battery: canSeeLastSeen ? u.battery : null, // Battery tied to status/lastseen privacy
                status: u.status // Online status check could be more granular, but usually tied to lastseen
            };
        }));

        res.status(200).json(filteredUsers);
    } catch (e) {
        console.error("Batch Users Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
