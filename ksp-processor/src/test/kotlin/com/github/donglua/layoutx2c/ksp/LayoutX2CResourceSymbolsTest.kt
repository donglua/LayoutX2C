package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.resources.ResourceReference
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutX2CResourceSymbolsTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `combines source res symbols with inferred AGP symbols`() {
        val projectDir = tempDir.newFolder("app")
        val resDir = projectDir.resolve("src/main/res").apply { mkdirs() }
        resDir.resolve("values").apply { mkdirs() }
            .resolve("colors.xml")
            .writeText("<resources><color name=\"local_panel\">#fff</color></resources>")
        projectDir
            .resolve("build/intermediates/runtime_symbol_list/debug/processDebugResources/R.txt")
            .apply {
                parentFile.mkdirs()
                writeText("int color dependency_panel 0x7f060001")
            }

        val symbols = LayoutX2CResourceSymbols.resolve(
            resDir = resDir,
            explicitSymbolFiles = emptyList()
        )

        assertThat(symbols.references).containsAtLeast(
            ResourceReference("color", "local_panel"),
            ResourceReference("color", "dependency_panel")
        )
    }

    @Test
    fun `combines explicit AGP symbol files`() {
        val projectDir = tempDir.newFolder("app")
        val resDir = projectDir.resolve("src/main/res").apply { mkdirs() }
        val explicitSymbols = tempDir.newFile("explicit-R.txt").apply {
            writeText("int color explicit_panel 0x7f060001")
        }

        val symbols = LayoutX2CResourceSymbols.resolve(
            resDir = resDir,
            explicitSymbolFiles = listOf(explicitSymbols)
        )

        assertThat(symbols.references)
            .contains(ResourceReference("color", "explicit_panel"))
    }
}
