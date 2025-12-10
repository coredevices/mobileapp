# Keep all classes in androidx.sqlite and their members
-keep class androidx.sqlite.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep native methods and the classes that contain them, including their names and signatures
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}