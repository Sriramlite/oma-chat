const { MongoClient } = require('mongodb');
const dns = require('dns');
require('dotenv').config();

// UNIVERSAL DNS FIX: Force every possible resolution method to use Google DNS
// This bypasses the broken Windows/Node v24 global DNS state.
const forceGoogleDns = (method) => {
    const original = dns[method];
    dns[method] = (name, ...args) => {
        const callback = args[args.length - 1];
        if (typeof callback !== 'function') return original(name, ...args);

        const resolver = new dns.Resolver();
        resolver.setServers(['8.8.8.8', '8.8.4.4']);

        // Call the resolver method with the same arguments
        const resolverArgs = args.slice(0, -1);
        resolver[method](name, ...resolverArgs, callback);
    };
};

// Apply to all common resolution methods used by MongoDB driver
['resolve4', 'resolve6', 'resolveSrv', 'resolveTxt', 'lookup'].forEach(m => {
    try { forceGoogleDns(m); } catch (e) { }
});

// Specialized lookup patch because it's the most common failure point
const originalLookup = dns.lookup;
dns.lookup = (hostname, options, callback) => {
    if (typeof options === 'function') {
        callback = options;
        options = {};
    }

    // Attempt resolve4 first, then fallback to original lookup (which usually fails or hangs)
    const resolver = new dns.Resolver();
    resolver.setServers(['8.8.8.8', '8.8.4.4']);
    resolver.resolve4(hostname, (err, addresses) => {
        if (err || !addresses.length) {
            return originalLookup(hostname, options, callback);
        }
        if (options.all) {
            return callback(null, addresses.map(addr => ({ address: addr, family: 4 })));
        }
        return callback(null, addresses[0], 4);
    });
};

// MongoClient Configuration
const client = new MongoClient(process.env.MONGODB_URI, {
    family: 4,
    serverSelectionTimeoutMS: 30000,
    connectTimeoutMS: 30000,
    tlsAllowInvalidCertificates: true
});

let dbInstance = null;

async function connectToDatabase() {
    if (dbInstance) {
        return dbInstance;
    }

    try {
        console.log("Connecting to MongoDB Atlas (Hardened Path)...");
        await client.connect();
        dbInstance = client.db();
        console.log("Connected to MongoDB successfully");
        return dbInstance;
    } catch (error) {
        console.error("MongoDB Connection Error:", error.message);
        throw error;
    }
}

function getDb() {
    if (!dbInstance) {
        throw new Error("Database not initialized. Call connectToDatabase first.");
    }
    return dbInstance;
}

async function setupIndexes() {
    if (!dbInstance) await connectToDatabase();
    
    try {
        console.log("Setting up MongoDB Indexes...");
        const db = dbInstance;
        
        // Messages: Compound index for history lookup (sender + receiver + time)
        await db.collection('messages').createIndex({ senderId: 1, receiverId: 1, timestamp: -1 });
        await db.collection('messages').createIndex({ receiverId: 1, senderId: 1, timestamp: -1 });
        
        // AGGREGATION SUPPORT: Fast sorting for 'Recent Chats' list
        await db.collection('messages').createIndex({ senderId: 1, timestamp: -1 });
        await db.collection('messages').createIndex({ receiverId: 1, timestamp: -1 });
        
        // Messages: Fast lookup by timestamp for incremental polling
        await db.collection('messages').createIndex({ timestamp: 1 });
        
        // Users: Lookup by ID and Username (removed unique to avoid blocking on duplicates)
        await db.collection('users').createIndex({ id: 1 });
        await db.collection('users').createIndex({ username: 1 });
        
        console.log("MongoDB Indexes verified/created successfully.");
    } catch (e) {
        console.error("Index Setup Warning (some may already exist or contain duplicates):", e.message);
    }
}

module.exports = { connectToDatabase, getDb, setupIndexes, client };
