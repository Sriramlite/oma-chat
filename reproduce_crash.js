const { generateToken } = require('./api/utils/auth');
const fetch = require('node-fetch'); // Assuming node-fetch is available or using native fetch in Node 18+

const mockUser = { id: 'test_user_id', username: 'test_user' };
const token = generateToken(mockUser);

console.log("Generated Token:", token);

async function triggerError() {
    try {
        console.log("Sending POST to /api/user/test-push...");
        const res = await fetch('http://localhost:3001/api/user/test-push', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            }
        });
        console.log("Status:", res.status);
        const text = await res.text();
        console.log("Body:", text);
    } catch (e) {
        console.error("Fetch Failed:", e);
    }
}

// Wait for server to be ready if running simultaneously,
// but assumed server is running separately.
triggerError();
