<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Features - OMA Messaging</title>
    <link rel="stylesheet" href="style.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;700;800&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link href="https://unpkg.com/aos@2.3.1/dist/aos.css" rel="stylesheet">
</head>
<body>
    <div class="gradient-bg"></div>
    <div class="floating-orb orb-primary"></div>
    <div class="floating-orb orb-secondary"></div>

    <nav>
        <div class="logo">OMA</div>
        <ul class="nav-links">
            <li><a href="index.php">Home</a></li>
            <li><a href="features.php">Features</a></li>
            <li><a href="about.php">About</a></li>
            <li><a href="assets/oma-app.apk" download><i class="fab fa-android"></i> Download</a></li>
        </ul>
        <div class="nav-cta">
            <div class="status-check">
                <div id="status-dot" class="status-dot"></div>
                <span id="status-text">Checking...</span>
            </div>
            <a href="https://api.pdktdev.in/" target="_blank" class="btn btn-primary">Launch App</a>
        </div>
    </nav>

    <header class="page-header" data-aos="fade-up">
        <h1>Powerful Features</h1>
        <p>Explore the innovative technologies that make OMA the ultimate messaging platform for modern users.</p>
    </header>

    <section class="features-detailed">
        <div class="feature-row">
            <div class="feature-info" data-aos="fade-right">
                <h2>Real-Time Messaging</h2>
                <p>Experience sub-second message delivery powered by Socket.io. Our architecture ensures that your conversations flow naturally without any lag.</p>
                <ul class="feature-list">
                    <li><i class="fas fa-check-circle"></i> Instant delivery notifications</li>
                    <li><i class="fas fa-check-circle"></i> Real-time typing indicators</li>
                    <li><i class="fas fa-check-circle"></i> Online/Offline presence tracking</li>
                </ul>
            </div>
            <div class="feature-visual" data-aos="fade-left">
                <i class="fas fa-bolt"></i>
            </div>
        </div>

        <div class="feature-row">
            <div class="feature-info" data-aos="fade-left">
                <h2>Voice & Video Calls</h2>
                <p>High-definition WebRTC peer-to-peer calling allows you to stay connected with crystal clear quality, even on low-bandwidth networks.</p>
                <ul class="feature-list">
                    <li><i class="fas fa-check-circle"></i> Low-latency P2P connection</li>
                    <li><i class="fas fa-check-circle"></i> Background call notifications</li>
                    <li><i class="fas fa-check-circle"></i> Screen sharing support (Desktop)</li>
                </ul>
            </div>
            <div class="feature-visual" data-aos="fade-right">
                <i class="fas fa-video"></i>
            </div>
        </div>

        <div class="feature-row">
            <div class="feature-info" data-aos="fade-right">
                <h2>Security & Privacy</h2>
                <p>We prioritize your data security using industry-standard Firebase Authentication and secure server-side message vaults.</p>
                <ul class="feature-list">
                    <li><i class="fas fa-check-circle"></i> End-to-end data encryption</li>
                    <li><i class="fas fa-check-circle"></i> Multi-factor authentication</li>
                    <li><i class="fas fa-check-circle"></i> Private media storage</li>
                </ul>
            </div>
            <div class="feature-visual" data-aos="fade-left">
                <i class="fas fa-shield-halved"></i>
            </div>
        </div>

        <div class="feature-row">
            <div class="feature-info" data-aos="fade-left">
                <h2>Cross-Platform Support</h2>
                <p>One code, multiple platforms. Thanks to Capacitor, OMA works seamlessly on Android, iOS, and all modern web browsers.</p>
                <ul class="feature-list">
                    <li><i class="fas fa-check-circle"></i> Native mobile experience</li>
                    <li><i class="fas fa-check-circle"></i> Progressive Web App (PWA)</li>
                    <li><i class="fas fa-check-circle"></i> Desktop application ready</li>
                </ul>
                <a href="assets/oma-app.apk" class="btn btn-primary" style="margin-top:2rem;" download><i class="fab fa-android"></i> Download APK</a>
            </div>
            <div class="feature-visual" data-aos="fade-right">
                <i class="fas fa-mobile-screen-button"></i>
            </div>
        </div>
    </section>

    <footer>
        <p>&copy; <?php echo date("Y"); ?> OMA - Online Messaging Application. All rights reserved.</p>
        <p style="margin-top: 10px; font-size: 1rem; color: var(--text-main);">Created and Owned by <a href="https://pdktdev.in" target="_blank" style="color: var(--primary); text-decoration: none; font-weight: 700;">Pdktdev.in</a></p>
    </footer>

    <script src="https://unpkg.com/aos@2.3.1/dist/aos.js"></script>
    <script>
        AOS.init({ duration: 1000, once: true });

        const API_URL = 'https://api.pdktdev.in/api';
        const statusDot = document.getElementById('status-dot');
        const statusText = document.getElementById('status-text');

        async function checkServerStatus() {
            try {
                await fetch(API_URL + '/user/list', { mode: 'no-cors' }); 
                statusDot.className = 'status-dot online';
                statusText.innerText = 'Server Online';
                statusText.style.color = '#10b981';
            } catch (error) {
                statusDot.className = 'status-dot offline';
                statusText.innerText = 'Server Offline';
                statusText.style.color = '#ef4444';
            }
        }
        checkServerStatus();
        setInterval(checkServerStatus, 30000);
    </script>
</body>
</html>
