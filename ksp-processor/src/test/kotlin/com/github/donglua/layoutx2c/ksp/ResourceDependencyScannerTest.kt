package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import java.security.MessageDigest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceDependencyScannerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `scan follows layout and value references recursively`() {
        val resDir = tempDir.newFolder("recursive-res")
        resDir.resolve("layout").mkdirs()
        resDir.resolve("values").mkdirs()
        resDir.resolve("layout/demo.xml").writeText(
            """
            <TextView
                android:text="@string/title"
                android:textColor="@color/title_color" />
            """.trimIndent()
        )
        resDir.resolve("values/strings.xml").writeText(
            """
            <resources>
                <string name="title">@string/app_name</string>
                <string name="app_name">LayoutX2C</string>
            </resources>
            """.trimIndent()
        )
        resDir.resolve("values/colors.xml").writeText(
            """
            <resources>
                <color name="title_color">#ff0000</color>
                <color name="unused">#00ff00</color>
            </resources>
            """.trimIndent()
        )

        val dependencies = ResourceDependencyScanner.scan(
            layoutFile = resDir.resolve("layout/demo.xml"),
            resDir = resDir
        )

        assertThat(dependencies.map { it.key }).containsAtLeast(
            "string/title",
            "string/app_name",
            "color/title_color"
        )
        assertThat(dependencies.map { it.key }).doesNotContain("color/unused")
    }

    @Test
    fun `scan includes all values qualifier definitions for a referenced key`() {
        val resDir = tempDir.newFolder("qualified-res")
        resDir.resolve("layout").mkdirs()
        resDir.resolve("values").mkdirs()
        resDir.resolve("values-land").mkdirs()
        resDir.resolve("layout/demo.xml").writeText(
            """<TextView android:text="@string/title" />"""
        )
        resDir.resolve("values/strings.xml").writeText(
            """<resources><string name="title">Portrait</string></resources>"""
        )
        resDir.resolve("values-land/strings.xml").writeText(
            """<resources><string name="title">Landscape</string></resources>"""
        )

        val dependencies = ResourceDependencyScanner.scan(
            layoutFile = resDir.resolve("layout/demo.xml"),
            resDir = resDir
        )

        val titleDependencies = dependencies.filter { it.key == "string/title" }
        assertThat(titleDependencies.mapNotNull { it.file?.relativeTo(resDir)?.invariantSeparatorsPath })
            .containsExactly("values/strings.xml", "values-land/strings.xml")
        assertThat(titleDependencies.map { it.content }.joinToString(separator = "\n"))
            .contains("Portrait")
        assertThat(titleDependencies.map { it.content }.joinToString(separator = "\n"))
            .contains("Landscape")
    }

    @Test
    fun `scan includes drawable file content dependencies`() {
        val resDir = tempDir.newFolder("drawable-res")
        resDir.resolve("layout").mkdirs()
        resDir.resolve("drawable").mkdirs()
        resDir.resolve("layout/demo.xml").writeText(
            """<ImageView android:src="@drawable/logo" />"""
        )
        val logoBytes = byteArrayOf(0x00, 0x7f, 0x50, 0x4e, 0x47, 0x01, 0x02)
        resDir.resolve("drawable/logo.png").writeBytes(logoBytes)

        val dependencies = ResourceDependencyScanner.scan(
            layoutFile = resDir.resolve("layout/demo.xml"),
            resDir = resDir
        )

        val logo = dependencies.single { it.key == "drawable/logo" }
        assertThat(logo.file?.relativeTo(resDir)?.invariantSeparatorsPath).isEqualTo("drawable/logo.png")
        assertThat(logo.content).isEqualTo(sha256Hex(logoBytes))
    }

    @Test
    fun `scan records unresolved references conservatively`() {
        val resDir = tempDir.newFolder("unresolved-res")
        resDir.resolve("layout").mkdirs()
        resDir.resolve("layout/demo.xml").writeText(
            """<TextView android:text="@string/missing_title" />"""
        )

        val dependencies = ResourceDependencyScanner.scan(
            layoutFile = resDir.resolve("layout/demo.xml"),
            resDir = resDir
        )

        assertThat(dependencies.map { it.key }).contains("unresolved:string/missing_title")
    }

    @Test
    fun `scan ignores id references as digest resource dependencies`() {
        val resDir = tempDir.newFolder("id-res")
        resDir.resolve("layout").mkdirs()
        resDir.resolve("layout/demo.xml").writeText(
            """
            <TextView
                android:id="@+id/title"
                android:labelFor="@id/name" />
            """.trimIndent()
        )

        val dependencies = ResourceDependencyScanner.scan(
            layoutFile = resDir.resolve("layout/demo.xml"),
            resDir = resDir
        )

        assertThat(dependencies).isEmpty()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }
    }
}
