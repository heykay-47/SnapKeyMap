# The accessibility service and activities are referenced from the manifest, which R8 already keeps.

# Shizuku loads this Binder class and its no-argument constructor by name.
-keep class com.heykay.snapkeymap.LogGrantService { *; }
-keep class com.heykay.snapkeymap.ShizukuLogService { *; }
