# Focus Guard — Android starter project

A local-only Kotlin starter for a user-controlled focus app.

## Included

- AccessibilityService listening only for window-state/content-change events.
- Immediate `GLOBAL_ACTION_HOME` when a selected app becomes foreground.
- YouTube is not blocked wholesale; a separate heuristic checks its accessibility tree for a Shorts destination hint plus player-action labels.
- Fixed weekly policy: restrictions Monday–Saturday, Sunday exemption.
- Simple app picker based on launchable activities (uses a scoped `<queries>` intent rather than `QUERY_ALL_PACKAGES`); YouTube is excluded from the block list so normal videos cannot be blocked accidentally.
- Explicit in-app accessibility disclosure and consent, plus an easy master switch.
- No screenshots, network requests, wake locks, polling loops, or foreground service.

## Build

- **Without Android Studio:** follow [BUILD_WITHOUT_ANDROID_STUDIO.md](BUILD_WITHOUT_ANDROID_STUDIO.md). The included GitHub Actions workflow builds a debug APK in the cloud.
- **With Android Studio:** open this directory, install Android SDK Platform 35 if requested, sync Gradle, and run the `app` configuration on your test device.

After installing the app, review the disclosure, enable the master switch, open Android Accessibility settings, enable **Focus Guard screen monitor**, and select the apps you want blocked. Test both a standard YouTube video and the Shorts player before relying on it.

## Important Shorts detection limitation

YouTube does not expose a public, stable API for identifying the Shorts player. `ShortsDetector.kt` is deliberately a conservative heuristic: it requires a selected Shorts destination/view-id hint plus at least two player-action labels. This is not guaranteed to match every YouTube release, locale, or OEM. It can also produce false positives if YouTube changes its accessibility tree. Before daily use, capture representative accessibility-node observations locally during testing and tune the matcher. Do not add broad text-only rules such as “any screen containing Shorts”, because the normal YouTube home feed can contain Shorts shelves.

The service sends Home immediately once a positive match is made. It does not kill YouTube's process or guarantee stopping background playback.

## Policy and distribution

This is a personal productivity/automation use of AccessibilityService, not a disability-support accessibility tool. The app does **not** set `isAccessibilityTool=true`. Before Google Play distribution, review the current Accessibility API policy, provide the required prominent disclosure/affirmative consent, complete the relevant Play Console declaration/review, and use narrower APIs where possible. Play approval is not guaranteed. Users must remain able to disable the service and uninstall the app.

## Technical notes

- Package name: `com.focusguard.android`
- Minimum Android: API 26
- Compile/target SDK: 35
- Java/Kotlin bytecode target: 17
- The selected-app list stores package names locally in SharedPreferences.
- The schedule is checked on incoming accessibility events using the device's current local date. No alarm or timer is needed for this fixed day-of-week policy.
- This environment did not include Android SDK/Gradle, so the project could not be assembled into an APK here. Open it in Android Studio to resolve dependencies and build it.
