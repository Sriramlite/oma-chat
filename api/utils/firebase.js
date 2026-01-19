const admin = require('firebase-admin');

let isInitialized = false;
let initError = null;

function initFirebase() {
    if (isInitialized) return { success: true };
    if (initError) return { success: false, error: initError };

    try {
        let serviceAccount;

        // PRIORITIZE INDIVIDUAL ENV VARS (Matches Render Setup)
        if (process.env.FIREBASE_PROJECT_ID &&
            process.env.FIREBASE_CLIENT_EMAIL &&
            process.env.FIREBASE_PRIVATE_KEY) {

            console.log("Using INDIVIDUAL Firebase Environment Variables");

            const privateKey = process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n');

            serviceAccount = {
                projectId: process.env.FIREBASE_PROJECT_ID,
                clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
                privateKey: privateKey
            };

        } else if (process.env.FIREBASE_SERVICE_ACCOUNT) {
            // FALLBACK TO JSON STRING
            console.log("Using JSON Firebase Environment Variable");
            try {
                serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
                if (serviceAccount.private_key) {
                    serviceAccount.private_key = serviceAccount.private_key.replace(/\\n/g, '\n');
                }
            } catch (parseErr) {
                initError = "Configuration Error: Invalid JSON in FIREBASE_SERVICE_ACCOUNT.";
                console.error(parseErr);
                return { success: false, error: initError };
            }
        } else {
            initError = "Configuration Error: Missing Firebase Credentials (FIREBASE_PRIVATE_KEY etc).";
            console.warn(initError);
            return { success: false, error: initError };
        }

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

async function sendPushNotification(token, title, body, data = {}, options = {}) {
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
            token: token,
            ...options // Mix in android/apns specific options
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
