# YouTube Timer Guard v0.1.1

Private/sideloaded Android parental-control prototype.

## Behavior

- Detects foreground use of YouTube (`com.google.android.youtube`) and YouTube Kids (`com.google.android.apps.youtube.kids`).
- Counts **continuous** foreground usage.
- Default limit: **15 minutes** (configurable 1–180 minutes).
- Leaving YouTube or turning the screen off resets the timer.
- At the limit, a fullscreen black fake-shutdown screen appears.
- The lockout remains active if Home/Recents is used; the monitor attempts to bring the fake-shutdown screen back.
- Parent unlock: tap the **top-left corner 5 times within 3 seconds**, then enter the parent PIN.
- The PIN is stored as salted SHA-256 rather than plaintext.

## Required setup on the phone

1. Install the APK.
2. Open **YouTube Timer Guard**.
3. Grant **Usage Access**.
4. Grant **Display over other apps**.
5. Set a parent PIN.
6. Leave the limit at 15 minutes or change it.
7. Tap **Start Monitoring**.
8. On Android 13+, allow notifications because Android requires a visible foreground-service notification.

## Important limitations

- This does **not actually power off** the phone. Normal third-party Android apps cannot silently perform a real shutdown.
- The fake shutdown is intentionally recoverable and does not override hardware power/emergency controls.
- Some vendors aggressively stop background services. If monitoring stops, allow unrestricted/background battery use for this app in the phone's normal system settings.
- Usage Access is a special user-granted permission and Display-over-apps is also user-granted.
- This project is designed for private sideloading. Publishing to Google Play would require a separate policy review, particularly for special-use foreground service and overlay behavior.

## Build in Android Studio

Requirements:
- Android Studio with JDK 17
- Android SDK 35 installed

Open this project, allow Gradle sync, then choose:

`Build > Build App Bundle(s) / APK(s) > Build APK(s)`

Expected debug APK path:

`app/build/outputs/apk/debug/app-debug.apk`

## Test without waiting 15 minutes

Set the limit to **1 minute**, start monitoring, open YouTube and keep it foreground. After validation, restore the setting to 15 minutes.

## Build automatically with GitHub Actions

The project includes `.github/workflows/build-apk.yml`. If the project is pushed to a GitHub repository, the workflow builds the debug APK on every push to `main`, or it can be started manually from the **Actions** tab. Download the `YouTubeTimerGuard-debug-apk` artifact after the run succeeds.


## v0.1.2 shutdown screen
The fake shutdown now shows a centered “Shutting down...” message with a small rotating arc for 15 seconds before becoming fully black.


## v0.1.3
- Mutes media volume to 0 when the YouTube time limit is reached.
- Restores the previous media volume after the parent PIN unlocks.
- Shows a fake 0% / empty-battery indicator during the 15-second shutdown animation.
- The real Android battery percentage is not modified.
