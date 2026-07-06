# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK proguard-android-optimize.txt file.

# Keep InfiPush public API surface
-keep class com.infipush.InfiPush { *; }
-keep class com.infipush.model.** { *; }
-keep class com.infipush.network.DevicePayload { *; }

# Keep FCM service and receiver
-keep class com.infipush.service.InfiPushMessagingService { *; }
-keep class com.infipush.receiver.NotificationClickReceiver { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
