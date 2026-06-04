package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.registry.CustomViewAttribute
import com.github.donglua.layoutx2c.registry.CustomViewAttributeKind
import com.github.donglua.layoutx2c.registry.CustomViewDescriptor

internal object CustomViewConfigParser {

    private val customViewPattern = Regex(
        """FastCustomView\s*\(""",
    )
    private val attrPattern = Regex(
        """FastCustomViewAttr\s*\(\s*name\s*=\s*"([^"]+)"\s*,\s*kind\s*=\s*FastCustomViewAttrKind\.([A-Z_]+)\s*\)""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val viewClassPattern = Regex(
        """viewClass\s*=\s*([A-Za-z_][A-Za-z0-9_\.]*)::class"""
    )
    private val importPattern = Regex(
        """(?m)^\s*import\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\s*$"""
    )

    fun extractCustomViews(sourceText: String): List<CustomViewDescriptor> {
        val importedTypes = importedTypes(sourceText)
        val descriptors = mutableListOf<CustomViewDescriptor>()

        for (match in customViewPattern.findAll(sourceText)) {
            val startIndex = sourceText.indexOf('(', match.range.first)
            if (startIndex < 0) continue
            val block = extractParenthesizedBlock(sourceText, startIndex) ?: continue
            val viewClassName = resolveViewClassName(block, importedTypes) ?: continue
            val attributes = attrPattern.findAll(block).mapNotNull { attrMatch ->
                val name = attrMatch.groupValues[1].trim()
                val kind = attrMatch.groupValues[2].toCustomViewAttributeKindOrNull() ?: return@mapNotNull null
                CustomViewAttribute(name = name, kind = kind)
            }.toList()
            descriptors += CustomViewDescriptor(
                viewClassName = viewClassName,
                attributes = attributes
            )
        }

        return descriptors
    }

    private fun importedTypes(sourceText: String): Map<String, String> {
        return importPattern.findAll(sourceText)
            .map { it.groupValues[1] }
            .associateBy({ it.substringAfterLast('.') }, { it })
    }

    private fun resolveViewClassName(block: String, importedTypes: Map<String, String>): String? {
        val raw = viewClassPattern.find(block)?.groupValues?.get(1) ?: return null
        return if ('.' in raw) raw else importedTypes[raw]
    }

    private fun extractParenthesizedBlock(sourceText: String, openParenIndex: Int): String? {
        var depth = 0
        var index = openParenIndex
        while (index < sourceText.length) {
            when (sourceText[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return sourceText.substring(openParenIndex + 1, index)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun String.toCustomViewAttributeKindOrNull(): CustomViewAttributeKind? {
        return runCatching { CustomViewAttributeKind.valueOf(this) }.getOrNull()
    }
}
