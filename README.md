# VInstall

A minimal Android application for installing APK, XAPK, APKS, APKM, APKV, and ZIP packages, with batch install, install history, a built-in app manager with per-app timeline, backup, uninstaller, and silent install for trusted apps.

## Features

### Package Installation

Supports six package formats:

| Format | Description |
|--------|-------------|
| APK    | Standard Android package |
| XAPK   | APK with OBB expansion data or split APKs bundle |
| APKS   | Split APKs archive (SAI format) |
| APKM   | Split APKs archive (APKMirror format) |
| APKV   | Encrypted or plain split APKs archive (custom format, see [APKV spec](https://github.com/vinstall/apkv-spec/blob/main/README.md)) |
| ZIP    | Generic ZIP archive containing APK split files | 

For split APK formats (XAPK, APKS, APKM, APKV, ZIP), individual splits can be selected or deselected before installation. APKV files support optional password-based encryption and integrity verification via SHA-256 checksums.

Packages can also be opened directly from a file manager or browser. VInstall registers itself as a handler for these file types and shows a confirmation sheet with the app details before installing.

### Batch Install

Pick several files at once and install them one after another:

- Drag to reorder the queue before starting
- Overall progress is shown as the current file out of the total
- Encrypted APKV files in the queue are marked as locked, tap them to enter the password before installing
- Unreadable files are skipped instead of stopping the whole batch
- Install progress and results are also posted as notifications

### Install History

Every installation is recorded with its result (success, failed, or cancelled). Tap an entry to see the package name, version, format, file size, install mode, duration, time, and result details. Single records can be deleted, or the whole history can be cleared from Settings.

### Install Modes

Three installation modes are available:

- **Normal** - uses the standard Android package installer
- **Root** - installs silently using root access
- **Shizuku** - installs silently via the [Shizuku](https://shizuku.rikka.app/) service without requiring full root

With Root or Shizuku, the **Silent install** setting installs without the system prompt. When it is off, VInstall asks for confirmation before installing.

### Install from Other Apps

With **Silent install from other apps** enabled (Shizuku or Root mode), trusted apps can install packages through VInstall in the background, with no screen.

- Apps not on the Trusted apps list trigger a warning with Continue and Block options. An optional delay can be set before Continue becomes available
- Apps can be blocked permanently or for a limited time, and unblocked from the Blocked apps screen
- Blocked requests are rejected and recorded in the install history

### App Manager

Browse all installed user apps with the ability to:

- Search by app name or package name, sort by name, size, install date, or update date, and optionally include system apps
- View app details: version, SDK range, install and update dates, APK size, data directory, split count, and requested permissions
- Launch or open the system app info page
- Export the app as an `.apkv` archive
- Open the app's Timeline
- Uninstall the app (supports Normal, Root, and Shizuku modes)
- Compute and copy the APK hash (MD5, SHA-1, SHA-256)

### Backup/Export

Export any installed user app as an `.apkv` archive directly from the App Manager or the dedicated Backup screen. Backups are saved to `Documents/VInstall/Backups/` on external storage. Optional password-based encryption is supported when exporting.

### Timeline

Tracks the version history of each app as a tree of events: detected, installed, updated, downgraded, reinstalled, uninstalled, and failed attempts. Entries are tagged when the change was forced, when data was kept or erased, when the signature changed, when permissions were added or removed, or when it happened outside VInstall (detected by comparing app snapshots, so details are limited). Tap an event to see the previous version, installer, signature (SHA-256), SDK range, data state, and permission changes. Open it from the App Manager (all apps) or from an app's detail page (single app). Timelines can be cleared from the same screen.

### Settings

| Section | Settings |
|---------|----------|
| Install Mode | Normal, Root, or Shizuku |
| Behavior | Silent install, hide notifications, auto clear cache, debug tab |
| Security | Silent install from other apps, trusted and blocked apps, continue delay for untrusted apps |
| Experimental | Install apps with incompatible API, allow downgrade, force install on signature mismatch (the last two can keep app data) |
| Appearance | Language (system, English, Bahasa Indonesia, Русский, 中文（简体）), theme (light, dark, system), dialog style (Bottom Sheet or Alert Dialog) |
| Accessibility | Text size, display size, font, animation speed, high contrast, bold text, large touch targets, haptic feedback, message duration |
| History | View, inspect, or clear install history |
| Crash Reports | View, copy, or clear crash logs |
| About | Credits: license, open-source libraries, and thanks |

Experimental options are still being tested and may not work on every device or Android version.

If VInstall crashed during the previous session, a prompt appears on the next launch with a shortcut to the saved report.

## Requirements

- Android 5.0 (API 21) or higher
- "Install unknown apps" permission granted for this app
- "All Files Access" permission required for XAPK packages that include OBB data and for writing backups to external storage (Android 11+)
- Root access required when using Root mode
- Shizuku or Root mode required for silent install and silent install from other apps
- [Shizuku](https://shizuku.rikka.app/) installed and running when using Shizuku mode

## Building

### Debug

```bash
./gradlew assembleDebug
```

### Release

#### Using GitHub Actions (CI)

Before triggering a release build, create a keystore and configure the following secrets in your GitHub repository settings:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore file |
| `STORE_PASSWORD`  | Keystore password |
| `KEY_ALIAS`       | Key alias |
| `KEY_PASSWORD`    | Key password |

Then push a tag prefixed with `v` to trigger the release workflow:

```bash
git tag v0.4.6
git push origin v0.4.6
```

The signed release APK will be automatically attached to the corresponding GitHub Release.

#### Using a Local Keystore

Alternatively, you can build a signed release APK locally by adding the following properties to your `local.properties` file:

```properties
STORE_FILE=/absolute/path/to/your/keystore.jks
STORE_PASSWORD=your_store_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

Then run:

```bash
./gradlew assembleRelease
```

## Gradle Wrapper

The `gradle/wrapper/gradle-wrapper.jar` file is included in the repository. However, if you want to generate the file again, just type:

```bash
gradle wrapper --gradle-version=9.4.1
```

## APKV Format

VInstall introduces **APKV**, a custom container format for archiving and distributing Android application packages. It supports plain and password-encrypted payloads, embeds an application icon, includes integrity checksums per APK file, and includes a structured JSON manifest. The full specification is available in the [APKV spec](https://github.com/vinstall/apkv-spec/blob/main/README.md) and [apkv-cli](https://github.com/vinstall/apkv-cli).

## License

Licensed under the [GNU General Public License v3.0](LICENSE).

## Credits

This project uses the following open-source libraries:

| Library | Author | License | Notes |
|---------|--------|---------|-------|
| [AndroidX](https://developer.android.com/jetpack/androidx) | Google | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) | |
| [Material Components for Android](https://github.com/material-components/material-components-android) | Google | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) | |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | JetBrains | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) | |
| [Gson](https://github.com/google/gson) | Google | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) | |
| [Android Desugar JDK Libs](https://github.com/google/desugar_jdk_libs) | Google | [GPL-2.0 + Classpath Exception](https://opensource.org/license/gpl-2-0) | Core library desugaring |
| [Shizuku API](https://github.com/RikkaApps/Shizuku-API) | RikkaApps | [MIT](https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE) | `api` and `provider` modules only |
| [Poppins](https://github.com/itfoundry/Poppins) | Indian Type Foundry | [OFL 1.1](https://openfontlicense.org/) | Typeface |
| [Bouncy Castle](https://www.bouncycastle.org/) | The Legion of the Bouncy Castle | [MIT-style](https://www.bouncycastle.org/licence.html) | Removed in v0.4.2-hotfix2 |

## Author

Developed by [AlwizBA](https://github.com/lenzarchive)/[VInstall](https://github.com/vinstall)
