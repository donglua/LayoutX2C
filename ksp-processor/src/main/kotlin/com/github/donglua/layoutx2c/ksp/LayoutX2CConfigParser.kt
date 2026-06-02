package com.github.donglua.layoutx2c.ksp

internal object LayoutX2CConfigParser {

    private val layoutsArrayPattern = Regex(
        """\blayouts\b\s*=\s*intArrayOf\s*\((.*?)\)""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val rLayoutPattern = Regex("""(?:\w+\.)*R\.layout\.(\w+)""")
    private val rImportPattern = Regex("""(?m)^\s*import\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\.R(?:\s|$)""")
    private val fullyQualifiedRLayoutPattern = Regex(
        """([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+)\.R\.layout\.\w+"""
    )

    fun extractLayoutNames(sourceText: String): List<String> {
        val layoutsArrayBody = layoutsArrayPattern.find(sourceText)?.groupValues?.get(1) ?: return emptyList()
        return rLayoutPattern.findAll(layoutsArrayBody)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    fun extractRPackageName(sourceText: String): String? {
        rImportPattern.find(sourceText)?.let { return it.groupValues[1] }

        val layoutsArrayBody = layoutsArrayPattern.find(sourceText)?.groupValues?.get(1) ?: return null
        return fullyQualifiedRLayoutPattern.find(layoutsArrayBody)?.groupValues?.get(1)
    }
}
