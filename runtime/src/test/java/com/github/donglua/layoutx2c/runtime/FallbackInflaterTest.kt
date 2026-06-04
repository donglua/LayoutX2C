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
            .contains("isSafeForPartialInflate(targetTag)")
        assertWithMessage("Non-whitelisted ordinary tags should keep the full-tree fallback path")
            .that(source)
            .contains("if (!isSafeForPartialInflate(targetTag))")
        assertWithMessage("The first phase whitelist should cover common simple platform views")
            .that(source)
            .contains("\"TextView\"")
        assertWithMessage("The first phase whitelist should keep ConstraintLayout out until runtime verification exists")
            .that(source)
            .doesNotContain("\"ConstraintLayout\"")
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
    fun `batch child fallback delegates each path to child partial inflate`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Batch child fallback should be available for sibling fallback nodes")
            .that(source)
            .contains("fun inflateChildren(")
        assertWithMessage("Batch child fallback should avoid full-tree extraction for ordinary view siblings")
            .that(source)
            .contains("return Array(childPaths.size) { index ->")
        assertWithMessage("Batch child fallback should reuse the single-child optimized path")
            .that(source)
            .contains("inflateChild(context, layoutId, childPaths[index], parent)")
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
