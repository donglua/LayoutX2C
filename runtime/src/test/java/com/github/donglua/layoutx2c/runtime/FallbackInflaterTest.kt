package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

class FallbackInflaterTest {

    @Test
    fun `child fallback uses parser based partial inflate for ordinary view subtree`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Child fallback should avoid inflating the whole original layout for whitelisted ordinary view subtrees")
            .that(source)
            .contains("ReplayCurrentStartTagXmlPullParser(parser)")
        assertWithMessage("Child fallback should seek with the framework XmlResourceParser from Resources.getLayout")
            .that(source)
            .contains("context.resources.getLayout(layoutId)")
    }

    @Test
    fun `child fallback keeps full tree extraction for inflater semantic tags`() {
        val source = fallbackInflaterSource()

        assertWithMessage("merge include and fragment depend on LayoutInflater host semantics")
            .that(source)
            .contains("requiresFullTreeExtraction")
        assertWithMessage("Unsupported partial roots should keep the known-correct full-tree path")
            .that(source)
            .contains("inflateChildFromFullTree(context, layoutId, childPath, parent, layoutName)")
    }

    @Test
    fun `child fallback gates partial inflate behind a safe tag whitelist`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Partial inflate should be limited to an explicit safe tag list")
            .that(source)
            .contains("requiresFullTreeExtraction(targetTag, childPlan)")
        assertWithMessage("Non-whitelisted ordinary tags should keep the full-tree fallback path")
            .that(source)
            .contains("return requiresFullTreeExtraction(tagName) || !isSafeForPartialInflate(tagName)")
        assertWithMessage("The first phase whitelist should cover common simple platform views")
            .that(source)
            .contains("\"TextView\"")
        assertWithMessage("The first phase whitelist should keep ConstraintLayout out until runtime verification exists")
            .that(source)
            .doesNotContain("\"ConstraintLayout\"")
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
    fun `replay parser wrapper replays the current start tag before delegating`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Wrapper should exist so LayoutInflater does not skip the seeked target START_TAG")
            .that(source)
            .contains("private class ReplayCurrentStartTagXmlPullParser")
        assertWithMessage("Wrapper should return START_TAG for the first LayoutInflater next call")
            .that(source)
            .contains("XmlPullParser.START_TAG")
        assertWithMessage("Wrapper should delegate subsequent next calls to the platform parser")
            .that(source)
            .contains("return delegate.next()")
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
