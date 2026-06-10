package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutX2CLayoutDependencyTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `expands generated layout names with include dependencies`() {
        val resDir = tempDir.newFolder("res")
        val layoutDir = resDir.resolve("layout").apply { mkdirs() }
        layoutDir.resolve("entry.xml").writeText(
            """
            <LinearLayout>
                <include layout="@layout/toolbar" />
            </LinearLayout>
            """.trimIndent()
        )
        layoutDir.resolve("toolbar.xml").writeText(
            """
            <FrameLayout>
                <include layout="@layout/title" />
            </FrameLayout>
            """.trimIndent()
        )
        layoutDir.resolve("title.xml").writeText("<TextView />")

        val expanded = expandLayoutNamesWithDependencies(setOf("entry"), resDir)

        assertThat(expanded).containsExactly("entry", "toolbar", "title")
    }

    @Test
    fun `expands include dependencies declared only in qualified layout variants`() {
        val resDir = tempDir.newFolder("qualified-res")
        val layoutDir = resDir.resolve("layout").apply { mkdirs() }
        val landLayoutDir = resDir.resolve("layout-land").apply { mkdirs() }
        layoutDir.resolve("entry.xml").writeText("<LinearLayout />")
        landLayoutDir.resolve("entry.xml").writeText(
            """
            <LinearLayout>
                <include layout="@layout/toolbar" />
            </LinearLayout>
            """.trimIndent()
        )
        layoutDir.resolve("toolbar.xml").writeText("<TextView />")

        val expanded = expandLayoutNamesWithDependencies(setOf("entry"), resDir)

        assertThat(expanded).containsExactly("entry", "toolbar")
    }

    @Test
    fun `qualified include resolves sibling variant before default layout`() {
        val resDir = tempDir.newFolder("variant-res")
        val defaultLayoutDir = resDir.resolve("layout").apply { mkdirs() }
        val landLayoutDir = resDir.resolve("layout-land").apply { mkdirs() }
        landLayoutDir.resolve("entry.xml").writeText(
            """
            <LinearLayout>
                <include layout="@layout/toolbar" />
            </LinearLayout>
            """.trimIndent()
        )
        defaultLayoutDir.resolve("toolbar.xml").writeText("<TextView android:text=\"default\" />")
        landLayoutDir.resolve("toolbar.xml").writeText("<TextView android:text=\"land\" />")

        val dependencies = LayoutDependencyScanner.scanDependencies(landLayoutDir.resolve("entry.xml"), resDir)

        assertThat(dependencies.map { it.relativeTo(resDir).invariantSeparatorsPath })
            .containsExactly("layout-land/toolbar.xml")
    }
}
