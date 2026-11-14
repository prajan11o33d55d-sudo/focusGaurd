# Build Focus Guard without installing Android Studio

This project includes a GitHub Actions workflow that builds a debug APK in GitHub's cloud runners. You do not need Android Studio, the Android SDK, or Gradle installed on your computer.

## What you need

- A GitHub account.
- A web browser and an internet connection.
- Your phone and a USB cable are not required for the cloud build; you can download the APK to the phone afterward.

## Steps

1. Extract `FocusGuardAndroid.zip` on your computer.
2. Sign in to GitHub and create a **new repository**. A private repository is fine. Give it a name such as `FocusGuardAndroid`.
3. Open the empty repository. Choose **Add file → Upload files**.
4. Upload the *contents* of the extracted `FocusGuardAndroid` folder, preserving the folder structure. Include the hidden `.github` folder. On Linux file pickers, press `Ctrl+H` to show hidden files if needed.
5. Commit the uploaded files to the repository's default branch.
6. Open the repository's **Actions** tab. Select **Build Focus Guard APK** in the left sidebar.
7. Press **Run workflow**, keep the default branch selected, and press the green **Run workflow** button.
8. Wait for the run to finish. Open the completed run and find **Artifacts** near the bottom. Download `focus-guard-debug-apk`.
9. Extract the downloaded artifact ZIP. It contains `app-debug.apk`.
10. Transfer `app-debug.apk` to your Android phone and open it to install. Android may ask you to allow installation from the app you used to open the APK. Only allow this for an APK you intentionally built and trust.

## Notes

- The workflow runs unit tests before assembling the APK. If it fails, open the failed workflow run and inspect the first red step; its log explains the build error.
- This creates a **debug APK**, not a Play Store release. It is intended for testing on your own device.
- The current Shorts detector is heuristic and is not guaranteed to identify every Shorts screen. Test it before relying on it.
- Android's Home action leaves YouTube in the background; it does not force-stop YouTube or guarantee that background playback ends.
- Do not publish the app as a disability-support accessibility tool. Review the applicable accessibility-service and distribution rules before public release.
