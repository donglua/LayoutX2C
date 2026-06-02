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
}
