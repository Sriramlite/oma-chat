const admin = require('firebase-admin');

let isInitialized = false;

function initFirebase() {
    if (isInitialized) return;

    try {
        const serviceAccountStr = process.env.FIREBASE_SERVICE_ACCOUNT;

        if (!serviceAccountStr) {
            console.warn("FIREBASE_SERVICE_ACCOUNT env var missing. Notifications disabled.");
            return;
        }

        const serviceAccount = JSON.parse(serviceAccountStr);

        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });

        isInitialized = true;
        console.log("Firebase Admin Initialized!");
    } catch (e) {
        console.error("Firebase Init Error:", e);
    }
}

async function sendPushNotification(token, title, body, data = {}) {
    if (!isInitialized) initFirebase();
    if (!isInitialized) return;

    try {
        const message = {
            notification: {
                title: title,
                body: body
            },
            data: data,
            token: token
        };

        await admin.messaging().send(message);
        console.log("Notification sent successfully!");
    } catch (e) {
        console.error("Error sending notification:", e);
    }
}

module.exports = { sendPushNotification };
