package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.codegen.BindingAdapterDescriptor

internal object BindingAdapterConfigParser {

    private val bindingAdapterPattern = Regex("""FastBindingAdapter\s*\(""")
    private val quotedStringPattern = Regex(""""([^"]+)"""")
    private val methodClassPattern = Regex(
        """methodClass\s*=\s*([A-Za-z_][A-Za-z0-9_\.]*)::class"""
    )
    private val methodNamePattern = Regex("""methodName\s*=\s*"([^"]+)"""")
    private val requireAllPattern = Regex("""requireAll\s*=\s*(true|false)""")
    private val importPattern = Regex(
        """(?m)^\s*import\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\s*$"""
    )

    fun extractBindingAdapters(sourceText: String): List<BindingAdapterDescriptor> {
        val importedTypes = importedTypes(sourceText)
        val descriptors = mutableListOf<BindingAdapterDescriptor>()

        for (match in bindingAdapterPattern.findAll(sourceText)) {
            val startIndex = sourceText.indexOf('(', match.range.first)
            if (startIndex < 0) continue
            val block = extractParenthesizedBlock(sourceText, startIndex) ?: continue
            val attrs = extractAttrs(block)
            val methodClassName = resolveMethodClassName(block, importedTypes) ?: continue
            val methodName = methodNamePattern.find(block)?.groupValues?.get(1)?.trim() ?: continue
            val requireAll = requireAllPattern.find(block)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: true
            if (attrs.isEmpty()) continue
            descriptors += BindingAdapterDescriptor(
                attrs = attrs,
                methodClassName = methodClassName,
                methodName = methodName,
                requireAll = requireAll
            )
        }

        return descriptors
    }

    private fun importedTypes(sourceText: String): Map<String, String> {
        return importPattern.findAll(sourceText)
            .map { it.groupValues[1] }
            .associateBy({ it.substringAfterLast('.') }, { it })
    }

    private fun resolveMethodClassName(block: String, importedTypes: Map<String, String>): String? {
        val raw = methodClassPattern.find(block)?.groupValues?.get(1) ?: return null
        return if ('.' in raw) raw else importedTypes[raw]
    }

    private fun extractAttrs(block: String): List<String> {
        val attrStart = block.indexOf("attrs")
        if (attrStart < 0) return emptyList()
        val equalsIndex = block.indexOf('=', attrStart)
        if (equalsIndex < 0) return emptyList()
        val valueStart = block.indexOfFirstNonWhitespace(equalsIndex + 1)
        if (valueStart < 0) return emptyList()

        val raw = when (block[valueStart]) {
            '[' -> extractDelimitedBlock(block, valueStart, '[', ']')
            '"' -> extractQuotedString(block, valueStart)
            else -> {
                if (block.startsWith("arrayOf", valueStart)) {
                    val openParen = block.indexOf('(', valueStart)
                    if (openParen >= 0) extractDelimitedBlock(block, openParen, '(', ')') else null
                } else {
                    null
                }
            }
        } ?: return emptyList()

        return quotedStringPattern.findAll(raw)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun extractParenthesizedBlock(sourceText: String, openParenIndex: Int): String? {
        return extractDelimitedBlock(sourceText, openParenIndex, '(', ')')
            ?.drop(1)
            ?.dropLast(1)
    }

    private fun extractDelimitedBlock(sourceText: String, openIndex: Int, open: Char, close: Char): String? {
        var depth = 0
        var index = openIndex
        var inString = false
        while (index < sourceText.length) {
            val char = sourceText[index]
            if (char == '"' && (index == 0 || sourceText[index - 1] != '\\')) {
                inString = !inString
            }
            if (!inString) {
                when (char) {
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) {
                            return sourceText.substring(openIndex, index + 1)
                        }
                    }
                }
            }
            index++
        }
        return null
    }

    private fun extractQuotedString(sourceText: String, startIndex: Int): String? {
        var index = startIndex + 1
        while (index < sourceText.length) {
            if (sourceText[index] == '"' && sourceText[index - 1] != '\\') {
                return sourceText.substring(startIndex, index + 1)
            }
            index++
        }
        return null
    }

    private fun String.indexOfFirstNonWhitespace(startIndex: Int): Int {
        for (index in startIndex until length) {
            if (!this[index].isWhitespace()) return index
        }
        return -1
    }
}
