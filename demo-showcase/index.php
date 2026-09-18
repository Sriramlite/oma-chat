<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>OMA - Premium Messaging Experience</title>
    <link rel="stylesheet" href="style.css">
    <!-- Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;700;800&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
    <!-- Font Awesome -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <!-- AOS Animations -->
    <link href="https://unpkg.com/aos@2.3.1/dist/aos.css" rel="stylesheet">
</head>
<body>
    <div class="gradient-bg"></div>

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
                <span id="status-text">Checking Server...</span>
            </div>
            <a href="https://api.pdktdev.in/" target="_blank" class="btn btn-primary">Launch App</a>
        </div>
    </nav>

    <header class="hero">
        <div class="floating-shape shape-1"></div>
        <div class="floating-shape shape-2"></div>
        <div class="floating-shape shape-3"></div>
        
        <div class="hero-content" data-aos="fade-right">
            <h1>Connect <span class="logo">Instantly</span>,<br>Securely & Freely.</h1>
            <p>Experience the next generation of messaging. High-quality video calls, instant syncing, and a beautiful interface designed for you.</p>
            <div class="hero-btns">
                <a href="https://api.pdktdev.in/" class="btn btn-primary">Get Started Now</a>
                <a href="assets/oma-app.apk" class="btn btn-secondary" download><i class="fab fa-android"></i> Download APK</a>
            </div>
        </div>
        <div class="hero-image" data-aos="fade-left">
            <img src="assets/mockup.png" alt="OMA App Mockup">
        </div>
    </header>

    <section id="features" class="features">
        <h2 data-aos="fade-up">Next-Gen Messaging</h2>
        <div class="feature-grid">
            <div class="feature-card" data-aos="fade-up" data-aos-delay="100">
                <i class="fas fa-bolt"></i>
                <h3>Lightning Fast</h3>
                <p>Enjoy sub-second message delivery thanks to our optimized Socket.io connections and real-time syncing.</p>
            </div>
            <div class="feature-card" data-aos="fade-up" data-aos-delay="200">
                <i class="fas fa-video"></i>
                <h3>HD Video Calls</h3>
                <p>Stay close to your loved ones with crystal-clear high-definition video and audio calling features.</p>
            </div>
            <div class="feature-card" data-aos="fade-up" data-aos-delay="300">
                <i class="fas fa-shield-halved"></i>
                <h3>Secure by Design</h3>
                <p>Your privacy is our priority. OMA uses Firebase Authentication and secure data vaults to keep your chats private.</p>
            </div>
        </div>
    </section>

    <section class="hero-footer">
        <div class="card-half" data-aos="fade-right">
            <h2><i class="fas fa-vial"></i> Quick Test</h2>
            <p>Use our demo account to explore the application features instantly without registration.</p>
            <div class="credential-box">
                <div class="credential-label">Username</div>
                <div class="credential-value">demo_user</div>
                <div class="credential-label">Password</div>
                <div class="credential-value">demo1234</div>
            </div>
            <p style="margin-top:1rem; font-size:0.85rem;"><i class="fas fa-info-circle"></i> Note: Please use these on the <a href="https://api.pdktdev.in/" style="color:var(--primary);">Live App</a>.</p>
        </div>
        <div class="card-half" data-aos="fade-left">
            <h2><i class="fas fa-layer-group"></i> Technology Stack</h2>
            <p>Built using modern, scalable technologies for a seamless real-time experience.</p>
            <div class="stack-grid">
                <div class="stack-item">Node.js</div>
                <div class="stack-item">Socket.io</div>
                <div class="stack-item">MongoDB</div>
                <div class="stack-item">Firebase</div>
                <div class="stack-item">Capacitor</div>
                <div class="stack-item">Express</div>
                <div class="stack-item">HTML/JS</div>
                <div class="stack-item">Vanilla CSS</div>
            </div>
        </div>
    </section>

    <footer>
        <p>&copy; <?php echo date("Y"); ?> OMA - Online Messaging Application. All rights reserved.</p>
        <p style="margin-top: 10px; font-size: 1rem; color: var(--text-main);">Created and Owned by <a href="https://pdktdev.in" target="_blank" style="color: var(--primary); text-decoration: none; font-weight: 700;">Pdktdev.in</a></p>
    </footer>

    <!-- Demo Notice Modal -->
    <div id="demo-modal" class="modal-overlay">
        <div class="modal-content">
            <i class="fas fa-info-circle"></i>
            <h3>Demo Website</h3>
            <p>This is a demonstration showcase of the OMA application. To experience the full messaging features, please visit the main application page.</p>
            <button onclick="closeModal()" class="btn btn-primary">Understood</button>
        </div>
    </div>

    <script src="https://unpkg.com/aos@2.3.1/dist/aos.js"></script>
    <script>
        AOS.init({
            duration: 1000,
            once: true,
            easing: 'ease-out-cubic'
        });

        // Server Status Logic
        const API_URL = 'https://api.pdktdev.in/api';
        const statusDot = document.getElementById('status-dot');
        const statusText = document.getElementById('status-text');

        async function checkServerStatus() {
            try {
                // Ping the server to check status and wake it up
                const start = Date.now();
                const response = await fetch(API_URL + '/user/list', { mode: 'no-cors' }); 
                // Since it's potentially cross-origin without CORS headers on that specific endpoint, 
                // no-cors will still 'trigger' the request and we can assume 'Online' if it doesn't fail immediately.
                // However, a better way is a simple fetch.
                
                statusDot.className = 'status-dot online';
                statusText.innerText = 'Server Online';
                statusText.style.color = '#10b981';
            } catch (error) {
                console.error("Status Check Error:", error);
                statusDot.className = 'status-dot offline';
                statusText.innerText = 'Server Offline';
                statusText.style.color = '#ef4444';
            }
        }

        // Modal Logic
        function showModal(e) {
            if (e) e.preventDefault();
            document.getElementById('demo-modal').style.display = 'flex';
        }

        function closeModal() {
            document.getElementById('demo-modal').style.display = 'none';
        }

        // Intercept Get Started Button (If Demo modal behavior is still desired)
        /* 
        document.querySelector('.hero-btns .btn').addEventListener('click', showModal);
        */

        // 3D Tilt Effect for Hero Image
        const heroImage = document.querySelector('.hero-image img');
        const heroContainer = document.querySelector('.hero-image');

        if (heroContainer && heroImage) {
            heroContainer.addEventListener('mousemove', (e) => {
                const rect = heroContainer.getBoundingClientRect();
                const x = e.clientX - rect.left;
                const y = e.clientY - rect.top;
                
                const centerX = rect.width / 2;
                const centerY = rect.height / 2;
                
                const rotateX = (y - centerY) / 10;
                const rotateY = (centerX - x) / 10;
                
                heroImage.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) scale(1.05)`;
            });

            heroContainer.addEventListener('mouseleave', () => {
                heroImage.style.transform = `perspective(1000px) rotateX(5deg) rotateY(-10deg) scale(1)`;
            });
        }

        // Initial Check
        checkServerStatus();
        // Refresh every 30 seconds
        setInterval(checkServerStatus, 30000);
    </script>
</body>
</html>
