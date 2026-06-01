package com.github.donglua.layoutx2c.report

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.analyzer.SupportLevel
import com.github.donglua.layoutx2c.codegen.BindingFacadeEligibility
import com.github.donglua.layoutx2c.codegen.BindingFacadeStatus
import com.github.donglua.layoutx2c.parser.LayoutNodeType
import com.github.donglua.layoutx2c.parser.LayoutTree

/**
 * 生成支持度报告，输出 JSON 格式。
 * 告诉用户哪些节点被生成了代码，哪些 fallback 了。
 */
class SupportReportGenerator {

    data class ReportEntry(
        val tagName: String,
        val indexPath: String,
        val supportLevel: SupportLevel,
        val unsupportedAttributes: Set<String>,
        val reason: String?
    )

    fun generate(analyzedRoot: AnalyzedNode, layoutName: String, tree: LayoutTree? = null): String {
        val entries = mutableListOf<ReportEntry>()
        collectEntries(analyzedRoot, "", entries)
        val bindingFacadeStatus = tree
            ?.let { BindingFacadeEligibility.evaluate(it, analyzedRoot).status }
            ?: BindingFacadeStatus.NOT_DATA_BINDING_LAYOUT

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"layout\": \"$layoutName\",")
        sb.appendLine("  \"bindingFacade\": \"${bindingFacadeStatus.name}\",")
        sb.appendLine("  \"nodes\": [")

        entries.forEachIndexed { index, entry ->
            sb.appendLine("    {")
            sb.appendLine("      \"tag\": \"${entry.tagName}\",")
            sb.appendLine("      \"path\": \"${entry.indexPath}\",")
            sb.appendLine("      \"support\": \"${entry.supportLevel.name}\",")
            sb.appendLine("      \"reason\": ${entry.reason?.let { "\"$it\"" } ?: "null"},")
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
                unsupportedAttributes = node.unsupportedAttributes,
                reason = fallbackReason(node)
            )
        )

        for (child in node.children) {
            collectEntries(child, currentPath, entries)
        }
    }

    private fun fallbackReason(node: AnalyzedNode): String? {
        if (node.supportLevel != SupportLevel.FALLBACK) return null

        val nodeType = node.node.nodeType
        when (nodeType) {
            is LayoutNodeType.Include -> nodeType.resolutionError?.let { return it }
            is LayoutNodeType.ViewStub -> nodeType.resolutionError?.let { return it }
            else -> Unit
        }

        return when (node.node.tagName) {
            "layout" -> "DATA_BINDING_WRAPPER"
            else -> "UNSUPPORTED_VIEW_OR_SEMANTICS"
        }
    }
}
