# SafeGuard AI — Firebase SHA-1 Fingerprint Setup

## Why This Is Needed

Google Sign-In requires your app's SHA-1 fingerprint to be registered in Firebase.
Without it, you'll get **Error 10** (`DEVELOPER_ERROR`) when trying to sign in.

## Your Firebase Project

- **Project ID:** `safeguardai-edb31`
- **Package name:** `com.example.android`
- **Already registered SHA-1 (in google-services.json):** `63DAB2C76817AD93E99F67B78E32C513FA123692`

> ⚠️ This existing hash was added during initial project setup. It may NOT match
> the debug keystore on your current machine. You need to extract **your** SHA-1
> and register it.

---

## Step 1: Extract Your Debug SHA-1

### Option A: Run the batch script (Windows)

```cmd
cd android
get_sha1.bat
```

### Option B: Manual command

Open a terminal and run:

```bash
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" ^
  -alias androiddebugkey -storepass android -keypass android
```

Look for the line starting with `SHA1:` or `SHA-1:`.

If `keytool` isn't found, locate it in your JDK installation:
- Android Studio bundles it at: `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`
- Or from a standalone JDK: look under `%JAVA_HOME%\bin\keytool.exe`

### Option C: From Gradle (if you can build)

```bash
cd android
gradlew signingReport
```

This prints all signing configs including SHA-1 for every build variant.

---

## Step 2: Register SHA-1 in Firebase Console

1. Go to: **https://console.firebase.google.com/project/safeguardai-edb31/settings/general/**
2. Scroll down to **"Your apps"** section
3. Find the Android app **`com.example.android`**
4. Click **"Add fingerprint"**
5. Paste the SHA-1 you extracted in Step 1
6. Click **"Save"**

![Firebase Console - Add Fingerprint](https://firebase.google.com/docs/auth/android/images/google-sign-in-firebase-console.png)

---

## Step 3: Download Updated Config

After saving, Firebase updates your `google-services.json` automatically.

1. Click **"Download google-services.json"** button
2. Replace `android/app/google-services.json` with the downloaded file
3. Rebuild the app: `./gradlew assembleDebug`

---

## Release Build (Future)

Currently, your `build.gradle.kts` has:

```kotlin
getByName("release") {
    signingConfig = signingConfigs.getByName("debug") // 👈 uses debug keystore
}
```

This means **release builds use the same debug SHA-1** for now — so one registration covers both.

When you're ready for Play Store release:

1. Create a real release keystore:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias safeguard-release \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Extract its SHA-1:
   ```bash
   keytool -list -v -keystore release.keystore -alias safeguard-release
   ```

3. Add it as a **second fingerprint** in Firebase Console (Step 2 again)

4. Update `build.gradle.kts` to use the release keystore

---

## Verifying It Works

After registering the SHA-1 and rebuilding:

1. Install the fresh APK on a device
2. Tap **"Sign in with Google"**
3. The Google account picker should appear (not Error 10)
4. After signing in, you should see "Signed in successfully!" Toast

If it still fails with Error 10, the SHA-1 you registered doesn't match the
keystore used to sign the APK. Double-check both.
