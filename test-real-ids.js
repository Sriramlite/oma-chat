require('dotenv').config();
const apiKey = process.env.FAST2SMS_API_KEY;
const phoneId = process.env.FAST2SMS_WA_PHONE_NUMBER_ID;
const fast2smsMessageId = "15830"; // payment_completed
const recipient = "916383194408";

const tests = [
    {
        url: `https://www.fast2sms.com/dev/whatsapp?authorization=${apiKey}&message_id=${fast2smsMessageId}&phone_number_id=${phoneId}&numbers=${recipient}&variables_values=123456`,
        method: 'GET'
    },
    {
        url: `https://www.fast2sms.com/dev/whatsapp/v1/message/template`,
        method: 'POST',
        body: {
            message_id: fast2smsMessageId,
            phone_number_id: phoneId,
            numbers: recipient,
            variables_values: "123456"
        }
    },
     {
        url: `https://www.fast2sms.com/dev/whatsapp/v24.0/${phoneId}/messages`,
        method: 'POST',
        body: {
            messaging_product: "whatsapp",
            to: recipient,
            type: "template",
            template: {
                name: "payment_completed",
                language: { code: "en" },
                components: [{ type: "body", parameters: [{ type: "text", text: "123456" }]}]
            }
        }
    }
];

async function runTests() {
    for (const test of tests) {
        console.log(`Testing ${test.method}: ${test.url}`);
        try {
            const options = {
                method: test.method,
                headers: { 'authorization': apiKey }
            };
            if (test.method === 'POST') {
                options.headers['Content-Type'] = 'application/json';
                options.body = JSON.stringify(test.body);
            }
            const res = await fetch(test.url, options);
            console.log(`Status: ${res.status}`);
            const text = await res.text();
            console.log(`Response: ${text.substring(0, 200)}`);
        } catch (e) {
            console.error(`Error: ${e.message}`);
        }
        console.log("---");
    }
}

runTests();
