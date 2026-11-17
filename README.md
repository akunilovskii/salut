# Salut

Salut is an independent Android YouTube client based on [NewTube](https://github.com/aleixrodriala/newtube), with personal player, channel and playlist improvements. It is not affiliated with Google or YouTube.

[Download the latest release](https://github.com/akunilovskii/salut/releases/latest). Choose the arm64-v8a APK for most recent phones, or universal if unsure. Android 7.0 or newer is required.

Install the APK and allow installation from that source when Android asks. Later releases appear in You and Settings > About > Check for updates. Automatic checks can be disabled in About. Downloads and installation start only when requested. Android confirms installation. Existing personal Salut installations retain their accounts and settings because the package and signing certificate remain the same. Builds made before this public release need one manual update to enable this update source.

To open YouTube links in Salut, use You > Settings > General > Open YouTube links and follow the manual guide.

## Build

The public source includes the vendored SharedModules and MediaServiceCore dependencies. Install PowerShell 7 and Docker with a Linux AMD64 engine. Local tests and builds use the same immutable container image as GitHub Actions, including Java, Android SDK and PowerShell. The image is public and contains no signing key or account credentials.

```powershell
./personal/tools/Test.ps1
./personal/tools/Build.ps1
```

The default test command runs the complete offline corpus, including every required regression suite. Use `-Fast` for the smaller required-only set, and `-ForceExecution` for a fresh run. `-FullSuite` explicitly includes live Google/YouTube integration checks, which need network access and, where applicable, test accounts; add `-OfflineOnly` to exclude that category. Live-only checks can also be selected on the youtubeapi Gradle test task with `-PsalutLiveServicesOnly=true`.

Source stays in your checkout; Linux build output and caches use a Docker volume scoped to that checkout. Each local run exports its logs and testcase evidence under `build/maintenance/container-runs/`. Successful APK builds export to `smarttubetv/build/outputs/renamed_apks/`. Phone test JVMs reuse workers on Linux; `-IsolatePhoneWorkers` is available for validation.

For an explicit native fallback, use `-Native` with JDK 21, Android Platform 37.0 and Build Tools 37.0.0 configured through JAVA_HOME and ANDROID_HOME. Supplying `-ToolchainRoot` also selects the native toolchain. Windows/macOS native phone tests retain per-class JVM isolation. Native output and Linux volume caches are separate.

Official releases require the retained signing key through SALUT_SIGNING_KEY and `-PsalutRelease`. A local release container receives that file through a read-only mount; the key is never copied into the image. Development builds use a development key and cannot replace official builds.

GitHub Actions uses Ubuntu 24.04 to test each push and pull request. A version tag matching personal/release.json runs the tests, builds signed release APKs, verifies their package, version and certificate, and publishes the APKs, SHA256SUMS and salut.json update feed in GitHub Releases. The maintainer supplies SALUT_SIGNING_KEY_BASE64 through GitHub Secrets. GitHub release notes contain only the tagged version section from CHANGELOG.md; Unreleased and earlier versions are excluded. See CHANGELOG.md for release history and personal/features.json for the behavior inventory.

## License and attribution

MIT, see [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md). Salut builds on NewTube, SmartTube, SharedModules and MediaServiceCore. Their source and license notices are retained. [Privacy policy](PRIVACY.md).
