package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.isConstraintLayout
import com.github.donglua.layoutx2c.registry.ConstraintLayoutRules
import com.github.donglua.layoutx2c.registry.DefaultViewRegistry
import com.github.donglua.layoutx2c.registry.ViewAnalysisRegistry

/**
 * 分析 LayoutTree 中每个节点的支持度。
 * 从外到内逐层分析：如果父节点不支持，子树整体 fallback。
 */
class LayoutAnalyzer(
    private val viewRegistry: ViewAnalysisRegistry = DefaultViewRegistry
) {

    companion object {

        /** xmlns 声明，忽略不计 */
        private fun isXmlnsAttribute(name: String) = name.startsWith("xmlns:")
        private fun isToolsAttribute(name: String) = name.startsWith("tools:")

    }

    fun analyze(node: LayoutNode): AnalyzedNode {
        if (hasUnsupportedLayoutParam(node) ||
            hasInvalidRelativeLayoutParam(node) ||
            hasDataBindingExpression(node)
        ) {
            return markAsFallback(node, parentTagName = null)
        }
        return analyzeNode(node, parentTagName = null)
    }

    private fun hasUnsupportedLayoutParam(node: LayoutNode): Boolean {
        return node.attributes.keys.any { attrName ->
            !isXmlnsAttribute(attrName) &&
                attrName.startsWith("android:layout_") &&
                !viewRegistry.isKnownLayoutAttribute(attrName)
        } || viewRegistry.hasUnsupportedLayoutAttributeValue(node, parentTagName = null) ||
            node.children.any(::hasUnsupportedLayoutParam)
    }

    private fun hasInvalidRelativeLayoutParam(node: LayoutNode, parentTagName: String? = null): Boolean {
        return viewRegistry.hasInvalidRelativeLayoutParamForNode(node, parentTagName) ||
            node.children.any { child -> hasInvalidRelativeLayoutParam(child, node.tagName) }
    }

    private fun hasDataBindingExpression(node: LayoutNode): Boolean {
        return node.attributes.values.any { it.contains("@{") || it.contains("@={") } ||
            node.children.any(::hasDataBindingExpression)
    }

    /**
     * ConstraintLayout 子树级别的 fallback 检测：
     * - 子节点含有复杂约束属性（chain、ratio、percent 等）
     * - 子节点含有不合法的锚点/bias 值
     * - 子节点是 ConstraintLayout helper 标签
     * 任何一项命中，整个 ConstraintLayout 子树 fallback。
     */
    private fun hasInvalidConstraintLayoutSubtree(node: LayoutNode): Boolean {
        if (!node.isConstraintLayout()) return false
        return hasConstraintLayoutIssueInChildren(node)
    }

    private fun hasConstraintLayoutIssueInChildren(parent: LayoutNode): Boolean {
        for (child in parent.children) {
            if (ConstraintLayoutRules.isHelperTag(child.tagName)) return true
            if (ConstraintLayoutRules.hasComplexConstraintAttribute(child)) return true
            if (viewRegistry.hasInvalidConstraintLayoutParamForNode(child, parent.tagName)) return true
            // 递归检查嵌套的 ConstraintLayout 子树中的孙节点不需要，
            // 因为 analyzeNode 会在遇到嵌套 ConstraintLayout 时再次触发此检查。
        }
        return false
    }

    private fun analyzeNode(node: LayoutNode, parentTagName: String?): AnalyzedNode {
        if (hasInvalidConstraintLayoutParamFromParent(node, parentTagName)) {
            return markAsFallback(node, parentTagName)
        }

        // 1. 检查 View 类型是否支持
        if (viewRegistry.viewHandlerFor(node.tagName) == null) {
            return markAsFallback(node, parentTagName)
        }

        // 2. 检查是否有强制 fallback 的属性（style, theme）
        val hasForceFallback = node.attributes.keys.any { it in viewRegistry.forceFallbackAttributes }
        if (hasForceFallback) {
            return markAsFallback(node, parentTagName)
        }

        // 3. 检查是否有 ?attr/ 引用（运行时 theme 依赖）
        val hasThemeRef = node.attributes.values.any { it.startsWith("?") }
        if (hasThemeRef) {
            return markAsFallback(node, parentTagName)
        }

        if (viewRegistry.hasUnsupportedAttributeValue(node, parentTagName)) {
            return markAsFallback(node, parentTagName)
        }

        // 4. ConstraintLayout 子树级别 fallback
        if (parentTagName != null && hasInvalidConstraintLayoutSubtree(node)) {
            return markAsFallback(node, parentTagName)
        }

        // 5. 分类属性
        val supported = mutableSetOf<String>()
        val unsupported = mutableSetOf<String>()

        for (attrName in node.attributes.keys) {
            when {
                isXmlnsAttribute(attrName) -> { /* 忽略 */ }
                isToolsAttribute(attrName) -> supported.add(attrName)
                viewRegistry.isSupportedAttribute(node, parentTagName, attrName) -> supported.add(attrName)
                else -> unsupported.add(attrName)
            }
        }

        val supportLevel = if (unsupported.isEmpty()) SupportLevel.FULL else SupportLevel.PARTIAL

        // 6. 递归分析子节点
        val analyzedChildren = node.children.map { analyzeNode(it, parentTagName = node.tagName) }

        return AnalyzedNode(
            node = node,
            supportLevel = supportLevel,
            supportedAttributes = supported,
            unsupportedAttributes = unsupported,
            children = analyzedChildren,
            indexInParent = node.indexInParent,
            parentTagName = parentTagName
        )
    }

    private fun hasInvalidConstraintLayoutParamFromParent(node: LayoutNode, parentTagName: String?): Boolean {
        if (!ConstraintLayoutRules.parentIsConstraintLayout(parentTagName)) return false
        return ConstraintLayoutRules.isHelperTag(node.tagName) ||
            ConstraintLayoutRules.hasComplexConstraintAttribute(node) ||
            viewRegistry.hasInvalidConstraintLayoutParamForNode(node, parentTagName)
    }

    private fun markAsFallback(node: LayoutNode, parentTagName: String? = null): AnalyzedNode {
        return AnalyzedNode(
            node = node,
            supportLevel = SupportLevel.FALLBACK,
            supportedAttributes = emptySet(),
            unsupportedAttributes = node.attributes.keys,
            children = emptyList(), // fallback 子树不再递归分析
            indexInParent = node.indexInParent,
            parentTagName = parentTagName
        )
    }

}
