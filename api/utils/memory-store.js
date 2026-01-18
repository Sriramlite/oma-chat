// In-memory store for ephemeral data like typing status
// This works because Node.js requires are cached and the server process is persistent
const store = {
    // Structure: { chatId: { userId: timestamp } }
    typing: {}
};

module.exports = store;
