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
        assertThat(tree.rootMetadata.isDataBindingLayout).isFalse()
        assertThat(tree.rootMetadata.isMalformedDataBindingLayout).isFalse()
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

    @Test
    fun `unwraps data binding layout root to first view child`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable
                        name="title"
                        type="java.lang.String" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "data_binding_layout")

        assertThat(tree.rootMetadata.originalRootTagName).isEqualTo("layout")
        assertThat(tree.rootMetadata.isDataBindingLayout).isTrue()
        assertThat(tree.rootMetadata.isMalformedDataBindingLayout).isFalse()
        assertThat(tree.rootMetadata.dataBindingVariables.map { it.name }).containsExactly("title")
        assertThat(tree.rootMetadata.dataBindingVariables.map { it.type }).containsExactly("java.lang.String")
        assertThat(tree.root.tagName).isEqualTo("LinearLayout")
        assertThat(tree.root.indexInParent).isEqualTo(0)
        assertThat(tree.root.children).hasSize(1)
        assertThat(tree.root.children[0].tagName).isEqualTo("TextView")
    }

    @Test
    fun `keeps malformed data binding layout root and marks metadata`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable
                        name="title"
                        type="java.lang.String" />
                </data>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "malformed_data_binding_layout")

        assertThat(tree.rootMetadata.originalRootTagName).isEqualTo("layout")
        assertThat(tree.rootMetadata.isDataBindingLayout).isTrue()
        assertThat(tree.rootMetadata.isMalformedDataBindingLayout).isTrue()
        assertThat(tree.rootMetadata.dataBindingVariables.map { it.name }).containsExactly("title")
        assertThat(tree.rootMetadata.dataBindingVariables.map { it.type }).containsExactly("java.lang.String")
        assertThat(tree.root.tagName).isEqualTo("layout")
    }

    @Test
    fun `ignores malformed data binding variables`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable
                        name="title"
                        type="java.lang.String" />
                    <variable
                        name=""
                        type="java.lang.String" />
                    <variable
                        type="java.lang.Integer" />
                    <variable
                        name="missingType" />
                </data>
                <FrameLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "data_binding_variables")

        assertThat(tree.rootMetadata.dataBindingVariables.map { it.name }).containsExactly("title")
        assertThat(tree.rootMetadata.dataBindingVariables.map { it.type }).containsExactly("java.lang.String")
    }

    // --- include / merge / ViewStub node type detection ---

    @Test
    fun `parses include tag with layout ref as LayoutNodeType Include`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include
                    layout="@layout/toolbar_common"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "include_layout")

        assertThat(tree.root.tagName).isEqualTo("LinearLayout")
        assertThat(tree.root.nodeType).isEqualTo(LayoutNodeType.Regular)
        assertThat(tree.root.children).hasSize(1)

        val includeNode = tree.root.children[0]
        assertThat(includeNode.tagName).isEqualTo("include")
        assertThat(includeNode.nodeType).isEqualTo(LayoutNodeType.Include("toolbar_common"))
        assertThat(includeNode.attributes["android:layout_width"]).isEqualTo("match_parent")
    }

    @Test
    fun `include without layout attribute is treated as Regular`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include android:id="@+id/no_layout" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "include_no_layout")

        val includeNode = tree.root.children[0]
        assertThat(includeNode.tagName).isEqualTo("include")
        assertThat(includeNode.nodeType).isEqualTo(LayoutNodeType.Regular)
    }

    @Test
    fun `parses merge tag as LayoutNodeType Merge`() {
        val xml = """
            <merge xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
                <Button
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </merge>
        """.trimIndent()

        val tree = parser.parse(xml, "merge_layout")

        assertThat(tree.root.tagName).isEqualTo("merge")
        assertThat(tree.root.nodeType).isEqualTo(LayoutNodeType.Merge)
        assertThat(tree.root.children).hasSize(2)
        assertThat(tree.root.children[0].tagName).isEqualTo("TextView")
        assertThat(tree.root.children[1].tagName).isEqualTo("Button")
    }

    @Test
    fun `parses ViewStub tag with layout ref as LayoutNodeType ViewStub`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <ViewStub
                    android:id="@+id/stub_import"
                    android:layout="@layout/stub_content"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" />
            </FrameLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "viewstub_layout")

        val viewStubNode = tree.root.children[0]
        assertThat(viewStubNode.tagName).isEqualTo("ViewStub")
        assertThat(viewStubNode.nodeType).isEqualTo(LayoutNodeType.ViewStub("stub_content"))
        assertThat(viewStubNode.attributes["android:id"]).isEqualTo("@+id/stub_import")
    }

    @Test
    fun `ViewStub without layout attribute is treated as Regular`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <ViewStub
                    android:id="@+id/stub_no_layout"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" />
            </FrameLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "viewstub_no_layout")

        val viewStubNode = tree.root.children[0]
        assertThat(viewStubNode.tagName).isEqualTo("ViewStub")
        assertThat(viewStubNode.nodeType).isEqualTo(LayoutNodeType.Regular)
    }

    @Test
    fun `existing nodes default to LayoutNodeType Regular`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "regular_nodes")

        assertThat(tree.root.nodeType).isEqualTo(LayoutNodeType.Regular)
        assertThat(tree.root.children[0].nodeType).isEqualTo(LayoutNodeType.Regular)
    }
}
