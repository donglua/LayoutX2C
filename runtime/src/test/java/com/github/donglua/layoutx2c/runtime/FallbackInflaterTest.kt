package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

class FallbackInflaterTest {

    @Test
    fun `child fallback does not use parser based LayoutInflater inflate`() {
        val moduleDir = listOf(File("."), File("runtime"))
            .map { it.canonicalFile }
            .first { File(it, "src/main/java").isDirectory }
        val source = File(
            moduleDir,
            "src/main/java/com/github/donglua/layoutx2c/runtime/FallbackInflater.kt"
        ).readText()

        assertWithMessage("Partial parser inflation crashes when platform code requires XmlBlock.Parser")
            .that(source)
            .doesNotContain(".inflate(parser,")
    }

    @Test
    fun `batch child fallback uses one full platform inflate`() {
        val moduleDir = listOf(File("."), File("runtime"))
            .map { it.canonicalFile }
            .first { File(it, "src/main/java").isDirectory }
        val source = File(
            moduleDir,
            "src/main/java/com/github/donglua/layoutx2c/runtime/FallbackInflater.kt"
        ).readText()

        assertWithMessage("Batch child fallback should be available for sibling fallback nodes")
            .that(source)
            .contains("fun inflateChildren(")
        assertWithMessage("Batch child fallback should inflate the original layout once")
            .that(source)
            .contains("val fullTree = inflater.inflate(layoutId, parent, false)")
        assertWithMessage("Batch child fallback should locate all children before detaching")
            .that(source)
            .contains("childPaths.map { childPath ->")
    }
}
