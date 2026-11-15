# Focus Guard (2.0.0)

Android focus blocker built with Kotlin. The repository root contains the GitHub Actions workflow; the Android Gradle project is in `FocusGuardAndroid/`.

## Focus Guard 2.0

- Restrictions apply all seven days. There is no Sunday exemption.
- A 30-gem redemption grants a 24-hour pass. An active pass can be extended by redeeming another 30 gems. Restrictions resume automatically when the pass expires.
- YouTube remains available for regular videos. The accessibility service inspects window, click and scroll events and exits YouTube when dedicated Shorts-player signals are identified. Known internal view-ID fingerprints are isolated in `ShortsDetector` so they can be updated after YouTube UI changes.
- User-selected OTT/streaming apps are sent to Home on foreground entry.
- A separate app list earns usage gems per selected app and completed day: under 10 minutes = 5 gems; 10 through 30 minutes = 3 gems; over 30 minutes = 0 gems.
- One additional gem is awarded for a calendar day when the restrictions remained active and the accessibility service session covered the entire day without interruption.
- Blocked apps and usage-reward apps are mutually exclusive, so a blocked app cannot earn gems from zero usage.
- Usage totals, reward calculations and the gem wallet remain on-device.

## Build without Android Studio

1. Upload the contents of this repository archive to the GitHub repository root, keeping `.github/workflows/build-apk.yml` at the root and `FocusGuardAndroid/` as a folder.
2. Open **Actions** → **Build Focus Guard APK** → **Run workflow**.
3. Download the `focus-guard-debug-apk` artifact and extract `app-debug.apk`.

The workflow builds from `FocusGuardAndroid/` and runs unit tests before packaging.

## Required Android settings

- Enable **Focus Guard** under Android **Accessibility** settings. Android requires the user to grant this manually.
- Grant **Usage Access** under **Special app access → Usage access** to calculate app-usage rewards. This is a special settings access, not a normal runtime permission.

## Important limitations

- YouTube has no public, stable API exposing a Shorts-player flag. Detection is based on accessibility view IDs, selected Shorts navigation state and Shorts-player controls. YouTube updates, language changes or missing accessibility nodes can affect detection. Test against the installed YouTube version; this cannot be guaranteed to catch every Shorts screen.
- Android's Home action leaves the blocked app in the background; it does not force-stop its process or guarantee that background audio stops.
- This is a user-configured digital-wellbeing tool, not a device-admin/parental-control lock. The device owner can disable the accessibility or Usage Access setting, uninstall the app, or change system settings.
- UsageStats data is supplied by Android and can be delayed or rounded. Reward calculations run after 06:00 local time on the following day and may be processed later by WorkManager.
- The +1 full-day gem is awarded only if an accessibility-service session spans the entire local calendar day, and no 24-hour pass overlaps that date. Critical system settings, the default launcher and the default dialer are excluded from the block list to avoid locking out essential phone controls.

## Permissions and privacy

The accessibility service reads foreground package names and the active YouTube accessibility tree when needed. Usage Access reads aggregate foreground time only for the separately selected reward apps. The app does not capture screenshots, inspect typed text or upload this data. Disclose these behaviours to users and obtain their explicit consent before enabling monitoring.
