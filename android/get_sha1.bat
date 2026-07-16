@echo off
ECHO ============================================
ECHO  SafeGuard AI - SHA-1 Fingerprint Extractor
ECHO ============================================
ECHO.

REM Find the debug keystore
SET KEYSTORE_PATH=%USERPROFILE%\.android\debug.keystore
SET KEYSTORE_PASS=android
SET KEYSTORE_ALIAS=androiddebugkey

IF EXIST "%KEYSTORE_PATH%" (
    ECHO [1/2] Debug keystore found at: %KEYSTORE_PATH%
    ECHO.
    ECHO Your DEBUG SHA-1 fingerprint:
    "C:\Program Files\Android\Android Studio\jbr\bin\keytool" -list -v -keystore "%KEYSTORE_PATH%" -alias "%KEYSTORE_ALIAS%" -storepass "%KEYSTORE_PASS%" -keypass "%KEYSTORE_PASS%" 2>nul | findstr /C:"SHA1:" /C:"SHA-1:"
    
    IF ERRORLEVEL 1 (
        REM Try alternative JDK locations
        WHERE keytool 2>nul >nul
        IF NOT ERRORLEVEL 1 (
            keytool -list -v -keystore "%KEYSTORE_PATH%" -alias "%KEYSTORE_ALIAS%" -storepass "%KEYSTORE_PASS%" -keypass "%KEYSTORE_PASS%" | findstr /C:"SHA1:" /C:"SHA-1:"
        ) ELSE (
            ECHO   Could not find keytool. Try running manually:
            ECHO   keytool -list -v -keystore "%%USERPROFILE%%\.android\debug.keystore" ^
            ECHO     -alias androiddebugkey -storepass android -keypass android ^| findstr SHA1
        )
    )
) ELSE (
    ECHO [1/2] Debug keystore not found at default location.
    ECHO   Default location: %KEYSTORE_PATH%
    ECHO   If you haven't built the project yet, run ./gradlew assembleDebug first
    ECHO   to generate the debug keystore.
)

ECHO.
ECHO [2/2] For RELEASE builds:
ECHO   Your app uses the debug keystore for release builds too
ECHO   (signingConfig = signingConfigs.getByName("debug") in build.gradle.kts).
ECHO   So the same SHA-1 above works for both.
ECHO.
ECHO   When you switch to a real release keystore, come back and run this again.
ECHO.
ECHO ============================================
ECHO  Next Step: Firebase Console
ECHO ============================================
ECHO.
ECHO  1. Go to: https://console.firebase.google.com/project/safeguardai-edb31/settings/general/
ECHO  2. Scroll to "Your apps" section
ECHO  3. Click the Android icon (com.example.android)
ECHO  4. Click "Add fingerprint"
ECHO  5. Paste the SHA-1 shown above
ECHO  6. Click "Save"
ECHO  7. Download the updated google-services.json and replace the current one
ECHO.
ECHO  After updating, rebuild the app. Google Sign-In will work without Error 10.
ECHO ============================================
pause
