# Add project specific ProGuard rules here.

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# Keep Room entities
-keep class com.notifyai.data.local.entity.** { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep domain models
-keep class com.notifyai.domain.model.** { *; }

# llama.cpp JNI
-keep class com.llama.** { *; }
-keep class ai.ggml.** { *; }
