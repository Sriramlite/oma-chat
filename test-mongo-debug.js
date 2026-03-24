require('dotenv').config();
const { MongoClient } = require('mongodb');

async function run() {
    const client = new MongoClient(process.env.MONGODB_URI, {
        serverSelectionTimeoutMS: 5000
    });
    
    client.on('serverHeartbeatFailed', event => {
        console.log('Heartbeat Failed:', event.failure.message);
    });

    try {
        await client.connect();
    } catch(e) {}
    
    await client.close();
}
run();
