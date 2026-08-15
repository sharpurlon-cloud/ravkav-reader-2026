# ProGuard/R8 Rules for RavKav Card Reader

# Keep all classes in com.unicapitalgroup package
-keep class com.unicapitalgroup.** { *; }

# Keep MainActivity and related classes
-keep class com.unicapitalgroup.ravkavreader.MainActivity { *; }
-keep class com.unicapitalgroup.ravkavreader.CryptoAnalyzer { *; }

# Keep inner classes
-keepclassmembers class com.unicapitalgroup.ravkavreader.CryptoAnalyzer$* { *; }

# Keep enum classes
-keepclasseswithmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Android framework classes
-keep public class android.** { *; }

# Keep NFC related classes
-keep class android.nfc.** { *; }
-keep class android.nfc.tech.** { *; }

# Keep androidx classes
-keep class androidx.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep exceptions
-keep public class * extends java.lang.Exception { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Allow access to private fields and methods for testing
-keepclassmembers class com.unicapitalgroup.ravkavreader.** {
    *** *;
}

# Optimization
-optimizationpasses 5
-dontoptimize

# Verbosity
-verbose
