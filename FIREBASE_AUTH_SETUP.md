# Firebase Authentication Setup Guide

To make the implemented features work, you need to enable them in your Firebase Console.

## 1. Enable Email Magic Link (Passwordless)
1.  Go to **Firebase Console** -> **Authentication** -> **Sign-in method**.
2.  Click **Add new provider** (or edit **Email/Password**).
3.  Enable **Email/Password** if not already enabled.
4.  **IMPORTANT:** Enable **Email link (passwordless sign-in)** check box.
5.  Click **Save**.

## 2. Enable Google Sign-In
1.  Go to **Firebase Console** -> **Authentication** -> **Sign-in method**.
2.  Click **Add new provider**.
3.  Select **Google**.
4.  Toggle **Enable**.
5.  Set the **Project support email** (required).
6.  Click **Save**.

## 3. (Optional) Native Android App Configuration
*If you are building the Android APK, you need to add your SHA-1 key.*
1.  Go to **Project Settings** (Gear icon) -> **General**.
2.  Scroll down to **Your apps**.
3.  Select your Android App.
4.  Click **Add fingerprint**.
5.  Paste your SHA-1 Key (from your keystore).
    *   *Debug Key*: run `./gradlew signingReport` in `android/` folder.
