const { connectToDatabase } = require('../utils/db');

module.exports = async (req, res) => {
    // Enable CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { phone } = req.body;
        if (!phone) return res.status(400).json({ error: 'Phone number required' });

        const db = await connectToDatabase();
        const limits = db.collection('sms_limits');

        const today = new Date().toISOString().split('T')[0];
        const record = await limits.findOne({ phone });

        if (record && record.date === today) {
            // Increment
            await limits.updateOne({ phone }, { $inc: { count: 1 } });
        } else {
            // Create New or Reset
            // upsert: true handles both cases if we query by phone
            await limits.updateOne(
                { phone },
                { $set: { phone, date: today, count: 1 } },
                { upsert: true }
            );
        }

        res.status(200).json({ success: true });

    } catch (e) {
        console.error("SMS Log Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
