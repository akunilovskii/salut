# Salut

Salut is an independent Android YouTube client based on [NewTube](https://github.com/aleixrodriala/newtube), with personal player, channel and playlist improvements. It is not affiliated with Google or YouTube.

[Download the latest release](https://github.com/akunilovskii/salut/releases/latest). Choose the arm64-v8a APK for most recent phones, or universal if unsure. Android 7.0 or newer is required.

Install the APK and allow installation from that source when Android asks. Later releases appear in You and Settings > About > Check for updates. Automatic checks can be disabled in About. Downloads and installation start only when requested. Android confirms installation. Existing personal Salut installations retain their accounts and settings because the package and signing certificate remain the same. Builds made before this public release need one manual update to enable this update source.

To open YouTube links in Salut, use You > Settings > General > Open YouTube links and follow the manual guide.

## Build

The public source includes the modified SharedModules and MediaServiceCore dependencies. No private submodule access is needed. Install JDK 21, PowerShell 7, and Android SDK Platform 37.0 with Build Tools 37.0.0. Set JAVA_HOME and ANDROID_HOME. On Windows, Linux or macOS, run these commands in PowerShell:

```powershell
./personal/tools/Test.ps1
./personal/tools/Build.ps1
```

The scripts select the platform's Java and Gradle executables. On Linux or macOS, a direct Gradle build is also available with `bash gradlew :smarttubetv:assembleStmobileDebug`. The wrapper downloads its pinned, hash-checked Gradle bootstrap. Development builds use a local development key and cannot replace official builds. The official signing key is not included in the public source.

GitHub Actions uses Ubuntu 24.04 to test each push and pull request. A version tag matching personal/release.json runs the tests, builds signed release APKs, verifies their package, version and certificate, and publishes the APKs, SHA256SUMS and salut.json update feed in GitHub Releases. The maintainer supplies SALUT_SIGNING_KEY_BASE64 through GitHub Secrets. GitHub release notes contain only the tagged version section from CHANGELOG.md; Unreleased and earlier versions are excluded. See CHANGELOG.md for release history and personal/features.json for the behavior inventory.

## License and attribution

MIT, see [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md). Salut builds on NewTube, SmartTube, SharedModules and MediaServiceCore. Their source and license notices are retained. [Privacy policy](PRIVACY.md).
