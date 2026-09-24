# Changelog

All notable changes to the **OMA Messenger** project are documented below.

---

## [v2.9.0] - 2026-09-25

### 📞 Calling & Audio Experience
- **Custom Audio Tones Integration**:
  - Outgoing dialing audio (`outgoingcall.mp3`) during call setup.
  - Call rejected audio (`callrejected.mp3`) followed by busy tone.
  - Unreachable / offline peer sequence (`notreacheable.mp3`) followed by busy tone.
  - 20-second timeout mechanism marking unanswered calls with busy tone (`busytone.mp3`).
- **Call Tone Completion & Screen Lifecycle**:
  - Call screen now awaits complete audio playback before closing or navigating away on mobile and web.
- **Audio Routing & Earpiece Support**:
  - Implemented dynamic earpiece (`AudioDeviceInfo.TYPE_BUILTIN_EARPIECE`) and speakerphone (`AudioDeviceInfo.TYPE_BUILTIN_SPEAKER`) toggle on Android with backward-compatible fallbacks.
- **Proximity Sensor Mistouch Prevention**:
  - Added [`CallProximityManager`](file:///d:/Program%20Files/Xampp/htdocs/oma/android/app/src/main/java/com/oma/chat/data/call/CallProximityManager.kt) utilizing `PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK`.
  - Automatically blacks out the display and disables touch when the phone is held to the ear during earpiece calls.
  - Automatically restores display when switched to speakerphone or when the call concludes.

### 👤 WhatsApp-Style User Profile Screen
- **Android**:
  - Created [`UserProfileScreen.kt`](file:///d:/Program%20Files/Xampp/htdocs/oma/android/app/src/main/java/com/oma/chat/presentation/profile/UserProfileScreen.kt) and [`UserProfileViewModel.kt`](file:///d:/Program%20Files/Xampp/htdocs/oma/android/app/src/main/java/com/oma/chat/presentation/profile/UserProfileViewModel.kt).
  - Features high-resolution avatar with full-screen zoom preview, Name, @username, Bio/About, Phone number, and Live Battery Status.
  - Added Chat settings including Mute Notifications toggle, Media Visibility, End-to-End Encryption information badge, and Danger Zone actions (Block, Report, Clear/Delete Chat).
  - Tapping the chat header user name or avatar routes directly to the profile screen.
- **Web**:
  - Enhanced user profile overlay modal accessible via chat header with live bio, phone number, battery percentage, call shortcuts, and moderation controls.

### 🔋 Battery Status & Chat Header Animation
- **Vertical Slide Status Carousel**:
  - Implemented smooth CSS sliding animation in chat headers alternating between presence info (Online / Last seen) and real-time battery status (`fa-battery-*`, percentage, charging bolt).
- **Auto Status & Battery Loading**:
  - Fixed initial load issue on Web where battery was only fetched upon opening profile modal.
  - `startStatusCarousel` now automatically fetches and populates fresh partner user data (`api.batchGetUsers`) on opening any chat.
- **Real-time Battery Broadcast**:
  - Client sends live battery updates via socket and API on connection and battery percentage/charging changes.

### 💬 Real-Time Messaging & Synchronization
- **Cross-User Message Deletion & Editing**:
  - Fixed real-time socket events for message deletion and editing so changes immediately reflect on both sender and recipient screens.
- **Google & Phone OTP Authentication**:
  - Streamlined Google Login integration and phone verification flows.

---

## [v2.8.5] - 2026-03-15
- **Security & Recovery**:
  - WhatsApp-based Password Recovery (OTP).
  - Bcrypt salted password hashing.
  - IP-based rate limiting on auth endpoints.
  - Phone verification and MongoDB consistency fixes.

## [v2.8.0] - 2026-02-20
- **WhatsApp Integration**:
  - Fast2SMS WhatsApp API integration.
  - Verified OTP delivery via Meta Proxy.
  - WhatsApp business template support.
