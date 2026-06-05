package com.github.donglua.layoutx2c.resources

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StyleResourceRepositoryTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `expands current module style with parent chain`() {
        val resDir = tempDir.newFolder("res")
        resDir.resolve("values").mkdirs()
        resDir.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="BaseText">
                    <item name="android:textColor">#112233</item>
                </style>
                <style name="TitleText" parent="@style/BaseText">
                    <item name="android:textSize">16sp</item>
                    <item name="android:text">Styled</item>
                </style>
            </resources>
            """.trimIndent()
        )

        val expansion = StyleResourceRepository.fromResDir(resDir).expand("@style/TitleText")

        assertThat(expansion).isInstanceOf(StyleExpansion.Expanded::class.java)
        assertThat((expansion as StyleExpansion.Expanded).attributes).containsExactly(
            "android:textColor",
            "#112233",
            "android:textSize",
            "16sp",
            "android:text",
            "Styled"
        )
    }

    @Test
    fun `rejects style with qualifier variant`() {
        val resDir = tempDir.newFolder("res")
        resDir.resolve("values").mkdirs()
        resDir.resolve("values-night").mkdirs()
        resDir.resolve("values/styles.xml").writeText(
            """
            <resources>
                <style name="TitleText">
                    <item name="android:text">Day</item>
                </style>
            </resources>
            """.trimIndent()
        )
        resDir.resolve("values-night/styles.xml").writeText(
            """
            <resources>
                <style name="TitleText">
                    <item name="android:text">Night</item>
                </style>
            </resources>
            """.trimIndent()
        )

        val expansion = StyleResourceRepository.fromResDir(resDir).expand("@style/TitleText")

        assertThat(expansion).isInstanceOf(StyleExpansion.Unsupported::class.java)
        assertThat((expansion as StyleExpansion.Unsupported).reason).contains("qualifier")
    }
}
