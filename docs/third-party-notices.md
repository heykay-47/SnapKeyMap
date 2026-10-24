# Third-party notice sources

`app/src/main/assets/licenses/notices.json` is the production dependency-group registry.
`LicensesScreen.kt` reads that registry, `attributions.txt`, and both full licence texts offline.
The registry records pinned versions, inspected source URLs, and text hashes. No internet permission or licence dependency is added.

## Evidence reviewed on 2026-10-24

- Shizuku API and provider 13.1.5 POMs declare MIT. API requests aidl and shared 13.1.5, whose POMs also declare MIT. Their other dependency is AndroidX annotation 1.3.0. The MIT text comes from Shizuku-API commit `aaba9928f50f9df62c5e429318de14a0ed473480`, the last commit before publication on 2023-09-21. Its `manifest.gradle` confirms version 13.1.5. There is no 13.1.5 repository tag. The published API source JAR has no separate licence file.
- HiddenApiBypass 6.1 POM declares Apache-2.0 and has no POM runtime dependencies. The full Apache text is copied from its `v6.1/LICENSE`. Its published source JAR supplies the LSPosed copyright and permission notice.
- Compose BOM 2025.10.01 pins UI 1.9.4, Material3 1.4.0, and extended icons 1.7.8. Core KTX 1.17.0, Activity Compose 1.11.0, and Lifecycle Runtime Compose 2.9.4 are direct pins. Their compile/runtime POM dependency traversal and BOM-constrained Compose requests were inspected. AndroidX POMs declare Apache-2.0. Core 1.17.0 and UI Android 1.9.4 source JARs contain the complete Apache terms in `META-INF/androidx/.../LICENSE.txt`.
- The traversal includes AndroidX activity, annotation, arch.core, autofill, collection, compose.animation, compose.foundation, compose.material, compose.material3, compose.runtime, compose.ui, concurrent, core, customview, emoji2, graphics, interpolator, lifecycle, profileinstaller, savedstate, startup, tracing, and versionedparcelable.
- Non-AndroidX transitive POM groups are `org.jetbrains.kotlin`, `org.jetbrains.kotlinx`, `org.jetbrains`, `org.jspecify`, and `com.google.guava`. Their registry entries record version requests. Kotlin 2.2.21 is the app's toolchain standard-library version. Coroutines, atomicfu, serialization, annotations, and JSpecify declare Apache-2.0. ListenableFuture 1.0's POM omits a licence, so its published source JAR's full Apache copyright and permission header supplies that evidence.
- The bundled Apache text matches HiddenApiBypass's licence, including the appendix. The bundled MIT text matches the Shizuku source licence. Attribution blocks preserve the inspected upstream wording. The coroutines copyright and permission block is from its 1.8.1 licence appendix. Other attribution blocks identify their source file in `attributions.txt`.

## Verification and remaining gaps

Run `python3 scripts/check-license-notices.py` to check the actual production registry, source references, attribution file, and full text hashes. It checks every direct implementation group and the identified runtime-transitive groups. An optional POM inventory JSON argument checks every inventory entry's group. `artifacts` entries must contain a `coordinate` string.

This is a conservative POM traversal, not Gradle dependency resolution. It includes older superseded requests and may include variants that the APK does not use. POMs defer to richer Gradle module metadata. Final Android variant selection, version conflict resolution, R8 removal, and the APK's complete embedded NOTICE inventory have not been verified because this worker must not run Gradle or build an APK.

Some old Kotlin POM dependencies omit inherited versions. Four older AndroidX POM fetches failed during traversal: collection 1.5.0, lifecycle-runtime 2.3.1, annotation-experimental 1.1.0, and core-ktx 1.9.0. Their groups are covered, but their missing transitive edges are not asserted complete. Attribution blocks are verified source notices, not an exhaustive per-file copyright inventory of all AndroidX artifacts.

The v1.1.0 release build's resolved runtime dependency report mapped every group to this registry, and its packaged licence assets matched the source. Repeat that check for each release. No claim of complete legal compliance is made for uninspected transitives or embedded third-party code.
