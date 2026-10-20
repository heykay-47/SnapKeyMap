# The accessibility service and activities are referenced from the manifest, which R8 already keeps.

# Shizuku loads these Binder classes and their no-argument constructors by name.
-keep class com.heykay.snapkeymap.LogGrantService { *; }
-keep class com.heykay.snapkeymap.ShizukuLogService { *; }
