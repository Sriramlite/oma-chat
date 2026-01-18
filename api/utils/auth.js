const jwt = require('jsonwebtoken');

const SECRET = 'oma-secret-key-123'; // In prod, use env var

function generateToken(user) {
    return jwt.sign({ id: user.id, username: user.username }, SECRET, { expiresIn: '1d' });
}

function verifyToken(token) {
    try {
        return jwt.verify(token, SECRET);
    } catch (e) {
        return null;
    }
}

module.exports = { generateToken, verifyToken };
