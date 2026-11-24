# Room, WorkManager, ML Kit, Compose and coroutines ship their own consumer rules.
# Kept is open source, so there's nothing to hide by renaming: keep class names as they are.
# That also keeps every name-based lookup working (Room enums, WorkManager workers, manifest
# components) and makes crash reports readable without a mapping file.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
