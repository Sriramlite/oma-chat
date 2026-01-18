const admin = require('firebase-admin');

let isInitialized = false;
let initError = null;

function initFirebase() {
    if (isInitialized) return { success: true };
    // If it failed before, return the same error (don't retry endlessly)
    if (initError) return { success: false, error: initError };

    try {
        const serviceAccountStr = process.env.FIREBASE_SERVICE_ACCOUNT;

        if (!serviceAccountStr) {
            initError = "Configuration Error: FIREBASE_SERVICE_ACCOUNT is missing.";
            console.warn(initError);
            return { success: false, error: initError };
        }

        let serviceAccount;
        try {
            serviceAccount = JSON.parse(serviceAccountStr);
        } catch (parseErr) {
            initError = "Configuration Error: Invalid JSON in FIREBASE_SERVICE_ACCOUNT. Check quotes or copying.";
            console.error(parseErr);
            return { success: false, error: initError };
        }

        // --- FIX FOR RENDER/ENV VARS ---
        // Newlines often get mangled as literal "\n" strings.
        // We must convert them back to real newlines for the Private Key to work.
        if (serviceAccount.private_key) {
            serviceAccount.private_key = serviceAccount.private_key.replace(/\\n/g, '\n');
        }
        // -------------------------------

        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });

        isInitialized = true;
        console.log("Firebase Admin Initialized Successfully!");
        return { success: true };

    } catch (e) {
        initError = "Firebase Initialization Failed: " + e.message;
        console.error(initError);
        return { success: false, error: initError };
    }
}

async function sendPushNotification(token, title, body, data = {}) {
    const status = initFirebase();
    if (!status.success) {
        throw new Error(status.error || "Firebase not initialized");
    }

    try {
        const message = {
            notification: {
                title: title,
                body: body
            },
            data: data,
            token: token
        };

        const response = await admin.messaging().send(message);
        console.log("Notification sent:", response);
        return { success: true, response };
    } catch (e) {
        console.error("Error sending notification:", e);
        // Throwing here allows test-push.js to catch and report it
        throw e;
    }
}

module.exports = { sendPushNotification };
