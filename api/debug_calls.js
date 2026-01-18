const fs = require('fs');
const path = require('path');

const dbPath = path.join(__dirname, 'data', 'db.json');
try {
    const data = fs.readFileSync(dbPath, 'utf8');
    const db = JSON.parse(data);

    console.log("Total Messages:", db.messages.length);
    const callLogs = db.messages.filter(m => m.type === 'call_log');
    console.log("Call Logs Found:", callLogs.length);

    if (callLogs.length > 0) {
        console.log("First Call Log:", JSON.stringify(callLogs[0], null, 2));
    } else {
        console.log("No call logs found. Attempting to inject one...");
        const testLog = {
            id: 'test-log-' + Date.now(),
            senderId: db.users[0]?.id || 'u1',
            senderName: 'System Test',
            content: 'Missed Call',
            type: 'call_log',
            receiverId: db.users[0]?.id || 'u1', // Self-log for testing
            status: 'seen',
            timestamp: Date.now()
        };
        db.messages.push(testLog);
        fs.writeFileSync(dbPath, JSON.stringify(db, null, 2));
        console.log("Injected test call log.");
    }

} catch (e) {
    console.error("Error:", e.message);
}
