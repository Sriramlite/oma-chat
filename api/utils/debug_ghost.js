const { connectToDatabase } = require('./db');

async function debugGhost() {
    try {
        console.log("Connecting to DB...");
        const db = await connectToDatabase();
        const users = db.collection('users');
        const messages = db.collection('messages');

        console.log("Searching for user 'Periyakaruppan'...");
        const ghostUser = await users.findOne({
            $or: [
                { name: { $regex: 'Periyakaruppan', $options: 'i' } },
                { username: { $regex: 'Periyakaruppan', $options: 'i' } }
            ]
        });

        if (ghostUser) {
            console.log("FOUND GHOST USER:", JSON.stringify(ghostUser, null, 2));

            console.log("Searching for messages involving this user...");
            const msgs = await messages.find({
                $or: [{ senderId: ghostUser.id }, { receiverId: ghostUser.id }]
            }).toArray();

            console.log(`Found ${msgs.length} messages.`);
            if (msgs.length > 0) {
                console.log("Sample Message:", JSON.stringify(msgs[0], null, 2));
            }
        } else {
            console.log("No user found with that name.");

            // Search messages content just in case
            console.log("Searching message content...");
            const contentMsgs = await messages.find({
                content: { $regex: 'Periyakaruppan', $options: 'i' }
            }).toArray();
            console.log(`Found ${contentMsgs.length} messages with name in content.`);
        }

    } catch (e) {
        console.error("Debug Error:", e);
        if (e.cause) console.error("Cause:", e.cause);
    }
    process.exit();
}

debugGhost();
