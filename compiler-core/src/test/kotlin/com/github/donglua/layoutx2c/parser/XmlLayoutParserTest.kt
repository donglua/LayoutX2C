package com.github.donglua.layoutx2c.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class XmlLayoutParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
    fun `preserves attribute namespace metadata for synthetic AttributeSet codegen`() {
        val xml = """
            <com.example.PriceView xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:id="@+id/price"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:priceColor="@color/red"
                app:priceFormat="%.2f" />
        """.trimIndent()

        val node = parser.parse(xml, "price_view").root

        assertThat(node.xmlAttributes.map { it.qualifiedName }).containsAtLeast(
            "android:id",
            "app:priceColor",
            "app:priceFormat"
        )
        val androidId = node.xmlAttributes.first { it.qualifiedName == "android:id" }
        assertThat(androidId.namespaceUri).isEqualTo("http://schemas.android.com/apk/res/android")
        assertThat(androidId.name).isEqualTo("id")
        assertThat(androidId.value).isEqualTo("@+id/price")

        val priceColor = node.xmlAttributes.first { it.qualifiedName == "app:priceColor" }
        assertThat(priceColor.namespaceUri).isEqualTo("http://schemas.android.com/apk/res-auto")
        assertThat(priceColor.name).isEqualTo("priceColor")
        assertThat(priceColor.value).isEqualTo("@color/red")
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

    @Test
    fun `parses data binding imports with aliases`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <import type="com.example.shared.R" />
                    <import type="com.example.feature.R" alias="FeatureR" />
                </data>
                <FrameLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "data_binding_imports")

        assertThat(tree.rootMetadata.dataBindingImports.map { it.type }).containsExactly(
            "com.example.shared.R",
            "com.example.feature.R"
        ).inOrder()
        assertThat(tree.rootMetadata.dataBindingImports.map { it.alias }).containsExactly(null, "FeatureR").inOrder()
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
        val nodeType = includeNode.nodeType as LayoutNodeType.Include
        assertThat(nodeType.layoutRef).isEqualTo("toolbar_common")
        assertThat(nodeType.includeAttributes["layout"]).isEqualTo("@layout/toolbar_common")
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
    fun `include resolution failure is preserved on AST node`() {
        val layoutDir = tempFolder.newFolder("layout")
        val hostFile = File(layoutDir, "host.xml")
        hostFile.writeText(
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include layout="@layout/missing_panel" />
            </LinearLayout>
            """.trimIndent()
        )
        val parser = XmlLayoutParser(IncludeResolver(layoutDir))

        val tree = parser.parse(hostFile)

        val includeNode = tree.root.children[0]
        assertThat(includeNode.tagName).isEqualTo("include")
        assertThat(includeNode.nodeType).isEqualTo(
            LayoutNodeType.Include("missing_panel", "INCLUDE_NOT_FOUND")
        )
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
