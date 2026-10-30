# SnapKey Map agent guide

SnapKey Map is an Android app that maps the Oppo Find X9's Snap Key to apps and actions. It uses Kotlin, Jetpack Compose and Material 3. The minimum SDK is 29 and the target SDK is 36. The package is `com.heykay.snapkeymap`.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before you change code. Its "Find the relevant code" section maps each source file to its job. Its "Find X9 device notes" section records how the phone reports the key.

## Build in CI only

Do not install a JDK or the Android SDK on the host. The [Build APK workflow](.github/workflows/build.yml) runs `./gradlew testReleaseUnitTest assembleRelease`, checks the APK permissions and proves the build is reproducible.

- A pull request build uses a temporary debug key. Its APK does not install over a release build.
- To get a release-signed APK for the phone, push the branch and run `scripts/phone-build`. It runs the workflow on that branch and copies the APK to the maintainer's Downloads folder.

## Follow these conventions

- Give each pull request one change and a Conventional Commit title, such as `fix(engine): ignore duplicate key edges`.
- Put each closed issue on its own `Fixes #n` line.
- Commit as `heykay-47 <krithick008@proton.me>`.
- Change settings through `ConfigStore.update`, as in `SnapEngine.store.update { it.copy(enabled = on) }`.
- Serialize with `org.json`. Do not add a JSON library.
- Do not add the `INTERNET` permission. If a change needs another permission, update `app/expected-permissions.txt` in the same pull request and say why. CI fails on any unlisted permission.

## Prepare a release only with the maintainer

- `versionCode` is major × 10000 + minor × 100 + patch. For example, 1.2.0 is `10200`.
- Write the release notes in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, at most 500 characters.
- Add a `## Delivered in v<version>` section to `ROADMAP.md`.
- Run `scripts/release-check`. It checks the three items above.
- Never create or push a version tag. A tag publishes a release, and only the maintainer does that.

## Test on the device honestly

The maintainer has one phone and no emulator. The `verify-snapkeymap` skill in `.agents/skills/` drives that phone over adb and lists the rules that protect its settings. Unit tests do not prove phone behavior. Never say a device test passed unless it ran and you saw the result.
