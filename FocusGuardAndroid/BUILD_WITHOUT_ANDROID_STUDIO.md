# Build Focus Guard 2.0 without Android Studio

This archive is arranged to match the root of the GitHub repository:

- `.github/workflows/build-apk.yml` — GitHub Actions APK build workflow.
- `FocusGuardAndroid/` — the complete Android Gradle project.

## Updating an existing GitHub repository

1. Extract the archive on your computer.
2. Open your GitHub repository in a browser and choose **Add file → Upload files**.
3. Upload the updated `.github/workflows/build-apk.yml` and the contents of `FocusGuardAndroid/`, preserving those paths. GitHub should show the files under the same paths as the archive.
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
