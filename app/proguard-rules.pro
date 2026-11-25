# NightBaseline release rules. Compose, Hilt, Room, WorkManager, Navigation, kotlinx.serialization and Haze ship their
# own consumer rules; the rules below cover what is looked up by name at runtime in this app.

# Typed navigation routes (nav/Routes.kt) are found through their generated serializers, by reflection on the
# companion / object instance. Keep the route classes and their serializers.
-keep @kotlinx.serialization.Serializable class io.github.alien2003.nightbaseline.nav.** { *; }
-keepclassmembers class io.github.alien2003.nightbaseline.nav.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Stored nights are JSON of the core-algo types (data-local NightJson): keep their serializers so a release build
# reads rows written by any other build.
-keepclassmembers @kotlinx.serialization.Serializable class io.github.alien2003.nightbaseline.algo.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.alien2003.nightbaseline.algo.**$$serializer { *; }

# WorkManager instantiates the Hilt worker by class name through HiltWorkerFactory.
-keep class io.github.alien2003.nightbaseline.work.worker.NightWorker { <init>(...); }
