package com.github.donglua.layoutx2c.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XmlLayoutParserTest {

    private val parser = XmlLayoutParser()

    @Test
    fun `parses simple linear layout with text view`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Hello" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "simple_layout")

        assertThat(tree.fileName).isEqualTo("simple_layout")
        assertThat(tree.root.tagName).isEqualTo("LinearLayout")
        assertThat(tree.root.attributes["android:orientation"]).isEqualTo("vertical")
        assertThat(tree.root.children).hasSize(1)

        val textView = tree.root.children[0]
        assertThat(textView.tagName).isEqualTo("TextView")
        assertThat(textView.attributes["android:id"]).isEqualTo("@+id/title")
        assertThat(textView.attributes["android:text"]).isEqualTo("Hello")
        assertThat(textView.indexInParent).isEqualTo(0)
    }

    @Test
    fun `parses nested layout with multiple children`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <View android:layout_width="match_parent" android:layout_height="match_parent" />
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal">
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" />
                </LinearLayout>
            </FrameLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "nested")

        assertThat(tree.root.children).hasSize(2)
        assertThat(tree.root.children[0].tagName).isEqualTo("View")
        assertThat(tree.root.children[0].indexInParent).isEqualTo(0)
        assertThat(tree.root.children[1].tagName).isEqualTo("LinearLayout")
        assertThat(tree.root.children[1].indexInParent).isEqualTo(1)
        assertThat(tree.root.children[1].children).hasSize(1)
    }
}
