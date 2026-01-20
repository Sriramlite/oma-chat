const path = require('path');
try {
    console.log("Attempting to require api/user/test-push.js...");
    const testPush = require('./api/user/test-push.js');
    console.log("Success:", testPush);
} catch (e) {
    console.error("Require Failed:", e);
}
