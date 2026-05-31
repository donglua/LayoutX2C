package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.LayoutNodeType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Comprehensive tests for include/merge/ViewStub analysis in LayoutAnalyzerV2.
 */
class LayoutAnalyzerIncludeTest {

    private val analyzer = LayoutAnalyzerV2()

    // ─── Include: Simple Resolved (FULL) ────────────────────────────────────────

    @Test
    fun `resolved include with fully supported root returns FULL`() {
        // Simulates: <include layout="@layout/toolbar" /> resolved to a LinearLayout root
        val includedChild = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:text" to "Hello"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val includeNode = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:orientation" to "vertical"
            ),
            children = listOf(includedChild),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("toolbar")
        )
        val root = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        val includeResult = result.children[0]
        assertThat(includeResult.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(includeResult.includedLayoutRef).isEqualTo("toolbar")
        assertThat(includeResult.children).hasSize(1)
        assertThat(includeResult.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
    }

    // ─── Include: With Unsupported Child (PARTIAL) ──────────────────────────────

    @Test
    fun `resolved include with fallback child returns PARTIAL`() {
        // Included root is supported but has a custom view child → include is PARTIAL
        val unsupportedChild = LayoutNode(
            tagName = "com.example.CustomView",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val includeNode = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:orientation" to "vertical"
            ),
            children = listOf(unsupportedChild),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("content_layout")
        )
        val root = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        val includeResult = result.children[0]
        assertThat(includeResult.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(includeResult.includedLayoutRef).isEqualTo("content_layout")
        assertThat(includeResult.children[0].supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    // ─── Include: Unsupported Root View → FALLBACK ──────────────────────────────

    @Test
    fun `resolved include with unsupported root view type returns FALLBACK`() {
        // Include resolved to a custom view that's not in ViewRegistry
        val includeNode = LayoutNode(
            tagName = "com.example.CustomLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content"
            ),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("custom_toolbar")
        )
        val root = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val includeResult = result.children[0]
        assertThat(includeResult.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(includeResult.includedLayoutRef).isEqualTo("custom_toolbar")
    }

    // ─── Include: Unresolved → FALLBACK ─────────────────────────────────────────

    @Test
    fun `unresolved include (tagName still include) returns FALLBACK`() {
        // Include resolution failed — tagName remains "include"
        val includeNode = LayoutNode(
            tagName = "include",
            attributes = mapOf(
                "layout" to "@layout/missing_layout",
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content"
            ),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("missing_layout")
        )
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent",
                "android:orientation" to "vertical"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        val includeResult = result.children[0]
        assertThat(includeResult.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(includeResult.includedLayoutRef).isEqualTo("missing_layout")
    }

    // ─── Include: With style attribute → FALLBACK ───────────────────────────────

    @Test
    fun `resolved include with style attribute returns FALLBACK`() {
        val includeNode = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "style" to "@style/Widget.Toolbar",
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content"
            ),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("styled_toolbar")
        )
        val root = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val includeResult = result.children[0]
        assertThat(includeResult.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(includeResult.includedLayoutRef).isEqualTo("styled_toolbar")
    }

    // ─── Merge: All Children FULL → FULL ────────────────────────────────────────

    @Test
    fun `merge with all FULL children returns FULL`() {
        val child1 = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:text" to "Item 1"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val child2 = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:text" to "Item 2"
            ),
            children = emptyList(),
            indexInParent = 1
        )
        val mergeNode = LayoutNode(
            tagName = "merge",
            attributes = mapOf("xmlns:android" to "http://schemas.android.com/apk/res/android"),
            children = listOf(child1, child2),
            indexInParent = 0,
            nodeType = LayoutNodeType.Merge
        )

        val result = analyzer.analyze(mergeNode)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.isMerge).isTrue()
        assertThat(result.children).hasSize(2)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[1].supportLevel).isEqualTo(SupportLevel.FULL)
    }

    // ─── Merge: With FALLBACK Child → PARTIAL ───────────────────────────────────

    @Test
    fun `merge with fallback child returns PARTIAL`() {
        val supportedChild = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:text" to "Hello"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val fallbackChild = LayoutNode(
            tagName = "com.example.CustomView",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content"
            ),
            children = emptyList(),
            indexInParent = 1
        )
        val mergeNode = LayoutNode(
            tagName = "merge",
            attributes = mapOf("xmlns:android" to "http://schemas.android.com/apk/res/android"),
            children = listOf(supportedChild, fallbackChild),
            indexInParent = 0,
            nodeType = LayoutNodeType.Merge
        )

        val result = analyzer.analyze(mergeNode)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(result.isMerge).isTrue()
        assertThat(result.children).hasSize(2)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[1].supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    // ─── Merge: parentTagName propagation ───────────────────────────────────────

    @Test
    fun `merge children use merge parent as parentTagName`() {
        // Merge is inside a LinearLayout; merge's children should see "LinearLayout" as parent
        val mergeChild = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:layout_weight" to "1"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val mergeNode = LayoutNode(
            tagName = "merge",
            attributes = emptyMap(),
            children = listOf(mergeChild),
            indexInParent = 0,
            nodeType = LayoutNodeType.Merge
        )
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent",
                "android:orientation" to "vertical"
            ),
            children = listOf(mergeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val mergeResult = result.children[0]
        assertThat(mergeResult.isMerge).isTrue()
        assertThat(mergeResult.parentTagName).isEqualTo("LinearLayout")
        // merge children should have parentTagName = "LinearLayout" (merge's parent)
        assertThat(mergeResult.children[0].parentTagName).isEqualTo("LinearLayout")
    }

    // ─── Merge: Empty merge → FULL ─────────────────────────────────────────────

    @Test
    fun `empty merge returns FULL`() {
        val mergeNode = LayoutNode(
            tagName = "merge",
            attributes = mapOf("xmlns:android" to "http://schemas.android.com/apk/res/android"),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.Merge
        )

        val result = analyzer.analyze(mergeNode)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.isMerge).isTrue()
        assertThat(result.children).isEmpty()
    }

    // ─── Merge: With PARTIAL child → PARTIAL ────────────────────────────────────

    @Test
    fun `merge with partial child returns PARTIAL`() {
        val partialChild = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:alpha" to "0.5"  // unsupported attribute → PARTIAL
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val mergeNode = LayoutNode(
            tagName = "merge",
            attributes = emptyMap(),
            children = listOf(partialChild),
            indexInParent = 0,
            nodeType = LayoutNodeType.Merge
        )

        val result = analyzer.analyze(mergeNode)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(result.isMerge).isTrue()
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.PARTIAL)
    }

    // ─── Include Resolved to Merge ──────────────────────────────────────────────

    @Test
    fun `include resolved to merge analyzes as merge`() {
        val mergeChild = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:text" to "Merged content"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        // Include resolves to a merge layout: tagName="merge", nodeType=Include
        val includeNode = LayoutNode(
            tagName = "merge",
            attributes = mapOf("xmlns:android" to "http://schemas.android.com/apk/res/android"),
            children = listOf(mergeChild),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("merge_content")
        )
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent",
                "android:orientation" to "vertical"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val mergeResult = result.children[0]
        assertThat(mergeResult.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(mergeResult.isMerge).isTrue()
        assertThat(mergeResult.includedLayoutRef).isEqualTo("merge_content")
        assertThat(mergeResult.children).hasSize(1)
        assertThat(mergeResult.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        // Children should use "LinearLayout" as parent (merge's parent)
        assertThat(mergeResult.children[0].parentTagName).isEqualTo("LinearLayout")
    }

    // ─── ViewStub: Basic FULL ───────────────────────────────────────────────────

    @Test
    fun `viewstub with supported attributes returns FULL`() {
        val viewStubNode = LayoutNode(
            tagName = "ViewStub",
            attributes = mapOf(
                "android:id" to "@+id/stub_import",
                "android:inflatedId" to "@+id/panel_import",
                "android:layout" to "@layout/progress_overlay",
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:visibility" to "gone"
            ),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.ViewStub("progress_overlay")
        )
        val root = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = listOf(viewStubNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val stubResult = result.children[0]
        assertThat(stubResult.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(stubResult.isViewStub).isTrue()
        assertThat(stubResult.children).isEmpty()
        assertThat(stubResult.unsupportedAttributes).isEmpty()
    }

    // ─── ViewStub: With Unsupported Attribute → PARTIAL ─────────────────────────

    @Test
    fun `viewstub with unsupported attribute returns PARTIAL`() {
        val viewStubNode = LayoutNode(
            tagName = "ViewStub",
            attributes = mapOf(
                "android:id" to "@+id/stub",
                "android:layout" to "@layout/detail",
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:background" to "#FF0000"  // unsupported for ViewStub
            ),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.ViewStub("detail")
        )
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent",
                "android:orientation" to "vertical"
            ),
            children = listOf(viewStubNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val stubResult = result.children[0]
        assertThat(stubResult.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(stubResult.isViewStub).isTrue()
        assertThat(stubResult.unsupportedAttributes).contains("android:background")
    }

    // ─── ViewStub: With Layout Params → FULL ────────────────────────────────────

    @Test
    fun `viewstub with layout margin attributes returns FULL`() {
        val viewStubNode = LayoutNode(
            tagName = "ViewStub",
            attributes = mapOf(
                "android:id" to "@+id/stub",
                "android:layout" to "@layout/overlay",
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:layout_marginTop" to "16dp"
            ),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.ViewStub("overlay")
        )
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent",
                "android:orientation" to "vertical"
            ),
            children = listOf(viewStubNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val stubResult = result.children[0]
        assertThat(stubResult.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(stubResult.isViewStub).isTrue()
    }

    // ─── ViewStub: No Children Analyzed ─────────────────────────────────────────

    @Test
    fun `viewstub never has children in analysis result`() {
        val viewStubNode = LayoutNode(
            tagName = "ViewStub",
            attributes = mapOf(
                "android:layout" to "@layout/lazy_panel",
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.ViewStub("lazy_panel")
        )

        val result = analyzer.analyze(viewStubNode)

        assertThat(result.isViewStub).isTrue()
        assertThat(result.children).isEmpty()
    }

    // ─── Regular Nodes Still Work Unchanged ─────────────────────────────────────

    @Test
    fun `regular nodes are unaffected by include dispatch`() {
        val regularChild = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:text" to "Normal"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent",
                "android:orientation" to "vertical"
            ),
            children = listOf(regularChild),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.includedLayoutRef).isNull()
        assertThat(result.isMerge).isFalse()
        assertThat(result.isViewStub).isFalse()
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
    }

    // ─── Include alongside Regular Nodes ────────────────────────────────────────

    @Test
    fun `include alongside regular nodes does not affect siblings`() {
        val regularChild = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:text" to "Sibling"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val unresolvedInclude = LayoutNode(
            tagName = "include",
            attributes = mapOf(
                "layout" to "@layout/missing",
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content"
            ),
            children = emptyList(),
            indexInParent = 1,
            nodeType = LayoutNodeType.Include("missing")
        )
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent",
                "android:orientation" to "vertical"
            ),
            children = listOf(regularChild, unresolvedInclude),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(2)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[1].supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    // ─── Include with DataBinding Expression Attributes ─────────────────────────

    @Test
    fun `resolved include with simple databinding expression classifies as databinding attribute`() {
        // Resolved include where the included root has a simple @{} expression on a supported
        // attribute. After the analyzer refactor, include nodes share the regular-node path
        // and should classify @{} as dataBindingAttributes (not unsupported).
        val includedChild = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:text" to "@{user.name}"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val includeNode = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:orientation" to "vertical"
            ),
            children = listOf(includedChild),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("user_card")
        )
        val root = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val includeResult = result.children[0]
        assertThat(includeResult.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(includeResult.includedLayoutRef).isEqualTo("user_card")
        // The included child's @{} expression should be in dataBindingAttributes, not unsupported
        val textViewResult = includeResult.children[0]
        assertThat(textViewResult.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(textViewResult.dataBindingAttributes).contains("android:text")
        assertThat(textViewResult.unsupportedAttributes).doesNotContain("android:text")
    }

    @Test
    fun `resolved include root with databinding expression on its own attribute classifies as databinding`() {
        // The included root view itself has a simple @{} expression
        val includeNode = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:text" to "@{viewModel.title}"
            ),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("title_view")
        )
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent",
                "android:orientation" to "vertical"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val includeResult = result.children[0]
        assertThat(includeResult.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(includeResult.includedLayoutRef).isEqualTo("title_view")
        // The include node itself should classify @{} on its own attribute as dataBinding
        assertThat(includeResult.dataBindingAttributes).contains("android:text")
        assertThat(includeResult.unsupportedAttributes).doesNotContain("android:text")
    }

    @Test
    fun `resolved include with two-way databinding expression classifies as twoWay`() {
        val includedChild = LayoutNode(
            tagName = "EditText",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:text" to "@={viewModel.input}"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val includeNode = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:orientation" to "vertical"
            ),
            children = listOf(includedChild),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("input_form")
        )
        val root = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val editTextResult = result.children[0].children[0]
        assertThat(editTextResult.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(editTextResult.dataBindingAttributes).contains("android:text")
        assertThat(editTextResult.twoWayBindingAttributes).contains("android:text")
    }

    @Test
    fun `resolved include with complex databinding expression returns PARTIAL`() {
        // A complex expression like @{user.name + " (" + user.age + ")"} cannot be supported
        // and should be classified as unsupported, leading to PARTIAL.
        val includedChild = LayoutNode(
            tagName = "TextView",
            attributes = mapOf(
                "android:layout_width" to "wrap_content",
                "android:layout_height" to "wrap_content",
                "android:text" to "@{user.name + \" (\" + user.age + \")\"}"
            ),
            children = emptyList(),
            indexInParent = 0
        )
        val includeNode = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "wrap_content",
                "android:orientation" to "vertical"
            ),
            children = listOf(includedChild),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("user_card")
        )
        val root = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = listOf(includeNode),
            indexInParent = 0
        )

        val result = analyzer.analyze(root)

        val includeResult = result.children[0]
        // Complex expression in child → child PARTIAL → include cap to PARTIAL
        assertThat(includeResult.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        val textViewResult = includeResult.children[0]
        assertThat(textViewResult.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(textViewResult.unsupportedAttributes).contains("android:text")
    }
}
