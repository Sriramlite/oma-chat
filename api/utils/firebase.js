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
                // Strip surrounding single or double quotes if present (common .env mistake)
                let rawJson = process.env.FIREBASE_SERVICE_ACCOUNT.trim();
                if ((rawJson.startsWith("'") && rawJson.endsWith("'")) ||
                    (rawJson.startsWith('"') && rawJson.endsWith('"'))) {
                    rawJson = rawJson.slice(1, -1);
                }
                serviceAccount = JSON.parse(rawJson);
                if (serviceAccount.private_key) {
                    // Handle both single-escaped \n and double-escaped \\n
                    serviceAccount.private_key = serviceAccount.private_key
                        .replace(/\\\\n/g, '\n')  // double-escaped: \\n -> newline
                        .replace(/\\n/g, '\n');    // single-escaped: \n -> newline
                }
            } catch (parseErr) {
                initError = "Configuration Error: Invalid JSON in FIREBASE_SERVICE_ACCOUNT. Raw: " + 
                    (process.env.FIREBASE_SERVICE_ACCOUNT || '').substring(0, 50);
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

async function sendPushNotification(token, title, body, data = {}, options = {}, db = null) {
    const status = initFirebase();
    if (!status.success) {
        throw new Error(status.error || "Firebase not initialized");
    }

    try {
        // FCM requires all data values to be strings
        const stringifiedData = {};
        for (const [key, val] of Object.entries(data || {})) {
            stringifiedData[key] = String(val);
        }

        const message = {
            notification: {
                title: title,
                body: body
            },
            data: stringifiedData,
            token: token,
            ...options // Mix in android/apns specific options
        };

        const response = await admin.messaging().send(message);
        console.log(`[FCM SUCCESS] Message ID: ${response} -> Token: ${token.substring(0, 5)}... -> Tag: ${options?.android?.notification?.tag || 'none'}`);
        return { success: true, response };
    } catch (e) {
        console.error("Error sending notification:", e.code || e.message);
        
        // AUTO-PRUNE INVALID TOKENS
        if (db && (e.code === 'messaging/registration-token-not-registered' || e.message.includes('not-registered'))) {
            console.log(`[FCM] Token ${token.substring(0, 10)}... is invalid. Pruning from DB.`);
            try {
                await db.collection('users').updateMany(
                    { pushToken: token },
                    { $unset: { pushToken: "" } }
                );
            } catch (pruneErr) {
                console.error("[FCM] Failed to prune token:", pruneErr.message);
            }
        }
        
        throw e;
    }
}

module.exports = { sendPushNotification, initFirebase };
