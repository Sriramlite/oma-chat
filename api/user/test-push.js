const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');
const { sendPushNotification } = require('../utils/firebase');

module.exports = async (req, res) => {
    // CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const token = authHeader.split(' ')[1];
        const user = verifyToken(token);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const db = await connectToDatabase();
        const userData = await db.collection('users').findOne({ id: user.id });

        if (!userData || !userData.pushToken) {
            return res.status(404).json({ error: 'No Push Token found for this user in database.' });
        }

        console.log(`Sending Test Push to ${user.username} (Token: ${userData.pushToken.substring(0, 10)}...)`);

        // Send Test Push
        await sendPushNotification(
            userData.pushToken,
            "OMA Test Notification 🔔",
            "FCM Push is working perfectly on your device!",
            {
                type: 'test',
                senderId: user.id,
                senderName: 'OMA System'
            },
            {
                android: {
                    priority: 'high',
                    notification: {
                        channelId: 'chat_messages_channel',
                        priority: 'max',
                        sound: 'message',
                        visibility: 'public'
                    }
                }
            },
            db
        );

        res.json({ success: true, message: 'Test Notification Sent Successfully' });

    } catch (e) {
        console.error("Test Push Error:", e);
        res.status(500).json({
            error: `Push failed: ${e.message}`,
            details: e.message
        });
    }
};
