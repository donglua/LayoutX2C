package com.github.donglua.layoutx2c.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IncludeResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createLayoutFile(name: String, content: String): File {
        val file = File(tempFolder.root, "$name.xml")
        file.writeText(content)
        return file
    }

    private fun IncludeResult.successTree(): LayoutTree {
        assertThat(this).isInstanceOf(IncludeResult.Success::class.java)
        return (this as IncludeResult.Success).tree
    }

    @Test
    fun `resolves simple include`() {
        createLayoutFile("toolbar", """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="56dp"
                android:orientation="horizontal">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Title" />
            </LinearLayout>
        """.trimIndent())

        val resolver = IncludeResolver(tempFolder.root)
        val result = resolver.resolveInclude("toolbar")
        val tree = result.successTree()

        assertThat(tree.root.tagName).isEqualTo("LinearLayout")
        assertThat(tree.root.children).hasSize(1)
        assertThat(tree.root.children[0].tagName).isEqualTo("TextView")
    }

    @Test
    fun `returns not found for non-existent layout`() {
        val resolver = IncludeResolver(tempFolder.root)
        val result = resolver.resolveInclude("non_existent")

        assertThat(result).isSameInstanceAs(IncludeResult.NotFound)
    }

    @Test
    fun `detects circular reference and preserves error on nested include`() {
        // a.xml includes b.xml which includes a.xml
        createLayoutFile("layout_a", """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include layout="@layout/layout_b" />
            </LinearLayout>
        """.trimIndent())
        createLayoutFile("layout_b", """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include layout="@layout/layout_a" />
            </LinearLayout>
        """.trimIndent())

        val resolver = IncludeResolver(tempFolder.root)
        // Resolving layout_a should succeed at top level
        val result = resolver.resolveInclude("layout_a")
        val tree = result.successTree()

        // The circular include (layout_b -> layout_a) should result in
        // layout_a being treated as unresolved (include tag stays as-is with Include type)
        val layoutB = tree.root.children[0]
        // Since the resolver resolves layout_b, we get its root
        assertThat(layoutB.tagName).isEqualTo("LinearLayout")
        // layout_b's include of layout_a should NOT be resolved (circular) — stays as include tag
        val circularInclude = layoutB.children[0]
        assertThat(circularInclude.tagName).isEqualTo("include")
        val circularIncludeType = circularInclude.nodeType as LayoutNodeType.Include
        assertThat(circularIncludeType.layoutRef).isEqualTo("layout_a")
        assertThat(circularIncludeType.resolutionError).isEqualTo("CIRCULAR_INCLUDE")
    }

    @Test
    fun `returns circular reference when requested layout is already visited`() {
        val resolver = IncludeResolver(tempFolder.root)

        val result = resolver.resolveInclude("self_ref", visitedLayouts = setOf("self_ref"))

        assertThat(result).isSameInstanceAs(IncludeResult.CircularReference)
    }

    @Test
    fun `respects max depth limit`() {
        // Create a chain: depth_0 -> depth_1 -> depth_2 -> ... beyond max
        for (i in 0..12) {
            val includeTag = if (i < 12) """<include layout="@layout/depth_${i + 1}" />""" else ""
            createLayoutFile("depth_$i", """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    $includeTag
                </LinearLayout>
            """.trimIndent())
        }

        // With maxDepth=3, only 3 levels should be resolved
        val resolver = IncludeResolver(tempFolder.root, maxDepth = 3)
        val result = resolver.resolveInclude("depth_0")
        val tree = result.successTree()

        // depth_0 -> includes depth_1 (resolved at depth 1)
        val child1 = tree.root.children[0]
        assertThat(child1.tagName).isEqualTo("LinearLayout") // depth_1 resolved
        // depth_1 -> includes depth_2 (resolved at depth 2)
        val child2 = child1.children[0]
        assertThat(child2.tagName).isEqualTo("LinearLayout") // depth_2 resolved
        // depth_2 -> includes depth_3 (depth=3 >= maxDepth=3, not resolved)
        val child3 = child2.children[0]
        assertThat(child3.tagName).isEqualTo("include") // NOT resolved
        val child3Type = child3.nodeType as LayoutNodeType.Include
        assertThat(child3Type.layoutRef).isEqualTo("depth_3")
        assertThat(child3Type.resolutionError).isEqualTo("INCLUDE_DEPTH_EXCEEDED")
    }

    @Test
    fun `returns depth exceeded at max depth`() {
        val resolver = IncludeResolver(tempFolder.root, maxDepth = 2)

        val result = resolver.resolveInclude("any_layout", depth = 2)

        assertThat(result).isSameInstanceAs(IncludeResult.DepthExceeded)
    }

    @Test
    fun `include merges layout params onto included root`() {
        createLayoutFile("child_view", """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Hello" />
        """.trimIndent())

        createLayoutFile("parent_layout", """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include
                    layout="@layout/child_view"
                    android:layout_width="match_parent"
                    android:layout_height="100dp"
                    android:layout_margin="16dp" />
            </LinearLayout>
        """.trimIndent())

        val resolver = IncludeResolver(tempFolder.root)
        val parser = XmlLayoutParser(includeResolver = resolver)
        val result = parser.parse(
            File(tempFolder.root, "parent_layout.xml")
        )

        val includedNode = result.root.children[0]
        // The included root (TextView) should have its layout params overridden
        assertThat(includedNode.tagName).isEqualTo("TextView")
        val includedNodeType = includedNode.nodeType as LayoutNodeType.Include
        assertThat(includedNodeType.layoutRef).isEqualTo("child_view")
        assertThat(includedNodeType.resolutionError).isNull()
        assertThat(includedNode.attributes["android:layout_width"]).isEqualTo("match_parent")
        assertThat(includedNode.attributes["android:layout_height"]).isEqualTo("100dp")
        assertThat(includedNode.attributes["android:layout_margin"]).isEqualTo("16dp")
        // Original attributes should be preserved
        assertThat(includedNode.attributes["android:text"]).isEqualTo("Hello")
    }

    @Test
    fun `include carries android id from include tag`() {
        createLayoutFile("toolbar_simple", """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="56dp">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>
        """.trimIndent())

        createLayoutFile("host_layout", """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include
                    android:id="@+id/my_toolbar"
                    layout="@layout/toolbar_simple"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" />
            </FrameLayout>
        """.trimIndent())

        val resolver = IncludeResolver(tempFolder.root)
        val parser = XmlLayoutParser(includeResolver = resolver)
        val result = parser.parse(File(tempFolder.root, "host_layout.xml"))

        val includedNode = result.root.children[0]
        assertThat(includedNode.tagName).isEqualTo("LinearLayout")
        assertThat(includedNode.attributes["android:id"]).isEqualTo("@+id/my_toolbar")
    }

    @Test
    fun `self-referencing layout returns null for the circular include`() {
        createLayoutFile("self_ref", """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include layout="@layout/self_ref" />
            </LinearLayout>
        """.trimIndent())

        val resolver = IncludeResolver(tempFolder.root)
        val result = resolver.resolveInclude("self_ref")
        val tree = result.successTree()

        // The self-referencing include should not be resolved
        val child = tree.root.children[0]
        assertThat(child.tagName).isEqualTo("include")
        val childType = child.nodeType as LayoutNodeType.Include
        assertThat(childType.layoutRef).isEqualTo("self_ref")
        assertThat(childType.resolutionError).isEqualTo("CIRCULAR_INCLUDE")
    }

    @Test
    fun `resolves nested includes`() {
        createLayoutFile("leaf", """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Leaf" />
        """.trimIndent())

        createLayoutFile("middle", """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content">
                <include layout="@layout/leaf" />
            </LinearLayout>
        """.trimIndent())

        createLayoutFile("top", """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include layout="@layout/middle" />
            </FrameLayout>
        """.trimIndent())

        val resolver = IncludeResolver(tempFolder.root)
        val parser = XmlLayoutParser(includeResolver = resolver)
        val result = parser.parse(File(tempFolder.root, "top.xml"))

        // top -> middle (resolved)
        val middleNode = result.root.children[0]
        assertThat(middleNode.tagName).isEqualTo("LinearLayout")
        val middleNodeType = middleNode.nodeType as LayoutNodeType.Include
        assertThat(middleNodeType.layoutRef).isEqualTo("middle")
        assertThat(middleNodeType.resolutionError).isNull()

        // middle -> leaf (resolved)
        val leafNode = middleNode.children[0]
        assertThat(leafNode.tagName).isEqualTo("TextView")
        val leafNodeType = leafNode.nodeType as LayoutNodeType.Include
        assertThat(leafNodeType.layoutRef).isEqualTo("leaf")
        assertThat(leafNodeType.resolutionError).isNull()
        assertThat(leafNode.attributes["android:text"]).isEqualTo("Leaf")
    }

    @Test
    fun `parser without resolver keeps include as regular node`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <include layout="@layout/some_layout" />
            </LinearLayout>
        """.trimIndent()

        val parser = XmlLayoutParser() // no resolver
        val tree = parser.parse(xml, "test")

        val includeNode = tree.root.children[0]
        assertThat(includeNode.tagName).isEqualTo("include")
        val nodeType = includeNode.nodeType as LayoutNodeType.Include
        assertThat(nodeType.layoutRef).isEqualTo("some_layout")
        assertThat(nodeType.includeAttributes["layout"]).isEqualTo("@layout/some_layout")
    }
}
