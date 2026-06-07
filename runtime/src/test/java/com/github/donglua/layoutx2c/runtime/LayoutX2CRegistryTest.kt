package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

class LayoutX2CRegistryTest {

    @Test
    fun `generated registry lookup caches packages that do not have generated output`() {
        val source = runtimeSource("LayoutX2CRegistry.kt")

        assertWithMessage("failed package cache should be declared")
            .that(source)
            .contains("failedPackages")
        assertWithMessage("failed package cache should short-circuit later registry lookups")
            .that(source)
            .contains("if (packageName in failedPackages)")
        assertWithMessage("failed registry lookup should mark the package as failed")
            .that(source)
            .contains("failedPackages.add(packageName)")
    }

    @Test
    fun `negative cache for missing layout IDs avoids repeated lookups`() {
        val source = runtimeSource("LayoutX2CRegistry.kt")

        assertWithMessage("negative cache should be declared for missing layouts")
            .that(source)
            .contains("knownMissingLayouts")
        assertWithMessage("negative cache should provide fast path for known missing layouts")
            .that(source)
            .contains("if (layoutId in knownMissingLayouts)")
        assertWithMessage("missing layout should be recorded in negative cache")
            .that(source)
            .contains("knownMissingLayouts.add(layoutId)")
    }

    private fun runtimeSource(fileName: String): String {
        val moduleDir = listOf(File("."), File("runtime"))
            .map { it.canonicalFile }
            .first { File(it, "src/main/java").isDirectory }
        return File(
            moduleDir,
            "src/main/java/com/github/donglua/layoutx2c/runtime/$fileName"
        ).readText()
    }
}
