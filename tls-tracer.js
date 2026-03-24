const tls = require('tls');
const fs = require('fs');

const logStream = fs.createWriteStream('tls-debug.log');
function log(msg) {
    console.log(msg);
    logStream.write(new Date().toISOString() + ': ' + msg + '\n');
}

log('Starting TLS connection attempt...');
const socket = tls.connect(27017, 'ac-p2ieqtf-shard-00-00.d9onlz3.mongodb.net', {
    servername: 'ac-p2ieqtf-shard-00-00.d9onlz3.mongodb.net',
    rejectUnauthorized: false
});

socket.on('secureConnect', () => {
    log('Successfully connected via TLS (unauthorized allowed)');
    log('Cipher: ' + JSON.stringify(socket.getCipher()));
    log('Protocol: ' + socket.getProtocol());
    socket.destroy();
    process.exit(0);
});

socket.on('error', (err) => {
    log('TLS Error: ' + err.message);
    log('Error Code: ' + err.code);
    if (err.stack) log('Stack: ' + err.stack);
    process.exit(1);
});

setTimeout(() => {
    log('Timeout reached (15s)');
    process.exit(2);
}, 15000);
