package com.github.donglua.layoutx2c.report

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.analyzer.SupportLevel

/**
 * 生成支持度报告，输出 JSON 格式。
 * 告诉用户哪些节点被生成了代码，哪些 fallback 了。
 */
class SupportReportGenerator {

    data class ReportEntry(
        val tagName: String,
        val indexPath: String,
        val supportLevel: SupportLevel,
        val unsupportedAttributes: Set<String>
    )

    fun generate(analyzedRoot: AnalyzedNode, layoutName: String): String {
        val entries = mutableListOf<ReportEntry>()
        collectEntries(analyzedRoot, "", entries)

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"layout\": \"$layoutName\",")
        sb.appendLine("  \"nodes\": [")

        entries.forEachIndexed { index, entry ->
            sb.appendLine("    {")
            sb.appendLine("      \"tag\": \"${entry.tagName}\",")
            sb.appendLine("      \"path\": \"${entry.indexPath}\",")
            sb.appendLine("      \"support\": \"${entry.supportLevel.name}\",")
            sb.appendLine("      \"unsupportedAttrs\": [${entry.unsupportedAttributes.joinToString(", ") { "\"$it\"" }}]")
            sb.append("    }")
            if (index < entries.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun collectEntries(
        node: AnalyzedNode,
        parentPath: String,
        entries: MutableList<ReportEntry>
    ) {
        val currentPath = if (parentPath.isEmpty()) "[${node.indexInParent}]"
        else "$parentPath[${node.indexInParent}]"

        entries.add(
            ReportEntry(
                tagName = node.node.tagName,
                indexPath = currentPath,
                supportLevel = node.supportLevel,
                unsupportedAttributes = node.unsupportedAttributes
            )
        )

        for (child in node.children) {
            collectEntries(child, currentPath, entries)
        }
    }
}
