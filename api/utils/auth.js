const jwt = require('jsonwebtoken');

const SECRET = process.env.JWT_SECRET;

if (!SECRET) {
    console.error("[Auth] CRITICAL: JWT_SECRET is not defined in environment variables!");
} else {
    // Log first and last 3 chars of secret for verification in logs (safe for debugging)
    const masked = SECRET.length > 8 ? `${SECRET.slice(0, 3)}...${SECRET.slice(-3)}` : "***";
    console.log(`[Auth] JWT Secret Loaded: ${masked}`);
}

function generateToken(user) {
    if (!SECRET) throw new Error("JWT_SECRET missing");
    return jwt.sign({ id: user.id, username: user.username }, SECRET, { expiresIn: '7d' });
}

function verifyToken(token) {
    if (!SECRET) return null;
    try {
        return jwt.verify(token, SECRET);
    } catch (e) {
        // console.error("[Auth] Token verification failed:", e.message);
        return null;
    }
}

async function isContact(targetId, requesterId, db) {
    if (!targetId || !requesterId) return false;
    if (targetId === requesterId) return true;

    // A contact is someone you have exchanged messages with
    const conversation = await db.collection('messages').findOne({
        $or: [
            { senderId: targetId, receiverId: requesterId },
            { senderId: requesterId, receiverId: targetId }
        ]
    });
    return !!conversation;
}

async function checkPrivacy(targetUser, requesterId, db, field) {
    if (!targetUser) return false;
    if (targetUser.id === requesterId) return true; // Self always has access

    const settings = targetUser.settings || {};
    const privacySetting = settings[`${field}Privacy`] || 'everyone';

    if (privacySetting === 'everyone') return true;
    if (privacySetting === 'nobody') return false;
    if (privacySetting === 'contacts') {
        return await isContact(targetUser.id, requesterId, db);
    }
    return true;
}

async function verifyAdmin(userId, db) {
    if (!userId) return false;
    const user = await db.collection('users').findOne({ id: userId });
    return !!(user && user.isAdmin);
}

module.exports = { generateToken, verifyToken, isContact, checkPrivacy, verifyAdmin };
