const { MongoClient } = require('mongodb');

let dbInstance = null;
const client = new MongoClient(process.env.MONGODB_URI || "mongodb://localhost:27017/oma_local_test");

async function connectToDatabase() {
    if (dbInstance) {
        return dbInstance;
    }

    try {
        await client.connect();
        dbInstance = client.db(); // Uses the database name from the connection string
        console.log("Connected to MongoDB");
        return dbInstance;
    } catch (error) {
        console.error("MongoDB Connection Error:", error);
        throw error;
    }
}

function getDb() {
    if (!dbInstance) {
        throw new Error("Database not initialized. Call connectToDatabase first.");
    }
    return dbInstance;
}

module.exports = { connectToDatabase, getDb, client };
