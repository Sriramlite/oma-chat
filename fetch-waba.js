require('dotenv').config();
const apiKey = process.env.FAST2SMS_API_KEY;

async function getWabaDetails() {
    console.log("Fetching WABA details...");
    try {
        // Fast2SMS WhatsApp API usually requires authorization in headers
        const res = await fetch('https://www.fast2sms.com/dev/whatsapp/get-waba-details', {
            method: 'GET',
            headers: {
                'authorization': apiKey
            }
        });
        const data = await res.json();
        console.log("WABA Data:", JSON.stringify(data, null, 2));
    } catch (e) {
        console.error("Error fetching WABA details:", e);
    }
}

getWabaDetails();
