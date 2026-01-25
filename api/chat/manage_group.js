const { connectToDatabase } = require('../utils/db');
const { verifyToken } = require('../utils/auth');

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
        const user = verifyToken(authHeader.split(' ')[1]);
        if (!user) return res.status(401).json({ error: 'Invalid Token' });

        const { groupId, memberId, action } = req.body; // action: 'add' or 'remove'

        if (!groupId || !action) return res.status(400).json({ error: 'Missing fields' });

        const db = await connectToDatabase();
        const groupsCollection = db.collection('groups');

        const group = await groupsCollection.findOne({ id: groupId });
        if (!group) return res.status(404).json({ error: 'Group not found' });

        // Logic
        if (action === 'leave') {
            // User leaving themselves
            const newMembers = group.members.filter(m => m !== user.id);
            if (newMembers.length === 0) {
                await groupsCollection.deleteOne({ id: groupId }); // Delete empty group
            } else {
                let newAdminIds = (group.adminIds || []).filter(a => a !== user.id);
                // Auto-Assign Admin if no admins left
                if (newAdminIds.length === 0 && newMembers.length > 0) {
                    const randomMember = newMembers[0];
                    newAdminIds.push(randomMember);
                }
                await groupsCollection.updateOne({ id: groupId }, { $set: { members: newMembers, adminIds: newAdminIds } });
            }
            return res.status(200).json({ success: true, message: 'Left group' });
        }

        // Admin actions
        // Check Admin
        // Note: Legacy groups might have adminId string or no adminIds array.
        const admins = group.adminIds || (group.adminId ? [group.adminId] : []);
        if (!admins.includes(user.id)) {
            return res.status(403).json({ error: 'Admin only' });
        }

        if (action === 'add') {
            if (!memberId) return res.status(400).json({ error: 'Member ID required' });
            if (group.members.includes(memberId)) return res.status(400).json({ error: 'Already a member' });

            await groupsCollection.updateOne({ id: groupId }, { $push: { members: memberId } });
            return res.status(200).json({ success: true, message: 'Member added' });
        }

        if (action === 'remove') {
            if (!memberId) return res.status(400).json({ error: 'Member ID required' });
            if (memberId === user.id) return res.status(400).json({ error: 'Cannot remove yourself, use leave' });

            const newMembers = group.members.filter(m => m !== memberId);
            let newAdminIds = (group.adminIds || []).filter(a => a !== memberId);

            await groupsCollection.updateOne({ id: groupId }, { $set: { members: newMembers, adminIds: newAdminIds } });
            return res.status(200).json({ success: true, message: 'Member removed' });
        }

        if (action === 'promote') {
            if (!memberId) return res.status(400).json({ error: 'Member ID required' });
            if (!group.members.includes(memberId)) return res.status(400).json({ error: 'User not in group' });

            await groupsCollection.updateOne({ id: groupId }, { $addToSet: { adminIds: memberId } });
            return res.status(200).json({ success: true, message: 'Member promoted to Admin' });
        }

        if (action === 'demote') {
            if (!memberId) return res.status(400).json({ error: 'Member ID required' });
            await groupsCollection.updateOne({ id: groupId }, { $pull: { adminIds: memberId } });
            return res.status(200).json({ success: true, message: 'Member demoted' });
        }

    } catch (e) {
        console.error(e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
