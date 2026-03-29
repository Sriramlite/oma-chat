const { connectToDatabase } = require('../../api/utils/db');
const { verifyToken } = require('../../api/utils/auth');

module.exports = async (req, res) => {
    // 1. CORS & Auth
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();

    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) return res.status(401).json({ error: 'Unauthorized' });
        const user = verifyToken(authHeader.split(' ')[1]);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const db = await connectToDatabase();
        const messagesCollection = db.collection('messages');

        // AGGREGATION PIPELINE: High Performance & Low Memory
        const pipeline = [
            { 
                $match: { 
                    $or: [{ senderId: user.id }, { receiverId: user.id }], 
                    receiverId: { $ne: 'general' } 
                } 
            },
            { $sort: { timestamp: -1 } },
            { 
                $group: {
                    _id: { $cond: [ { $eq: ["$senderId", user.id] }, "$receiverId", "$senderId" ] },
                    lastMsgObj: { $first: "$$ROOT" }
                }
            },
            {
                $lookup: {
                    from: "users",
                    localField: "_id",
                    foreignField: "id",
                    as: "partner"
                }
            },
            { $unwind: "$partner" },
            { 
                $project: {
                    _id: 0,
                    id: "$_id",
                    lastMsg: { 
                        $cond: [ 
                            { $eq: ["$lastMsgObj.type", "text"] }, 
                            "$lastMsgObj.content", 
                            { $cond: [ { $eq: ["$lastMsgObj.type", "image"] }, "Image", "Media" ] } 
                        ] 
                    },
                    timestamp: "$lastMsgObj.timestamp",
                    type: { $literal: "user" },
                    name: "$partner.name",
                    username: "$partner.username",
                    avatar: "$partner.avatar",
                    status: "$partner.status",
                    lastSeen: "$partner.lastSeen"
                }
            },
            { $sort: { timestamp: -1 } },
            { $limit: 40 } // Limit to 40 recent partners for initial load
        ];

        const results = await messagesCollection.aggregate(pipeline).toArray();
        res.status(200).json(results);
    } catch (e) {
        console.error("Chat List Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
