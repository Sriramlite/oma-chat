require('dotenv').config();
const apiKey = process.env.FAST2SMS_API_KEY;
const phoneId = process.env.FAST2SMS_WA_PHONE_NUMBER_ID;

const endpoints = [
    `https://www.fast2sms.com/dev/whatsapp/v24.0/${phoneId}/messages`,
    `https://www.fast2sms.com/dev/whatsapp/message/template`,
    `https://www.fast2sms.com/dev/whatsapp/send-template`,
    `https://www.fast2sms.com/dev/whatsapp/v1/message/send_template`,
    `https://www.fast2sms.com/dev/whatsapp/v1/send-template`,
    `https://www.fast2sms.com/dev/whatsapp/v1/messages`
];

async function testEndpoints() {
    for (const url of endpoints) {
        console.log(`Testing: ${url}`);
        try {
            const res = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'authorization': apiKey
                },
                body: JSON.stringify({ test: true })
            });
            console.log(`Status: ${res.status}`);
            const text = await res.text();
            console.log(`Preview: ${text.substring(0, 100)}`);
        } catch (e) {
            console.error(`Failed: ${e.message}`);
        }
        console.log("---");
    }
}

testEndpoints();
