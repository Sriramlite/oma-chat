const { connectToDatabase } = require('../utils/db');

const MAX_SMS_PER_DAY = 8;

module.exports = async (req, res) => {
    // Enable CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,POST');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { phone } = req.body;
        if (!phone) return res.status(400).json({ error: 'Phone number required' });

        const db = await connectToDatabase();
        const limits = db.collection('sms_limits');

        // Get today's date string (YYYY-MM-DD)
        const today = new Date().toISOString().split('T')[0];

        const record = await limits.findOne({ phone });

        if (record) {
            // Check if date is different (Reset if needed)
            if (record.date !== today) {
                // It's a new day, reset (logic handled in log-sms, but here we approve)
                return res.status(200).json({ allowed: true });
            }

            // Check Limit
            if (record.count >= MAX_SMS_PER_DAY) {
                return res.status(429).json({
                    allowed: false,
                    error: 'Daily SMS limit reached. Please use Email Login or try again tomorrow.'
                });
            }
        }

        // Default: Allowed
        res.status(200).json({ allowed: true });

    } catch (e) {
        console.error("SMS Limit Check Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
