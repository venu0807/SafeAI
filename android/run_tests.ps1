Write-Host "Building and installing APKs..."
.\gradlew.bat installDebug installDebugAndroidTest

Write-Host "Granting permissions..."
$pkg = "com.example.android"
adb shell pm grant $pkg android.permission.RECORD_AUDIO
adb shell pm grant $pkg android.permission.CAMERA
adb shell pm grant $pkg android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $pkg android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant $pkg android.permission.ACCESS_BACKGROUND_LOCATION
adb shell pm grant $pkg android.permission.SEND_SMS
adb shell pm grant $pkg android.permission.CALL_PHONE
adb shell pm grant $pkg android.permission.READ_CONTACTS
adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS

Write-Host "Running tests via adb..."
adb shell am instrument -w com.example.android.test/androidx.test.runner.AndroidJUnitRunner
