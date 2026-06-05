package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.github.donglua.layoutx2c.resources.StyleResourceRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StyleExpansionCodegenTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `expanded style attributes are emitted by existing attribute handlers`() {
        val resDir = tempDir.newFolder("res")
        resDir.resolve("values").mkdirs()
        resDir.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="TitleText">
                    <item name="android:text">Styled</item>
                    <item name="android:textColor">#112233</item>
                </style>
            </resources>
            """.trimIndent()
        )
        val analyzer = LayoutAnalyzerV2(
            styleResolver = StyleResourceRepository.fromResDir(resDir)
        )
        val generator = LayoutCodeGenerator(
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                style="@style/TitleText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        """.trimIndent()

        val analyzed = analyzer.analyze(XmlLayoutParser().parse(xml, "styled_text").root)
        val generated = generator.generate(analyzed, "styled_text", "R.layout.styled_text").toString()

        assertThat(generated).contains("text = \"Styled\"")
        assertThat(generated).contains("setTextColor(Color.parseColor(\"#112233\"))")
        assertThat(generated).doesNotContain("style")
    }
}
