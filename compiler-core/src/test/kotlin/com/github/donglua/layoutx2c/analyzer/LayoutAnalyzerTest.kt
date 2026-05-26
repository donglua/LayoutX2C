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
                android:alpha="0.5">
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(result.unsupportedAttributes).contains("android:alpha")
    }

    @Test
    fun `view-specific attributes are unsupported on other view types`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:src="@drawable/ic_demo">
            </FrameLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(result.unsupportedAttributes).containsAtLeast("android:orientation", "android:src")
    }

    @Test
    fun `unknown image scale type returns FALLBACK`() {
        val xml = """
            <ImageView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="32dp"
                android:layout_height="32dp"
                android:scaleType="centercrop" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.unsupportedAttributes).contains("android:scaleType")
    }

    @Test
    fun `supported high frequency attributes return FULL`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@color/title"
                android:textSize="@dimen/title_size"
                android:textStyle="bold|italic"
                android:background="@drawable/title_background" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
    }

    @Test
    fun `unsupported high frequency attribute values return FALLBACK`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textStyle="blod" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.unsupportedAttributes).contains("android:textStyle")
    }
}
