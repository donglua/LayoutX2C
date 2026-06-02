package com.github.donglua.layoutx2c.ksp

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutX2CConfigParserTest {

    @Test
    fun extractsLayoutNamesFromConfigObject() {
        val source = """
            package com.example

            import com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig

            @FastLayoutConfig
            object LayoutX2CConfig {
                val layouts = intArrayOf(
                    R.layout.activity_main,
                    com.example.R.layout.fragment_home,
                )

                val previewLayout = R.layout.not_configured
            }
        """.trimIndent()

        val layouts = LayoutX2CConfigParser.extractLayoutNames(source)

        assertEquals(listOf("activity_main", "fragment_home"), layouts)
    }

    @Test
    fun extractsImportedRPackageNameFromConfigObject() {
        val source = """
            package com.example.legacy.home

            import com.example.feature.home.R
            import com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig

            @FastLayoutConfig
            object LayoutX2CConfig {
                val layouts = intArrayOf(
                    R.layout.feature_home_entry,
                )
            }
        """.trimIndent()

        val rPackageName = LayoutX2CConfigParser.extractRPackageName(source)

        assertEquals("com.example.feature.home", rPackageName)
    }
}
