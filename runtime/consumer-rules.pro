# Keep LayoutFactory implementations
-keep class * implements com.github.donglua.layoutx2c.runtime.LayoutFactory { *; }

# Keep generated layout classes in the default namespace
-keep class com.github.donglua.layoutx2c.generated.** { *; }

# Keep app-package generated registries loaded reflectively by LayoutX2CRegistry
-keep class **.generated.LayoutX2CGenerated { *; }
