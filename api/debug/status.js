const admin = require('firebase-admin');

module.exports = (req, res) => {
    // Basic CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Content-Type', 'application/json');

    const envVar = process.env.FIREBASE_SERVICE_ACCOUNT;

    const report = {
        hasEnvVar: !!envVar,
        envVarLength: envVar ? envVar.length : 0,
        isJSON: false,
        hasPrivateKey: false,
        keyLength: 0,
        keyHasNewlines: false,
        firebaseApps: admin.apps.length,
        nodeVersion: process.version
    };

    if (envVar) {
        try {
            const parsed = JSON.parse(envVar);
            report.isJSON = true;
            report.project_id = parsed.project_id;
            report.hasPrivateKey = !!parsed.private_key;

            if (parsed.private_key) {
                report.keyLength = parsed.private_key.length;
                report.keyHasNewlines = parsed.private_key.includes('\n');
                report.keyHasLiteralNewlines = parsed.private_key.includes('\\n');
            }
        } catch (e) {
            report.parseError = e.message;
        }
    }

    res.json(report);
};
