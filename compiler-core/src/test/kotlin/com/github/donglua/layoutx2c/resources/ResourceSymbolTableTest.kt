package com.github.donglua.layoutx2c.resources

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceSymbolTableTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `scans value and file based resources from res directory`() {
        val resDir = tempDir.newFolder("res")
        val valuesDir = resDir.resolve("values").apply { mkdirs() }
        valuesDir.resolve("colors.xml").writeText(
            """
            <resources>
                <color name="divider">@color/black</color>
                <dimen name="gap">8dp</dimen>
                <string name="title">Title</string>
                <style name="AppTheme" />
                <item name="item_color" type="color">#fff</item>
            </resources>
            """.trimIndent()
        )
        resDir.resolve("drawable").apply { mkdirs() }
            .resolve("panel.xml").writeText("<shape />")
        resDir.resolve("color-night").apply { mkdirs() }
            .resolve("chip.xml").writeText("<selector />")

        val table = ResourceSymbolTable.fromResDir(resDir)

        assertThat(table.references).containsAtLeast(
            ResourceReference("color", "divider"),
            ResourceReference("dimen", "gap"),
            ResourceReference("string", "title"),
            ResourceReference("style", "AppTheme"),
            ResourceReference("color", "item_color"),
            ResourceReference("drawable", "panel"),
            ResourceReference("color", "chip")
        )
    }
}
