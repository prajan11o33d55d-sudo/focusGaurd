# Build Focus Guard 2.2.0 without Android Studio

This archive is arranged to match the root of the GitHub repository:

- `.github/workflows/build-apk.yml` — GitHub Actions APK build workflow.
- `FocusGuardAndroid/` — the complete Android Gradle project.

## Updating an existing GitHub repository

1. Extract the archive on your computer.
2. Open your GitHub repository in a browser and choose **Add file → Upload files**.
3. Upload the updated contents of `FocusGuardAndroid/`, preserving that folder path. The `.github/workflows/build-apk.yml` is included for reference; if your repository already has the workflow from version 2.0, you do not need to upload the hidden `.github` folder again.
4. Commit the changes.

If your file picker hides `.github`, use **Add file → Create new file** and enter `.github/workflows/build-apk.yml`, then paste the contents of the included workflow file.

## Build the APK

1. Open the repository's **Actions** tab.
2. Select **Build Focus Guard APK**.
3. Choose **Run workflow**.
4. When it succeeds, download the `focus-guard-debug-apk` artifact.
5. Extract the artifact ZIP and install `app-debug.apk` on your Android phone.

No Android Studio installation is needed. The workflow runs the unit tests before assembling the debug APK.

## Required phone settings

- Enable **Focus Guard** under Android **Accessibility** settings.
- Grant **Usage Access** under **Settings → Apps → Special app access → Usage access** to calculate usage-based gems.
- If the phone manufacturer repeatedly stops the Accessibility service, check the device's battery settings for Focus Guard and allow background operation where the device provides that option.

## Important limitations

YouTube does not publish a stable public Shorts-state API. Detection uses YouTube's accessibility tree, internal view-ID fingerprints and selected navigation state. YouTube updates can change those signals, so the Shorts detector must be tested on the exact YouTube app version installed on your phone. The service sends Android's Home action; it does not force-stop YouTube or guarantee that background playback is terminated.


## Version 2.2.0 update note

The dashboard includes a prominent emergency pause button. It stops Shorts and selected-app restrictions immediately without spending gems; restrictions remain off until **Resume restrictions** is selected. **Open Android app settings (Force stop)** opens the Android app-info page, where the operating system's Force stop command is available. Android does not allow an ordinary app to force-stop itself directly.

The app manifest does not request Internet permission. Accessibility still lets the service inspect YouTube's visible accessibility interface to identify Shorts, and Usage Access reads usage totals for selected reward apps; both are powerful special permissions that can be revoked in Android Settings.

**Installing an update:** this is a GitHub Actions debug build. Debug signing keys can differ between separate workflow runs. If Android refuses to update the existing Focus Guard because of a signature mismatch, uninstall the old build and install the new one. Uninstalling deletes the old app's locally stored gem balance and settings, so note those first.
