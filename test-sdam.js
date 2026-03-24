const { MongoClient } = require('mongodb');
const client = new MongoClient(process.env.MONGODB_URI || 'mongodb+srv://sriramlite202179_db_user:bSycnxltby7X0HKw@atlas-2q9mzi-shard-0.d9onlz3.mongodb.net/?retryWrites=true&w=majority&appName=aiomsg', {
  serverSelectionTimeoutMS: 5000,
  monitorCommands: true
});

client.on('serverHeartbeatStarted', e => console.log('Heartbeat Started:', e.connectionId));
client.on('serverHeartbeatSucceeded', e => console.log('Heartbeat Succeeded:', e.connectionId));
client.on('serverHeartbeatFailed', e => console.log('Heartbeat Failed:', e.connectionId, e.failure.message));
client.on('topologyDescriptionChanged', e => console.log('Topology:', e.newDescription.servers.size, 'servers'));

client.connect().then(() => {
  console.log('Connected');
  process.exit(0);
}).catch(e => {
  console.error('Connection Failed:', e.message);
  process.exit(1);
});
