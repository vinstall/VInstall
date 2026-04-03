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
| APKV   | Encrypted or plain split APKs archive (custom format, see [APKv_FORMAT.md](https://github.com/vinstall/apkv-spec/blob/main/README.md)) |
| ZIP    | Generic ZIP archive containing APK split files |

For split APK formats (XAPK, APKS, APKM, APKV, ZIP), individual splits can be selected or deselected before installation. APKM files marked as DRM-protected are rejected automatically. APKv files support optional password-based encryption.

### Install Modes

Three installation modes are available:

- **Normal** — uses the standard Android package installer
- **Root** — installs silently using root access
- **Shizuku** — installs silently via the [Shizuku](https://shizuku.rikka.app/) service without requiring full root

### App Manager

Browse all installed user apps with the ability to:

- View app details: version, SDK range, install and update dates, APK size, data directory, split count, and requested permissions
- Launch or open the system app info page
- Backup the app as an `.apks` archive
- Uninstall the app (supports Normal, Root, and Shizuku modes)
- Compute and copy the APK hash (MD5, SHA-1, SHA-256)

Backups are saved to `Documents/VInstall/Backups/` on external storage.

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
- "All Files Access" permission required for XAPK packages that include OBB data (Android 11+)
- Root access required when using Root mode
- [Shizuku](https://shizuku.rikka.app/) installed and running when using Shizuku mode

## Building

### Debug

```bash
./gradlew assembleDebug
```

### Release

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

## Gradle Wrapper

The `gradle/wrapper/gradle-wrapper.jar` file is included in the repository. However, if you want to generate the file again, just type:

```bash
gradle wrapper --gradle-version=8.4
```

## APKv Format

VInstall introduces **APKV**, a custom container format for archiving and distributing Android application packages. It supports plain and password-encrypted payloads, embeds an application icon, and includes a structured JSON manifest. The full specification is available in [APKv_FORMAT.md](https://github.com/vinstall/apkv-spec/blob/main/README.md).

## License

Licensed under the [GNU General Public License v3.0](LICENSE).

## Author

Developed by [AlwizBA](https://github.com/lenzarchive)/[VInstall](https://github.com/vinstall)
