package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

class FallbackInflaterTest {

    @Test
    fun `child fallback uses parser based partial inflate for ordinary view subtree`() {
        val source = fallbackInflaterSource()

        assertWithMessage("Child fallback should avoid inflating the whole original layout for ordinary view subtrees")
            .that(source)
            .contains(".inflate(parser, parent, false)")
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
