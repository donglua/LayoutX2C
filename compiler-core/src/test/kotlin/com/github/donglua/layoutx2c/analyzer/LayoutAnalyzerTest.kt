package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutAnalyzerTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()

    @Test
    fun `fully supported layout returns FULL`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Hello" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
    }

    @Test
    fun `unsupported view type returns FALLBACK`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <androidx.recyclerview.widget.RecyclerView
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `style attribute forces FALLBACK`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                style="@style/TextAppearance.Title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `theme reference in value forces FALLBACK`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="?attr/colorPrimary" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `unsupported attribute returns PARTIAL`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:background="#FF0000">
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(result.unsupportedAttributes).contains("android:background")
    }
}
