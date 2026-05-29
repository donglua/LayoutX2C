package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.registry.DefaultViewRegistry
import com.github.donglua.layoutx2c.registry.ViewAnalysisRegistry

/**
 * 改进的 LayoutAnalyzer，支持：
 * - 更细粒度的 fallback（按属性而不是整棵树）
 * - 区分简单 @{} 表达式（可支持）和复杂表达式（需要 fallback）
 * - 更详细的支持度报告
 */
class LayoutAnalyzerV2(
    private val viewRegistry: ViewAnalysisRegistry = DefaultViewRegistry
) {

    companion object {
        private fun isXmlnsAttribute(name: String) = name.startsWith("xmlns:")
        private fun isToolsAttribute(name: String) = name.startsWith("tools:")
    }

    fun analyze(node: LayoutNode): AnalyzedNode {
        if (hasUnsupportedLayoutParam(node) ||
            hasInvalidRelativeLayoutParam(node)
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
        } || node.children.any(::hasUnsupportedLayoutParam)
    }

    private fun hasInvalidRelativeLayoutParam(node: LayoutNode, parentTagName: String? = null): Boolean {
        return viewRegistry.hasInvalidRelativeLayoutParamForNode(node, parentTagName) ||
            node.children.any { child -> hasInvalidRelativeLayoutParam(child, node.tagName) }
    }

    private fun analyzeNode(node: LayoutNode, parentTagName: String?): AnalyzedNode {
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

        // 4. 分类属性 - 现在支持简单的 @{} 表达式
        val supported = mutableSetOf<String>()
        val unsupported = mutableSetOf<String>()

        for (attrName in node.attributes.keys) {
            when {
                isXmlnsAttribute(attrName) -> { /* 忽略 */ }
                isToolsAttribute(attrName) -> supported.add(attrName)
                viewRegistry.isSupportedAttribute(node, parentTagName, attrName) -> {
                    // 检查属性值中的表达式
                    val attrValue = node.attributes[attrName] ?: ""
                    if (hasComplexDataBindingExpression(attrValue)) {
                        unsupported.add(attrName)
                    } else {
                        supported.add(attrName)
                    }
                }
                else -> unsupported.add(attrName)
            }
        }

        val supportLevel = if (unsupported.isEmpty()) SupportLevel.FULL else SupportLevel.PARTIAL

        // 5. 递归分析子节点
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

    private fun hasComplexDataBindingExpression(attrValue: String): Boolean {
        // 简单的 @{variable} 或 @{variable.property} 是可以支持的
        // 复杂的表达式（包含操作符、方法调用等）需要 fallback
        if (!attrValue.contains("@{") && !attrValue.contains("@={")) {
            return false
        }

        // 提取表达式内容
        val exprStart = attrValue.indexOf("@{")
        val exprEnd = attrValue.lastIndexOf("}")
        if (exprStart < 0 || exprEnd <= exprStart) {
            return true // 格式错误，需要 fallback
        }

        val expr = attrValue.substring(exprStart + 2, exprEnd).trim()

        // 简单变量引用或属性访问是可以支持的
        if (expr.matches(Regex("""[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*"""))) {
            return false // 简单表达式，不需要 fallback
        }

        // 其他情况（操作符、方法调用、三元表达式等）需要 fallback
        return true
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
