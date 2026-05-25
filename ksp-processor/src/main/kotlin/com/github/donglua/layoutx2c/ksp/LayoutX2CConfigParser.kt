package com.github.donglua.layoutx2c.ksp

internal object LayoutX2CConfigParser {

    private val layoutsArrayPattern = Regex(
        """\blayouts\b\s*=\s*intArrayOf\s*\((.*?)\)""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val rLayoutPattern = Regex("""(?:\w+\.)*R\.layout\.(\w+)""")

    fun extractLayoutNames(sourceText: String): List<String> {
        val layoutsArrayBody = layoutsArrayPattern.find(sourceText)?.groupValues?.get(1) ?: return emptyList()
        return rLayoutPattern.findAll(layoutsArrayBody)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }
}
