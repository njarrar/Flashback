# R8 rules for release builds. The libraries in use (kotlinx.serialization, WorkManager,
# Coil, Compose) ship their own rules; these lines only add what the app itself needs.

# Keep line numbers in crash reports. Upload the mapping file to Play to read them.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager stores the worker's class name with each scheduled job, so a renamed
# class would break reminders scheduled by an older version.
-keep class com.njarrar.backthen.data.ReminderWorker { *; }
