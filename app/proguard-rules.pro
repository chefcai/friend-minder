# Add project specific ProGuard rules here.
# For MVP, minification is disabled (see app/build.gradle.kts). These rules
# are kept in place for when release minification is turned on later.

# Keep data model classes intact for Gson (de)serialization.
-keep class com.example.friendminder.data.models.** { *; }
