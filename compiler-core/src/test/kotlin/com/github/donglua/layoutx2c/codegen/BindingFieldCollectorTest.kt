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
    fun `uses concrete type for fully qualified custom view fields`() {
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
        assertThat(fields[0].viewClass.toString()).isEqualTo("com.example.CustomView")
    }

    @Test
    fun `uses concrete type for material and view class fields`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <com.google.android.material.tabs.TabLayout
                    android:id="@+id/tab_layout"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" />
                <view
                    class="androidx.viewpager.widget.ViewPager"
                    android:id="@+id/main_view_pager"
                    android:layout_width="match_parent"
                    android:layout_height="0dp" />
            </LinearLayout>
        """.trimIndent()

        val result = collector.collect(parser.parse(xml, "material").root)

        assertThat(result).isInstanceOf(BindingFieldResult.Success::class.java)
        val fields = (result as BindingFieldResult.Success).fields
        assertThat(fields.map { it.propertyName }).containsExactly("tabLayout", "mainViewPager").inOrder()
        assertThat(fields.map { it.viewClass.toString() }).containsExactly(
            "com.google.android.material.tabs.TabLayout",
            "androidx.viewpager.widget.ViewPager"
        ).inOrder()
    }

    @Test
    fun `uses concrete type for compound button fields`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <CheckBox
                    android:id="@+id/accepted_check"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
                <androidx.appcompat.widget.SwitchCompat
                    android:id="@+id/enabled_switch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>
        """.trimIndent()

        val result = collector.collect(parser.parse(xml, "compound_fields").root)

        assertThat(result).isInstanceOf(BindingFieldResult.Success::class.java)
        val fields = (result as BindingFieldResult.Success).fields
        assertThat(fields.map { it.propertyName }).containsExactly("acceptedCheck", "enabledSwitch").inOrder()
        assertThat(fields.map { it.viewClass.toString() }).containsExactly(
            "android.widget.CheckBox",
            "androidx.appcompat.widget.SwitchCompat"
        ).inOrder()
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
