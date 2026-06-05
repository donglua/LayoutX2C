package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.github.donglua.layoutx2c.resources.StyleResourceRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutAnalyzerV2StyleTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val parser = XmlLayoutParser()

    @Test
    fun `static app style expands into supported attributes`() {
        val analyzer = analyzerWithStyles(
            """
            <resources>
                <style name="TitleText">
                    <item name="android:text">Styled</item>
                    <item name="android:textSize">16sp</item>
                </style>
            </resources>
            """.trimIndent()
        )
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                style="@style/TitleText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        """.trimIndent()

        val result = analyzer.analyze(parser.parse(xml, "styled_text").root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.node.attributes).containsEntry("android:text", "Styled")
        assertThat(result.node.attributes).containsEntry("android:textSize", "16sp")
        assertThat(result.node.attributes).doesNotContainKey("style")
        assertThat(result.supportedAttributes).containsAtLeast("android:text", "android:textSize")
    }

    @Test
    fun `explicit xml attribute overrides style attribute`() {
        val analyzer = analyzerWithStyles(
            """
            <resources>
                <style name="TitleText">
                    <item name="android:text">FromStyle</item>
                </style>
            </resources>
            """.trimIndent()
        )
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                style="@style/TitleText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="FromXml" />
        """.trimIndent()

        val result = analyzer.analyze(parser.parse(xml, "styled_text").root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.node.attributes).containsEntry("android:text", "FromXml")
    }

    @Test
    fun `unsupported style item falls back instead of skipping the item`() {
        val analyzer = analyzerWithStyles(
            """
            <resources>
                <style name="UnsafeText">
                    <item name="android:unknownAttr">value</item>
                </style>
            </resources>
            """.trimIndent()
        )
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                style="@style/UnsafeText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        """.trimIndent()

        val result = analyzer.analyze(parser.parse(xml, "unsafe_style").root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `theme attribute still forces fallback`() {
        val analyzer = analyzerWithStyles("<resources />")
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:theme="@style/AppTheme" />
        """.trimIndent()

        val result = analyzer.analyze(parser.parse(xml, "themed_text").root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    private fun analyzerWithStyles(valuesXml: String): LayoutAnalyzerV2 {
        val resDir = tempDir.newFolder()
        resDir.resolve("values").mkdirs()
        resDir.resolve("values/styles.xml").writeText(valuesXml)
        return LayoutAnalyzerV2(
            styleResolver = StyleResourceRepository.fromResDir(resDir)
        )
    }
}
