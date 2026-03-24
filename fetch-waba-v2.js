require('dotenv').config();
const apiKey = process.env.FAST2SMS_API_KEY;

async function fetchDetails() {
    const endpoints = [
        'https://www.fast2sms.com/dev/whatsapp/get-waba-details',
        'https://www.fast2sms.com/dev/whatsapp/v1/get-waba-details'
    ];

    for (const url of endpoints) {
        console.log(`Fetching from: ${url}`);
        try {
            const res = await fetch(url, {
                headers: { 'Authorization': apiKey }
            });
            const data = await res.json();
            console.log("Response:", JSON.stringify(data, null, 2));
        } catch (e) {
            console.error("Error:", e.message);
        }
    }
}

fetchDetails();
