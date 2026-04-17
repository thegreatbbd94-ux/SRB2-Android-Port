# This file is intentionally left blank.
# It prevents Proguard from stripping SDL2 and SRB2 classes.
-keep class org.libsdl.app.** { *; }
-keep class org.srb2.android.** { *; }
