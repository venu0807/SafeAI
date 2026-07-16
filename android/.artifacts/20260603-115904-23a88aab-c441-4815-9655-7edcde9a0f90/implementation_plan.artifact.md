# Fixing Espresso RootViewWithoutFocusException on API 35

The goal is to ensure the application window gains focus on API 35 emulators to prevent 127 tests from failing with `RootViewWithoutFocusException`. Since the lock screen is already handled, we will target window animations and focus-triggering events.

## Proposed Changes

### Test Utilities (`DismissKeyguardRule.java`)

Enhance the `DismissKeyguardRule` to not only dismiss the keyguard but also force the window manager to grant focus to the app.

#### [DismissKeyguardRule.java](file:///D:/Proposals/SafeguardAI/android/app/src/androidTest/java/com/example/android/utils/DismissKeyguardRule.java)

- Add shell commands to disable window and transition animations (which are the primary cause of focus lag on API 35).
- Add a shell command to disable predictive back gestures.
- Add a center-screen tap via `input tap` to force the window manager to grant focus to the top-most window.

**Proposed Logic for `evaluate()`:**
1. `wm dismiss-keyguard`
2. `input keyevent KEYCODE_WAKEUP`
3. `settings put global window_animation_scale 0`
4. `settings put global transition_animation_scale 0`
5. `settings put global animator_duration_scale 0`
6. `settings put secure android_predictive_back_enabled 0`
7. `input tap <center_x> <center_y>` (using a general 500 1000 coordinate or calculated center).

---

## Verification Plan

### Automated Tests
- Run the following command on the API 35 emulator:
  ```powershell
  ./gradlew connectedDebugAndroidTest
  ```
- Verify that the 127 failing tests now pass or the failure rate significantly decreases.

### Manual Verification
- Observe the emulator during the first test run to ensure no animations are playing and the app receives focus immediately upon launch.
