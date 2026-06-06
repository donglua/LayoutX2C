package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

class FallbackInflaterTest {

    @Test
    fun `child fallback avoids parser replay partial inflate`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Fallback child inflate must keep platform XmlBlock.Parser semantics")
            .that(source)
            .doesNotContain("ReplayCurrentStartTagXmlPullParser")
        assertWithMessage("Child fallback should seek with the framework XmlResourceParser from Resources.getLayout")
            .that(source)
            .contains("context.resources.getLayout(layoutId)")
        assertWithMessage("Single child fallback should use the known-correct full tree extraction path")
            .that(source)
            .contains("return inflateChildFromFullTree(context, layoutId, childPath, parent, layoutName)")
    }

    @Test
    fun `child fallback keeps full tree extraction`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Unsupported partial roots should keep the known-correct full-tree path")
            .that(source)
            .contains("inflateChildFromFullTree(context, layoutId, childPath, parent, layoutName)")
    }

    @Test
    fun `child fallback does not expose partial inflate controls`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Parser replay partial inflate cannot preserve XmlBlock.Parser identity")
            .that(source)
            .doesNotContain("SAFE_PARTIAL_INFLATE_TAGS")
        assertWithMessage("Fallback child plans should not expose a no-op partial inflate switch")
            .that(source)
            .doesNotContain("partialInflateAllowed")
    }

    @Test
    fun `child fallback exposes compile-time fallback plans`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Generated code should be able to pass precomputed fallback metadata")
            .that(source)
            .contains("class FallbackChildPlan")
        assertWithMessage("Single-child fallback should accept a compile-time fallback plan")
            .that(source)
            .contains("childPlan: FallbackChildPlan")
        assertWithMessage("Batched child fallback should accept compile-time fallback plans")
            .that(source)
            .contains("childPlans: Array<FallbackChildPlan>")
    }

    @Test
    fun `replay parser wrapper is not used`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Do not pass a replay wrapper to LayoutInflater.inflate(XmlPullParser, ...)")
            .that(source)
            .doesNotContain("ReplayCurrentStartTagXmlPullParser")
        assertWithMessage("Do not delegate parser events through a wrapper for fallback children")
            .that(source)
            .doesNotContain("return delegate.next()")
    }

    @Test
    fun `batch child fallback uses a single parser pass instead of delegating each child`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Batch child fallback should be available for sibling fallback nodes")
            .that(source)
            .contains("fun inflateChildren(")
        assertWithMessage("Batch child fallback should share parser traversal across sibling paths")
            .that(source)
            .contains("inflateChildrenWithSingleParser")
        assertWithMessage("Batch child fallback should not reopen and reseek the XML for each child")
            .that(source)
            .doesNotContain("inflateChild(context, layoutId, childPaths[index], parent)")
    }

    @Test
    fun `batch child fallback extracts unsupported children from one full tree`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Unsupported batched children should share one full-tree inflate")
            .that(source)
            .contains("inflateChildrenFromFullTree")
        assertWithMessage("Single-child full-tree extraction should still be retained for the public child API")
            .that(source)
            .contains("inflateChildFromFullTree(context, layoutId, childPath, parent, layoutName)")
    }

    private fun fallbackInflaterSource(): String {
        val moduleDir = listOf(File("."), File("runtime"))
            .map { it.canonicalFile }
            .first { File(it, "src/main/java").isDirectory }
        return File(
            moduleDir,
            "src/main/java/com/github/donglua/layoutx2c/runtime/FallbackInflater.kt"
        ).readText()
    }
}
