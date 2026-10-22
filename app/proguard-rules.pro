# R8 rules for release builds (ROADMAP 14 Release, 22). pd-core ships none of its own.

# libpd: its native code calls back into Java by name (receiveFloat, receiveList... on whatever PdBase.setReceiver
# was given) and finds its own classes by name, so keep all of it, and every class that receives from Pd.
-keep class org.puredata.** { *; }
-keep class * implements org.puredata.core.PdReceiver { *; }
-keep class * implements org.puredata.core.PdMidiReceiver { *; }
-keep class * implements org.puredata.core.PdListener { *; }

# AudioService looks these two up by name, as only some pd-core releases have them (setDevicesId: the chosen mic and
# headphones; audioRuntimeInfo: the rate Android really gave). Kept by the rule above too; named here so it stays
# true if that rule narrows.
-keepclassmembers class org.puredata.android.io.PdAudio { public static *** setDevicesId(int, int); }
-keepclassmembers class org.puredata.core.PdBase { public static *** audioRuntimeInfo(java.lang.String); }

# Readable crash reports: keep line numbers, hide the source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
