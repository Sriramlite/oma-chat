const { MongoClient } = require('mongodb');
const uri = process.env.MONGODB_URI || 'mongodb+srv://sriramlite202179_db_user:bSycnxltby7X0HKw@atlas-2q9mzi-shard-0.d9onlz3.mongodb.net/?retryWrites=true&w=majority&appName=aiomsg';
console.log('Connecting to', uri.split('@')[1]);
const client = new MongoClient(uri, { serverSelectionTimeoutMS: 5000 });
client.connect().then(() => { console.log('success'); process.exit(0); }).catch(e => { console.error('error', e); process.exit(1); });
