# How to Build Your Android App (APK)

## Prerequisites
-   **Android Studio** installed on your computer.

## Steps
1.  **Open Android Studio**.
2.  Click **Open** and select the `android` folder inside your project:
    -   `D:\Program Files\Xampp\htdocs\oma\android`
3.  Wait for Gradle to sync (it might take a few minutes to download SDKs).
4.  **To Run on Phone**:
    -   Enable **USB Debugging** on your Android phone.
    -   Connect it to PC via USB.
    -   Click the green **Run (Play)** button in Android Studio.
5.  **To Build an APK (to share)**:
    -   Go to **Build** menu -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
    -   Once done, click **"locate"** in the popup to find your `.apk` file.

## Troubleshooting
-   **White Screen?** Check your internet. The app needs to connect to the Render Backend.
-   **Login Issues?** Ensure the `api.js` file has the correct `API_BASE` (I set it to your Render URL).
