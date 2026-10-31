# Gullak release keep rules.
# Hilt, Room, Navigation (type-safe routes) and kotlinx.serialization ship consumer rules in their AARs/JARs;
# only app-specific rules belong here.

# Keep @Serializable navigation routes and DTOs: their generated serializers are looked up reflectively by name.
-keepclassmembers @kotlinx.serialization.Serializable class com.shivangi.gullak.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
