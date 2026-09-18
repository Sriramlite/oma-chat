<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>About OMA - Created by Pdktdev.in</title>
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
        <h1>About OMA</h1>
        <p>A mission to redefine modern digital interaction through high-performance technologies.</p>
    </header>

    <section class="about-content">
        <div class="about-card" data-aos="fade-up">
            <h2>Visionary Messaging</h2>
            <p>OMA (Online Messaging Application) was born from a desire to create a communication tool that is as fast as a text and as personal as a face-to-face conversation. We believe that technology should empower human connections, not hinder them with complexity or lag.</p>
            <p>Built with a "Privacy-First" mindset, OMA ensures that your data is handled with the highest standards of security, allowing you to connect with confidence.</p>
        </div>

        <div class="about-card" data-aos="fade-up">
            <h2>Created & Owned by Pdktdev.in</h2>
            <p>The OMA project is exclusively developed, maintained, and owned by <strong>Pdktdev.in</strong>. Our team is dedicated to pushing the boundaries of what is possible on the web and mobile platforms.</p>
            <p>At Pdktdev.in, we specialize in high-performance real-time applications, cross-platform mobile development, and modern cloud architectures. OMA is a testament to our commitment to quality and innovation.</p>
            
            <div class="branding-logo">
                <a href="https://pdktdev.in" target="_blank" style="text-decoration: none; font-size: 2.5rem; font-weight: 800; color: var(--text-main);">
                    Pdkt<span style="color: var(--primary);">dev.in</span>
                </a>
            </div>
        </div>

        <div class="about-card" data-aos="fade-up">
            <h2>The Technology Stack</h2>
            <p>To achieve the performance and reliability expected from OMA, we utilized a state-of-the-art tech stack:</p>
            <div class="stack-grid">
                <div class="stack-item">Node.js</div>
                <div class="stack-item">Socket.io</div>
                <div class="stack-item">MongoDB</div>
                <div class="stack-item">Firebase</div>
                <div class="stack-item">Capacitor</div>
                <div class="stack-item">Express.js</div>
                <div class="stack-item">WebRTC</div>
                <div class="stack-item">IndexedDB</div>
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
