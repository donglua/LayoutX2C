package com.github.donglua.layoutx2c.runtime

import java.util.ServiceConfigurationError
import java.util.ServiceLoader

internal object GeneratedLayoutRegistryLoader {

    fun load(classLoader: ClassLoader): Int {
        val providers = ServiceLoader.load(GeneratedLayoutRegistry::class.java, classLoader).iterator()
        var loadedCount = 0

        while (true) {
            val hasProvider = try {
                providers.hasNext()
            } catch (_: ServiceConfigurationError) {
                false
            } catch (_: LinkageError) {
                false
            }
            if (!hasProvider) {
                break
            }

            val provider = try {
                providers.next()
            } catch (_: ServiceConfigurationError) {
                continue
            } catch (_: LinkageError) {
                continue
            }
            try {
                provider.register()
                loadedCount++
            } catch (_: RuntimeException) {
                // A broken generated module must not disable other registries or reflection fallback.
            } catch (_: ServiceConfigurationError) {
                // A provider may fail while resolving one of its generated dependencies.
            } catch (_: LinkageError) {
                // A provider compiled against an incompatible runtime is ignored.
            }
        }

        return loadedCount
    }
}
