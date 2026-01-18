# Setup Push Notifications (Firebase)

To get Push Notifications working, you need to link your app to Firebase.

## 1. Create a Firebase Project
1.  Go to [Firebase Console](https://console.firebase.google.com/).
2.  Click **Add project** and name it "OMA Chat".
3.  Disable Google Analytics (for simplicity) and create.

## 2. Add Android App
1.  In your Firebase Project dashboard, click the **Android Icon** (robot).
2.  **Package Name**: `com.oma.chat` (This MUST match exactly).
3.  **App Nickname**: OMA Chat.
4.  Click **Register app**.

## 3. Download Config File
1.  Download the **`google-services.json`** file.
2.  Move this file into your project folder here:
    `D:\Program Files\Xampp\htdocs\oma\android\app\google-services.json`

## 4. Re-sync and Build
1.  Run the following command in terminal (I have done this, but good to know):
    `npx cap sync`
2.  Open **Android Studio**.
3.  **Re-run the app**.

> **Note**: Push notifications will only work on a real device (not emulator) for most features.
