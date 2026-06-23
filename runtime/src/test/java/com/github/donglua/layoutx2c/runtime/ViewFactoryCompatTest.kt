package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

class ViewFactoryCompatTest {

    @Test
    fun `runtime exposes nullable attribute set view factory hook`() {
        val source = runtimeSource("ViewFactoryCompat.kt")

        assertWithMessage("ViewFactory interface should be public runtime API")
            .that(source)
            .contains("interface ViewFactory")
        assertWithMessage("Factory should receive XML tag name for JZViewInflater style adapters")
            .that(source)
            .contains("name: String")
        assertWithMessage("Generated code may pass synthetic attrs or null when no XML attrs are available")
            .that(source)
            .contains("attrs: AttributeSet?")
        assertWithMessage("Registry should allow tests and theme switches to restore default behavior")
            .that(source)
            .contains("fun reset()")
        assertWithMessage("Compat path should use the default creator when no custom view is returned")
            .that(source)
            .contains("defaultCreator")
    }

    private fun runtimeSource(fileName: String): String {
        val moduleDir = listOf(File("."), File("runtime"))
            .map { it.canonicalFile }
            .first { File(it, "src/main/java").isDirectory }
        return File(
            moduleDir,
            "src/main/java/com/github/donglua/layoutx2c/runtime/$fileName"
        ).readText()
    }
}
