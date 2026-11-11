# Installing Triangulation Locator

This guide assumes no programming experience. It takes about 30–60 minutes the first time,
mostly waiting for downloads. Everything is free.

## What you need

- A Windows, Mac or Linux computer
- An Android phone (Android 8.0 or newer) with a USB cable
- **Android Studio** (free, from https://developer.android.com/studio)

## Part 1: Open the project on your computer

1. Install Android Studio and open it. Accept the default options in the setup wizard.
2. Choose **File → Open** and select the `Triangulation-app` folder (the one containing `settings.gradle.kts`).
3. If asked **"Trust project?"**, choose **Trust Project**.
4. Android Studio now runs a **Gradle sync**. This downloads the build tools and libraries
   the app needs. The first time can take 5–15 minutes. Watch the progress bar at the bottom.
   - If a message says **"Gradle build scripts found"** or **"Load Gradle project"**, click it.
   - If it offers to download a missing **Android SDK platform**, click the link to install it.
   - If it suggests **upgrading** the Android Gradle Plugin, you can accept.

When the sync finishes, the green ▶ (Run) button at the top becomes active.

## Part 2: Prepare your phone (one time only)

1. On the phone, open **Settings → About phone**.
2. Tap **Build number** seven times. You'll see "You are now a developer".
3. Open **Settings → System → Developer options** (the location varies by phone) and turn on **USB debugging**.
4. Connect the phone to the computer with the USB cable.
5. On the phone, tap **Allow** when asked "Allow USB debugging?".

## Part 3: Install and run

1. In Android Studio, pick your phone from the device list next to the ▶ button.
2. Click ▶ **Run**. The app is built, installed and opened on your phone.
3. The app is now in your phone's app list as **Triangulation Locator**. You can unplug the phone.

## Getting updates

When the code changes (for example, after a new phase is added):

1. In Android Studio, if a bar says **"Gradle files have changed"**, click **Sync Now**.
2. Click ▶ **Run** again. Only changed parts are rebuilt, so this is much quicker than the first time.
   Your saved bearings are kept.

Small changes to code can also be sent to a running app with **Apply Changes** (the ⚡ buttons
next to Run). New permissions or libraries always need a full ▶ Run.

## Checking the maths (optional)

The project includes automatic tests for the triangulation calculations. They run on the
computer, so no phone is needed:

1. In the **Project** panel on the left, open `app → src → test`.
2. Right-click the `java` folder and choose **Run 'Tests in 'java''**.
3. A panel opens at the bottom. Green ticks mean the maths is working correctly.

## Sharing the app with other phones (optional)

To make an installable file (APK) for other people:

1. In Android Studio choose **Build → Generate Signed App Bundle or APK → APK**.
2. The first time, choose **Create new…** to make a signing key. **Save the key file and
   passwords somewhere safe and back them up.** Every future update must use the same key.
   Never put the key file in the project folder or in Git.
3. Choose **release** and finish. Android Studio shows a link to the finished `.apk` file.
4. Copy the APK to the other phone (USB cable, Taildrop, or a file server on your Tailscale
   network; see `PLAN.md`).
5. On that phone, open the APK. If asked, allow **"Install unknown apps"** for the app you
   opened it with, then tap **Install**.

## Troubleshooting

| Problem | What to try |
| --- | --- |
| Sync fails with a red error | Click **File → Sync Project with Gradle Files** to retry. Check your internet connection. |
| Phone doesn't appear in the device list | Unplug and replug it, check USB debugging is on, and accept the prompt on the phone. Try another cable. |
| "App not installed" on another phone | An older copy signed with a different key is installed. Uninstall it first. |
