const { connectToDatabase } = require('../utils/db');
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

    try {
        // Auth
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const token = authHeader.split(' ')[1];
        const user = verifyToken(token);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const { content, type, receiverId } = req.body;
        if (!content) return res.status(400).json({ error: 'Content required' });

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');
        const messagesCollection = db.collection('messages');

        const fullUser = await usersCollection.findOne({ id: user.id });

        const message = {
            id: crypto.randomUUID(),
            senderId: user.id,
            senderName: fullUser ? fullUser.name : user.username,
            avatar: fullUser ? fullUser.avatar : '',
            content,
            type: type || 'text', // text, image, video
            replyToId: req.body.replyToId || null, // New: Reply Support
            receiverId: receiverId || 'general', // Default to general group
            status: 'sent', // sent, delivered, seen
            timestamp: Date.now()
        };

        await messagesCollection.insertOne(message);

        // Send Push Notification
        if (receiverId !== 'general') {
            try {
                const receiver = await usersCollection.findOne({ id: receiverId });
                if (receiver && receiver.pushToken) {
                    const { sendPushNotification } = require('../utils/firebase');
                    const title = message.senderName;
                    const body = type === 'image' ? 'Sent an image' : content;
                    // Don't await, run in bg
                    sendPushNotification(receiver.pushToken, title, body,
                        { chatId: String(message.senderId) },
                        {
                            android: {
                                notification: {
                                    channelId: 'message_channel',
                                    priority: 'high',
                                    defaultSound: true,
                                    defaultVibrateTimings: true
                                }
                            }
                        }
                    ).catch(e => console.error("Push Error", e));
                }
            } catch (notifyErr) {
                console.error("Notification Logic Error:", notifyErr);
            }
        }

        res.status(201).json(message);
    } catch (e) {
        console.error("Send Message Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
