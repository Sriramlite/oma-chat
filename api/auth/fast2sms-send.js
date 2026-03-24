const { connectToDatabase } = require('../utils/db');
const checkSmsLimit = require('./check-sms-limit'); // We can reuse logic or just import
const logSms = require('./log-sms');
require('dotenv').config();

module.exports = async (req, res) => {
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { phone } = req.body;
        if (!phone) return res.status(400).json({ error: 'Phone number required' });

        // 1. Check Rate Limit (Internal Call Simulation)
        // We'll just call the functions directly since they are in the same dir
        const db = await connectToDatabase();
        
        // Manual limit check to avoid req/res mock
        const limits = db.collection('sms_limits');
        const today = new Date().toISOString().split('T')[0];
        const record = await limits.findOne({ phone });
        if (record && record.date === today && record.count >= 8) {
            return res.status(429).json({ error: 'Daily SMS limit reached. Try again tomorrow.' });
        }

        // 2. Generate 6-digit OTP
        const otp = Math.floor(100000 + Math.random() * 900000).toString();
        const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes

        // 3. Store OTP in DB
        const otps = db.collection('otps');
        await otps.updateOne(
            { phone },
            { $set: { phone, otp, createdAt: new Date(), expiresAt } },
            { upsert: true }
        );

        // Ensure TTL index exists (one-time or check)
        // db.collection('otps').createIndex({ "expiresAt": 1 }, { expireAfterSeconds: 0 });

        // 4. Send via Fast2SMS
        const apiKey = process.env.FAST2SMS_API_KEY;
        if (!apiKey || apiKey === 'YOUR_FAST2SMS_API_KEY') {
            console.error("Fast2SMS API Key not configured");
            return res.status(500).json({ error: 'SMS Gateway not configured' });
        }

        // Clean phone number: Strictly 10 digits for Indian OTP route
        let cleanPhone = phone.replace(/\D/g, '');
        if (cleanPhone.length > 10) {
            cleanPhone = cleanPhone.substring(cleanPhone.length - 10);
        }

        if (cleanPhone.length !== 10) {
            return res.status(400).json({ error: 'Please enter a valid 10-digit phone number' });
        }

        // Reverted to OTP route ('otp') for cost-efficiency (₹0.45/SMS). 
        // NOTE: Requires "Website Verification" on Fast2SMS dashboard to work.
        const url = `https://www.fast2sms.com/dev/bulkV2?authorization=${apiKey}&route=otp&variables_values=${otp}&numbers=${cleanPhone}&flash=0`;
        
        console.log(`[Fast2SMS] Sending OTP to ${cleanPhone}...`);
        const f2sRes = await fetch(url);
        const f2sData = await f2sRes.json();

        if (!f2sData.return) {
            console.error("Fast2SMS Error Response:", f2sData);
            return res.status(500).json({ error: f2sData.message || 'Failed to send SMS' });
        }

        // 5. Log SMS count
        await limits.updateOne(
            { phone },
            { $set: { phone, date: today }, $inc: { count: 1 } },
            { upsert: true }
        );

        res.status(200).json({ success: true, message: 'OTP sent successfully' });

    } catch (e) {
        console.error("Fast2SMS Send OTP Error:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
