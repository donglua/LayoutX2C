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

    @Test
    fun `scans resources from AGP R text symbol file`() {
        val symbolFile = tempDir.newFile("R.txt").apply {
            writeText(
                """
                int color dependency_divider 0x7f060001
                int dimen main_tab_height 0x7f070001
                int drawable panel_bg 0x7f080001
                int[] styleable Ignored { 0x7f010001 }
                int styleable Ignored_android_text 0
                """.trimIndent()
            )
        }

        val table = ResourceSymbolTable.fromSymbolFile(symbolFile)

        assertThat(table.references).containsAtLeast(
            ResourceReference("color", "dependency_divider"),
            ResourceReference("dimen", "main_tab_height"),
            ResourceReference("drawable", "panel_bg")
        )
        assertThat(table.references).doesNotContain(ResourceReference("styleable", "Ignored"))
    }

    @Test
    fun `scans resources and owner from AGP package aware symbol file`() {
        val symbolFile = tempDir.newFile("package-aware-r.txt").apply {
            writeText(
                """
                com.example.base
                color base_divider
                drawable base_panel
                com.example.feature
                string feature_title
                """.trimIndent()
            )
        }

        val table = ResourceSymbolTable.fromSymbolFile(symbolFile)

        assertThat(table.references).containsAtLeast(
            ResourceReference("color", "base_divider"),
            ResourceReference("drawable", "base_panel"),
            ResourceReference("string", "feature_title")
        )
        assertThat(table.owners[ResourceReference("color", "base_divider")])
            .isEqualTo("com.example.base")
        assertThat(table.owners[ResourceReference("string", "feature_title")])
            .isEqualTo("com.example.feature")
    }
}
