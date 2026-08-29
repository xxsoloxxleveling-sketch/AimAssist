# AimAssist screen-capture prototype

This APK is currently a test harness for an Android 2D pool app. It does not use the camera or
projector. It captures the Android display with MediaProjection, so the 2D pool app can be running
behind it.

## Test flow

1. Install and open the APK.
2. Press **Start screen capture** and approve Android's capture prompt.
3. Tap the 2D table corners in this order: top-left, top-right, bottom-right, bottom-left.
4. The app warps the selected quadrilateral to a 2000 x 1000 canonical table coordinate system.
5. Green circles are the classical OpenCV Hough-circle detections. White and magenta lines are
   the current guide overlay. Drag on the captured screen to provide a temporary aim direction.
6. Press **Calibrate (4 taps)** to repeat calibration.

The captured screen is processed on the display frame callback and all overlay coordinates are
converted through the same homography. This first build is intentionally aimed at validating
screen capture, calibration, coordinate conversion, and the 2D overlay before adding physical
camera input.

## Build

Open the project in Android Studio or run `gradlew.bat :app:assembleDebug`. The debug APK is
created at `app/build/outputs/apk/debug/app-debug.apk`.
