const { MongoClient } = require('mongodb');
const uri = "mongodb+srv://sriramlite202179_db_user:bSycnxltby7X0HKw@atlas-2q9mzi-shard-0.d9onlz3.mongodb.net/?retryWrites=true&w=majority&appName=aiomsg";

async function run() {
    console.log('Testing SRV URI...');
    const client = new MongoClient(uri, { serverSelectionTimeoutMS: 5000 });
    try {
        await client.connect();
        console.log('Connected via SRV successfully!');
        const db = client.db('test');
        console.log('Ping result:', await db.command({ ping: 1 }));
    } catch(e) {
        console.error('SRV Connection Error:', e);
    } finally {
        await client.close();
    }
}
run();
