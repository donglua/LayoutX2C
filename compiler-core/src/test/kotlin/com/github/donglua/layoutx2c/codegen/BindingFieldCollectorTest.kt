package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.LayoutNodeType
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
        assertThat(fields.map { it.isRoot }).containsExactly(true, false, false).inOrder()
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
    fun `maps remaining platform appcompat and fallback view field classes`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <RelativeLayout android:id="@+id/relative" android:layout_width="match_parent" android:layout_height="wrap_content" />
                <ScrollView android:id="@+id/scroll" android:layout_width="match_parent" android:layout_height="wrap_content" />
                <HorizontalScrollView android:id="@+id/h_scroll" android:layout_width="match_parent" android:layout_height="wrap_content" />
                <Button android:id="@+id/button" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <EditText android:id="@+id/edit" android:layout_width="match_parent" android:layout_height="wrap_content" />
                <Switch android:id="@+id/switch_view" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <RadioButton android:id="@+id/radio" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <ToggleButton android:id="@+id/toggle" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <View android:id="@+id/plain_view" android:layout_width="1dp" android:layout_height="1dp" />
                <ViewStub android:id="@+id/stub" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <androidx.appcompat.widget.AppCompatTextView android:id="@+id/app_text" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <androidx.appcompat.widget.AppCompatButton android:id="@+id/app_button" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <androidx.appcompat.widget.AppCompatEditText android:id="@+id/app_edit" android:layout_width="match_parent" android:layout_height="wrap_content" />
                <androidx.appcompat.widget.AppCompatCheckBox android:id="@+id/app_check" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <androidx.appcompat.widget.AppCompatRadioButton android:id="@+id/app_radio" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <androidx.appcompat.widget.AppCompatImageView android:id="@+id/app_image" android:layout_width="32dp" android:layout_height="32dp" />
                <androidx.recyclerview.widget.RecyclerView android:id="@+id/list" android:layout_width="match_parent" android:layout_height="wrap_content" />
                <view class="" android:id="@+id/blank_class" android:layout_width="1dp" android:layout_height="1dp" />
                <view class="com.example.1Bad" android:id="@+id/bad_class" android:layout_width="1dp" android:layout_height="1dp" />
                <custom android:id="@+id/local_custom" android:layout_width="1dp" android:layout_height="1dp" />
            </LinearLayout>
        """.trimIndent()

        val result = collector.collect(parser.parse(xml, "remaining_field_classes").root)

        assertThat(result).isInstanceOf(BindingFieldResult.Success::class.java)
        val fields = (result as BindingFieldResult.Success).fields
        assertThat(fields.map { it.viewClass.toString() }).containsExactly(
            "android.widget.RelativeLayout",
            "android.widget.ScrollView",
            "android.widget.HorizontalScrollView",
            "android.widget.Button",
            "android.widget.EditText",
            "android.widget.Switch",
            "android.widget.RadioButton",
            "android.widget.ToggleButton",
            "android.view.View",
            "android.view.ViewStub",
            "androidx.appcompat.widget.AppCompatTextView",
            "androidx.appcompat.widget.AppCompatButton",
            "androidx.appcompat.widget.AppCompatEditText",
            "androidx.appcompat.widget.AppCompatCheckBox",
            "androidx.appcompat.widget.AppCompatRadioButton",
            "androidx.appcompat.widget.AppCompatImageView",
            "androidx.recyclerview.widget.RecyclerView",
            "android.view.View",
            "android.view.View",
            "android.view.View"
        ).inOrder()
    }

    @Test
    fun `collects nested binding include fields without walking include children`() {
        val skippedIncludeChild = LayoutNode(
            tagName = "TextView",
            attributes = mapOf("android:id" to "@+id/skipped_child"),
            children = emptyList()
        )
        val nestedBindingInclude = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf("android:id" to "@+id/user_card"),
            children = listOf(skippedIncludeChild),
            nodeType = LayoutNodeType.Include(
                layoutRef = "user_card",
                isDataBindingLayout = true
            )
        )
        val mergeInclude = LayoutNode(
            tagName = "merge",
            attributes = mapOf("android:id" to "@+id/merge_panel"),
            children = listOf(skippedIncludeChild),
            nodeType = LayoutNodeType.Include(
                layoutRef = "merge_panel",
                isDataBindingLayout = true
            )
        )
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = emptyMap(),
            children = listOf(nestedBindingInclude, mergeInclude)
        )

        val result = BindingFieldCollector("com.example.databinding").collect(root)

        assertThat(result).isInstanceOf(BindingFieldResult.Success::class.java)
        val fields = (result as BindingFieldResult.Success).fields
        assertThat(fields.map { it.idName }).containsExactly("user_card", "merge_panel").inOrder()
        assertThat(fields[0].isNestedBinding).isTrue()
        assertThat(fields[0].nestedBindingLayoutName).isEqualTo("user_card")
        assertThat(fields[0].viewClass.toString()).isEqualTo("com.example.databinding.UserCardBinding")
        assertThat(fields[1].isNestedBinding).isFalse()
        assertThat(fields[1].nestedBindingLayoutName).isNull()
        assertThat(fields[1].viewClass.toString()).isEqualTo("android.view.View")
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
