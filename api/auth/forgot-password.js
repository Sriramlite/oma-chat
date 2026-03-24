const { connectToDatabase } = require('../utils/db');
const fetch = require('node-fetch');

module.exports = async (req, res) => {
    // Enable CORS
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { username } = req.body;
        if (!username) return res.status(400).json({ error: 'Username is required' });

        const db = await connectToDatabase();
        const usersCollection = db.collection('users');
        const otpsCollection = db.collection('otps');

        const user = await usersCollection.findOne({ username });
        if (!user || !user.phone) {
            // Security: Don't leak if user exists or not, but in this case, 
            // the user needs to know they can't reset if no phone is on file.
            return res.status(404).json({ error: 'No recovery phone found for this user' });
        }

        const otp = Math.floor(100000 + Math.random() * 900000).toString();
        
        // Store OTP with 10 min expiry
        await otpsCollection.updateOne(
            { username },
            { $set: { otp, createdAt: new Date() } },
            { upsert: true }
        );

        // Send via WhatsApp (Reusing our Fast2SMS Meta Proxy logic)
        const FAST2SMS_API_KEY = process.env.FAST2SMS_API_KEY;
        const MESSAGE_ID = process.env.FAST2SMS_WA_MESSAGE_ID; // e.g. 'offer_template' or 'otp_template'
        const PHONE_NUMBER_ID = process.env.FAST2SMS_WA_PHONE_NUMBER_ID;

        const payload = {
            route: "otp",
            sender_id: MESSAGE_ID, // This is the Template Name for Meta Proxy
            message: otp,
            variables_values: otp, // For otp_template
            numbers: user.phone
        };

        // Note: For 'offer_template' which has 3 variables, we need dummy values
        if (MESSAGE_ID === 'offer_template') {
            payload.variables_values = `${otp},User,OMA`;
        }

        const response = await fetch("https://www.fast2sms.com/dev/bulkV2", {
            method: 'POST',
            headers: {
                'authorization': FAST2SMS_API_KEY,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        const result = await response.json();
        console.log("Forgot Password OTP Response:", result);

        if (result.return) {
            res.status(200).json({ message: 'OTP sent successfully to your registered WhatsApp' });
        } else {
            res.status(500).json({ error: 'Failed to send OTP: ' + (result.message || 'Unknown error') });
        }

    } catch (e) {
        console.error("Forgot Password Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
