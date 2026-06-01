package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutDependencyScannerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `scan dependencies follows include and ViewStub references recursively`() {
        val resDir = tempDir.newFolder("res")
        val layoutDir = resDir.resolve("layout")
        layoutDir.mkdirs()
        val host = layoutDir.resolve("host.xml")
        val header = layoutDir.resolve("header.xml")
        val title = layoutDir.resolve("title.xml")
        val lazyPanel = layoutDir.resolve("lazy_panel.xml")
        host.writeText(
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <include layout="@layout/header" />
                <ViewStub android:layout="@layout/lazy_panel" />
            </LinearLayout>
            """.trimIndent()
        )
        header.writeText(
            """
            <FrameLayout>
                <include layout="@layout/title" />
            </FrameLayout>
            """.trimIndent()
        )
        title.writeText("<TextView />")
        lazyPanel.writeText("<FrameLayout />")

        val dependencies = LayoutDependencyScanner.scanDependencies(host, resDir)

        assertThat(dependencies).containsExactly(header, title, lazyPanel)
    }

    @Test
    fun `scan dependencies stops at circular references`() {
        val resDir = tempDir.newFolder("cycle-res")
        val layoutDir = resDir.resolve("layout")
        layoutDir.mkdirs()
        val first = layoutDir.resolve("first.xml")
        val second = layoutDir.resolve("second.xml")
        first.writeText("<include layout=\"@layout/second\" />")
        second.writeText("<include layout=\"@layout/first\" />")

        val dependencies = LayoutDependencyScanner.scanDependencies(first, resDir)

        assertThat(dependencies).containsExactly(second)
    }
}
