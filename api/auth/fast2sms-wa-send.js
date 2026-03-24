const { connectToDatabase } = require('../utils/db');
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

        const db = await connectToDatabase();
        
        // Rate limit check
        const limits = db.collection('sms_limits');
        const today = new Date().toISOString().split('T')[0];
        const record = await limits.findOne({ phone });
        if (record && record.date === today && record.count >= 10) {
            return res.status(429).json({ error: 'Daily limit reached.' });
        }

        // 1. Generate OTP
        const otp = Math.floor(100000 + Math.random() * 900000).toString();
        const expiresAt = new Date(Date.now() + 10 * 60 * 1000);

        // 2. Store OTP
        const otps = db.collection('otps');
        await otps.updateOne(
            { phone },
            { $set: { phone, otp, createdAt: new Date(), expiresAt } },
            { upsert: true }
        );

        // 3. Send via Fast2SMS
        const apiKey = process.env.FAST2SMS_API_KEY;
        const messageId = process.env.FAST2SMS_WA_MESSAGE_ID;
        const phoneNumberId = process.env.FAST2SMS_WA_PHONE_NUMBER_ID;

        if (!apiKey || !messageId || !phoneNumberId || messageId === 'YOUR_TEMPLATE_ID') {
            return res.status(500).json({ error: 'WhatsApp API not configured. Check .env' });
        }

        // Clean phone number
        let recipient = phone.replace(/\D/g, '');
        if (recipient.length === 10) {
            recipient = '91' + recipient;
        } else if (recipient.length === 11 && recipient.startsWith('0')) {
            recipient = '91' + recipient.substring(1);
        }

        // Fast2SMS WhatsApp Meta Proxy (Using v18.0 for better stability)
        const version = 'v18.0';
        const url = `https://www.fast2sms.com/dev/whatsapp/${version}/${phoneNumberId}/messages`;
        
        // Official Meta Template JSON Format
        const waPayload = {
            "messaging_product": "whatsapp",
            "recipient_type": "individual",
            "to": recipient,
            "type": "template",
            "template": {
                "name": messageId,
                "language": { "code": "en" },
                "components": [
                    {
                        "type": "body",
                        "parameters": [
                            { "type": "text", "text": otp }
                        ]
                    }
                ]
            }
        };

        // SPECIAL CASE: offer_template has 3 variables
        if (messageId === 'offer_template') {
            waPayload.template.components[0].parameters.push({ "type": "text", "text": "1" });
            waPayload.template.components[0].parameters.push({ "type": "text", "text": "Project Oma" });
        }

        console.log(`[Fast2SMS WA] Requesting ${url}...`);

        const waRes = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'authorization': apiKey
            },
            body: JSON.stringify(waPayload)
        });

        const waData = await waRes.json();
        console.log("Full Fast2SMS Response:", JSON.stringify(waData, null, 2));

        if (waData.return === false || waData.status === false || waData.error) {
            console.error("Fast2SMS Error detected:", waData);
            return res.status(500).json({ error: waData.message || 'API Error' });
        }

        // 4. Log usage
        await limits.updateOne(
            { phone },
            { $set: { phone, date: today }, $inc: { count: 1 } },
            { upsert: true }
        );

        res.status(200).json({ success: true, message: 'WhatsApp OTP sent successfully' });

    } catch (e) {
        console.error("Fast2SMS WA Backend Crash:", e);
        res.status(500).json({ error: 'Internal Server Error' });
    }
};
