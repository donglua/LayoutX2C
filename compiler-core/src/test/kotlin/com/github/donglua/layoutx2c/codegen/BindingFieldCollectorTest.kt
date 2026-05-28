package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BindingFieldCollectorTest {

    private val parser = XmlLayoutParser()
    private val collector = BindingFieldCollector()

    @Test
    fun `collects id fields with camel case names and view types`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:id="@+id/root_layout"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/title_text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
                <ImageView
                    android:id="@id/avatar"
                    android:layout_width="32dp"
                    android:layout_height="32dp" />
            </LinearLayout>
        """.trimIndent()

        val result = collector.collect(parser.parse(xml, "fields").root)

        assertThat(result).isInstanceOf(BindingFieldResult.Success::class.java)
        val fields = (result as BindingFieldResult.Success).fields
        assertThat(fields.map { it.propertyName }).containsExactly("rootLayout", "titleText", "avatar").inOrder()
        assertThat(fields.map { it.idName }).containsExactly("root_layout", "title_text", "avatar").inOrder()
        assertThat(fields.map { it.viewClass.simpleName }).containsExactly(
            "LinearLayout",
            "TextView",
            "ImageView"
        ).inOrder()
    }

    @Test
    fun `uses view type for unknown custom view fields`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <com.example.CustomView
                    android:id="@+id/custom_view"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </FrameLayout>
        """.trimIndent()

        val result = collector.collect(parser.parse(xml, "custom").root)

        assertThat(result).isInstanceOf(BindingFieldResult.Success::class.java)
        val fields = (result as BindingFieldResult.Success).fields
        assertThat(fields).hasSize(1)
        assertThat(fields[0].propertyName).isEqualTo("customView")
        assertThat(fields[0].viewClass.simpleName).isEqualTo("View")
    }

    @Test
    fun `rejects duplicate ids`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
                <TextView
                    android:id="@id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>
        """.trimIndent()

        val result = collector.collect(parser.parse(xml, "duplicate").root)

        assertThat(result).isInstanceOf(BindingFieldResult.DuplicateIds::class.java)
        assertThat((result as BindingFieldResult.DuplicateIds).ids).containsExactly("title")
    }
}
