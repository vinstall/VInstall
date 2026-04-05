# VInstall

A minimal Android application for installing APK, XAPK, APKS, APKM, APKV, and ZIP packages, with a built-in app manager, backup, and uninstaller.

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

For split APK formats (XAPK, APKS, APKM, APKV, ZIP), individual splits can be selected or deselected before installation. APKM files marked as DRM-protected are rejected automatically. APKV files support optional password-based encryption and integrity verification via SHA-256 checksums.

### Install Modes

Three installation modes are available:

- **Normal** — uses the standard Android package installer
- **Root** — installs silently using root access
- **Shizuku** — installs silently via the [Shizuku](https://shizuku.rikka.app/) service without requiring full root

### App Manager

Browse all installed user apps with the ability to:

- View app details: version, SDK range, install and update dates, APK size, data directory, split count, and requested permissions
- Launch or open the system app info page
- Export the app as an `.apkv` archive
- Uninstall the app (supports Normal, Root, and Shizuku modes)
- Compute and copy the APK hash (MD5, SHA-1, SHA-256)

### Backup

Export any installed user app as an `.apkv` archive directly from the App Manager or the dedicated Backup screen. Backups are saved to `Documents/VInstall/Backups/` on external storage. Optional password-based encryption is supported when exporting.

### Settings

| Setting | Description |
|---------|-------------|
| Install mode | Normal, Root, or Shizuku |
| Theme | Light, Dark, or follow system |
| Confirm before install | Show a confirmation dialog before installing |
| Clear cache after install | Automatically remove temp files after installation |
| Debug window | Show or hide the in-app log viewer |

## Requirements

- Android 5.0 (API 21) or higher
- "Install unknown apps" permission granted for this app
- "All Files Access" permission required for XAPK packages that include OBB data and for writing backups to external storage (Android 11+)
- Root access required when using Root mode
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
git tag v1.0.0
git push origin v1.0.0
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

| Library | Author | License |
|---------|--------|---------|
| [AndroidX](https://developer.android.com/jetpack/androidx) | Google | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Material Components for Android](https://github.com/material-components/material-components-android) | Google | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | JetBrains | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Gson](https://github.com/google/gson) | Google | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Shizuku](https://github.com/RikkaApps/Shizuku) | RikkaApps | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Bouncy Castle](https://www.bouncycastle.org/) | The Legion of the Bouncy Castle | [MIT-style](https://www.bouncycastle.org/licence.html) |

## Author

Developed by [AlwizBA](https://github.com/lenzarchive)/[VInstall](https://github.com/vinstall)
