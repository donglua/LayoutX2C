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
}
