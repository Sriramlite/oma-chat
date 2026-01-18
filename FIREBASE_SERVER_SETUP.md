# Firebase Server-Side Setup (For Notifications)

To let your Render server send notifications, it needs a "Private Key".

## 1. Get the Service Account Key
1.  Go to the [Firebase Console](https://console.firebase.google.com/).
2.  Open your project ("OMA Chat").
3.  Click the **Gear Icon ⚙️** (Project Settings).
4.  Go to the **Service accounts** tab.
5.  Click **Generate new private key**.
6.  Click **Generate key** to download a JSON file.

## 2. Configure Render
1.  Open the downloaded JSON file with a text editor (Notepad, VS Code).
2.  **Copy the entire content** (curly braces and all).
3.  Go to your [Render Dashboard](https://dashboard.render.com/).
4.  Select your **Web Service** (the Node app).
5.  Go to **Environment**.
6.  Add a new Environment Variable:
    -   **Key**: `FIREBASE_SERVICE_ACCOUNT`
    -   **Value**: Paste the *entire* JSON content you copied.
7.  Click **Save Changes**.

Render will redeploy automatically. Once done, notifications will work!
