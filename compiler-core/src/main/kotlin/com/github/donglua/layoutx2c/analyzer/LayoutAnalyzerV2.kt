package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.codegen.BindingAdapterDescriptor
import com.github.donglua.layoutx2c.codegen.DataBindingExpressionParser
import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.LayoutNodeType
import com.github.donglua.layoutx2c.registry.DefaultViewRegistry
import com.github.donglua.layoutx2c.registry.ViewAnalysisRegistry
import com.github.donglua.layoutx2c.resources.NoStyleResolver
import com.github.donglua.layoutx2c.resources.StyleExpansion
import com.github.donglua.layoutx2c.resources.StyleResolver

/**
 * 改进的 LayoutAnalyzer，支持：
 * - 更细粒度的 fallback（按属性而不是整棵树）
 * - 区分简单 @{} 表达式（可支持）和复杂表达式（需要 fallback）
 * - 更详细的支持度报告
 */
class LayoutAnalyzerV2(
    private val viewRegistry: ViewAnalysisRegistry = DefaultViewRegistry,
    private val styleResolver: StyleResolver = NoStyleResolver,
    private val bindingAdapters: List<BindingAdapterDescriptor> = emptyList()
) {

    companion object {
        private fun isXmlnsAttribute(name: String) = name.startsWith("xmlns:")
        private fun isToolsAttribute(name: String) = name.startsWith("tools:")

        /** ViewStub 固有支持的属性（不依赖 ViewRegistry） */
        private val VIEW_STUB_SUPPORTED_ATTRS = setOf(
            "android:id",
            "android:inflatedId",
            "android:layout",
            "android:layout_width",
            "android:layout_height",
            "android:visibility"
        )
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
        } || viewRegistry.hasUnsupportedLayoutAttributeValue(node, parentTagName = null) ||
            node.children.any(::hasUnsupportedLayoutParam)
    }

    private fun hasInvalidRelativeLayoutParam(node: LayoutNode, parentTagName: String? = null): Boolean {
        return viewRegistry.hasInvalidRelativeLayoutParamForNode(node, parentTagName) ||
            node.children.any { child -> hasInvalidRelativeLayoutParam(child, node.tagName) }
    }

    private fun analyzeNode(node: LayoutNode, parentTagName: String?): AnalyzedNode {
        // Dispatch based on nodeType for special tags
        return when (node.nodeType) {
            is LayoutNodeType.Include -> analyzeInclude(node, parentTagName)
            is LayoutNodeType.Merge -> analyzeMerge(node, parentTagName)
            is LayoutNodeType.ViewStub -> analyzeViewStub(node, parentTagName)
            LayoutNodeType.Regular -> analyzeRegularNode(node, parentTagName)
        }
    }

    private fun analyzeRegularNode(node: LayoutNode, parentTagName: String?): AnalyzedNode {
        return analyzeViewNode(node, parentTagName, includedLayoutRef = null)
    }

    // ─── Shared analysis logic for any node that represents a real View ────────

    /**
     * Core analysis for a node that maps to a real View (regular or resolved include).
     * Handles: view type check, force-fallback, ?attr/, attribute classification (including
     * DataBinding expressions), and recursive child analysis.
     */
    private fun analyzeViewNode(
        node: LayoutNode,
        parentTagName: String?,
        includedLayoutRef: String?
    ): AnalyzedNode {
        // 1. 检查 View 类型是否支持
        if (viewRegistry.viewHandlerFor(node.tagName) == null) {
            return markAsFallback(node, parentTagName, includedLayoutRef = includedLayoutRef)
        }

        val styleExpansion = expandStyle(node)
        if (styleExpansion == null) {
            return markAsFallback(node, parentTagName, includedLayoutRef = includedLayoutRef)
        }
        val (effectiveNode, styleOnlyAttributes) = styleExpansion

        // 2. 检查是否需要强制 fallback
        if (shouldForceFallback(effectiveNode, parentTagName)) {
            return markAsFallback(effectiveNode, parentTagName, includedLayoutRef = includedLayoutRef)
        }

        // 3. 分类属性（含 DataBinding 表达式识别）
        val classification = classifyAttributes(effectiveNode, parentTagName)
        if (classification.unsupported.any { it in styleOnlyAttributes }) {
            return markAsFallback(effectiveNode, parentTagName, includedLayoutRef = includedLayoutRef)
        }

        // 4. 递归分析子节点
        val analyzedChildren = effectiveNode.children.map { analyzeNode(it, parentTagName = effectiveNode.tagName) }

        // 5. 确定支持度：自身 PARTIAL 或子节点非 FULL 则 cap 到 PARTIAL
        val ownLevel = if (classification.unsupported.isEmpty()) SupportLevel.FULL else SupportLevel.PARTIAL
        val hasNonFullChild = analyzedChildren.any { it.supportLevel != SupportLevel.FULL }
        val supportLevel = when {
            ownLevel == SupportLevel.PARTIAL -> SupportLevel.PARTIAL
            hasNonFullChild -> SupportLevel.PARTIAL
            else -> SupportLevel.FULL
        }

        return AnalyzedNode(
            node = effectiveNode,
            supportLevel = supportLevel,
            supportedAttributes = classification.supported,
            unsupportedAttributes = classification.unsupported,
            children = analyzedChildren,
            indexInParent = effectiveNode.indexInParent,
            parentTagName = parentTagName,
            dataBindingAttributes = classification.dataBinding,
            twoWayBindingAttributes = classification.twoWayBinding,
            includedLayoutRef = includedLayoutRef
        )
    }

    private fun expandStyle(node: LayoutNode): Pair<LayoutNode, Set<String>>? {
        val styleRef = node.attributes["style"]
        return when (val expansion = styleResolver.expand(styleRef)) {
            StyleExpansion.None -> node to emptySet()
            is StyleExpansion.Unsupported -> null
            is StyleExpansion.Expanded -> {
                val explicitAttributes = node.attributes - "style"
                val effectiveAttributes = expansion.attributes + explicitAttributes
                val styleOnlyAttributes = expansion.attributes.keys - explicitAttributes.keys
                node.copy(attributes = effectiveAttributes) to styleOnlyAttributes
            }
        }
    }

    /**
     * Checks whether a node must unconditionally fallback (style/theme attrs, ?attr/ values,
     * unsupported attribute values).
     */
    private fun shouldForceFallback(node: LayoutNode, parentTagName: String?): Boolean {
        // Force-fallback attributes (style, theme)
        if (node.attributes.keys.any { it in viewRegistry.forceFallbackAttributes }) return true
        // ?attr/ runtime theme references
        if (node.attributes.values.any { it.startsWith("?") }) return true
        // Registry-level unsupported value check
        if (viewRegistry.hasUnsupportedAttributeValue(node, parentTagName)) return true
        return false
    }

    /**
     * Classifies all attributes on a node into supported, unsupported, dataBinding, and twoWayBinding sets.
     */
    private fun classifyAttributes(node: LayoutNode, parentTagName: String?): AttributeClassification {
        val supported = mutableSetOf<String>()
        val unsupported = mutableSetOf<String>()
        val dataBinding = mutableSetOf<String>()
        val twoWayBinding = mutableSetOf<String>()

        for (attrName in node.attributes.keys) {
            val attrValue = node.attributes[attrName] ?: ""
            val isTwoWay = attrValue.startsWith("@={") && attrValue.endsWith("}")
            val isOneWay = !isTwoWay && attrValue.startsWith("@{") && attrValue.endsWith("}")
            when {
                isXmlnsAttribute(attrName) -> { /* 忽略 */ }
                isToolsAttribute(attrName) -> supported.add(attrName)
                (isOneWay || isTwoWay) && isDeclaredBindingAdapterAttribute(attrName) -> {
                    if (isSupportedBindingAdapterExpression(attrValue)) {
                        dataBinding.add(attrName)
                        if (isTwoWay) twoWayBinding.add(attrName)
                    } else {
                        unsupported.add(attrName)
                    }
                }
                viewRegistry.isSupportedAttribute(node, parentTagName, attrName) -> {
                    // 检查属性值中的表达式
                    when {
                        isTwoWay || isOneWay -> {
                            if (hasComplexDataBindingExpression(attrValue)) {
                                unsupported.add(attrName)
                            } else {
                                // 简单表达式 - 由 BindingFacade 在 executePendingBindings()
                                // 中生成正向赋值；双向绑定额外生成反向监听器，不参与 X2C 静态 codegen。
                                dataBinding.add(attrName)
                                if (isTwoWay) twoWayBinding.add(attrName)
                            }
                        }
                        else -> supported.add(attrName)
                    }
                }
                else -> unsupported.add(attrName)
            }
        }

        return AttributeClassification(supported, unsupported, dataBinding, twoWayBinding)
    }

    private fun isDeclaredBindingAdapterAttribute(attrName: String): Boolean {
        return bindingAdapters.any { descriptor -> attrName in descriptor.attrs }
    }

    private fun isSupportedBindingAdapterExpression(attrValue: String): Boolean {
        val expression = DataBindingExpressionParser.extractExpression(attrValue)?.trim() ?: return false
        return DataBindingExpressionParser.expressionToCode(expression) != null
    }

    private data class AttributeClassification(
        val supported: Set<String>,
        val unsupported: Set<String>,
        val dataBinding: Set<String>,
        val twoWayBinding: Set<String>
    )

    /**
     * Analyze an include node.
     * - Unresolved include (tagName still "include") → FALLBACK
     * - Include resolved to merge → delegate to analyzeMerge
     * - Otherwise delegate to [analyzeViewNode] (shared logic with regular nodes)
     */
    private fun analyzeInclude(node: LayoutNode, parentTagName: String?): AnalyzedNode {
        val nodeType = node.nodeType as LayoutNodeType.Include

        if (nodeType.resolutionError != null) {
            return markAsFallback(node, parentTagName, includedLayoutRef = nodeType.layoutRef)
        }

        // Unresolved include: tagName is still "include" (resolution failed)
        if (node.tagName == "include") {
            return markAsFallback(node, parentTagName, includedLayoutRef = nodeType.layoutRef)
        }

        // Include resolved to merge: analyze as merge
        if (node.tagName == "merge") {
            return analyzeMerge(node, parentTagName, includedLayoutRef = nodeType.layoutRef)
        }

        // Resolved include: analyze as a regular view node (reuses full attribute classification
        // including DataBinding expression handling)
        return analyzeViewNode(node, parentTagName, includedLayoutRef = nodeType.layoutRef)
    }

    /**
     * Analyze a merge node (virtual container).
     * Merge does NOT create a View. Its children are injected into the parent ViewGroup.
     * parentTagName for merge's children should be the merge's own parent (not "merge").
     */
    private fun analyzeMerge(
        node: LayoutNode,
        parentTagName: String?,
        includedLayoutRef: String? = null
    ): AnalyzedNode {
        // Analyze children using merge's parent as their parentTagName
        val analyzedChildren = node.children.map { analyzeNode(it, parentTagName = parentTagName) }

        // Determine support level from children
        val supportLevel = when {
            analyzedChildren.isEmpty() -> SupportLevel.FULL
            analyzedChildren.all { it.supportLevel == SupportLevel.FULL } -> SupportLevel.FULL
            else -> SupportLevel.PARTIAL
        }

        return AnalyzedNode(
            node = node,
            supportLevel = supportLevel,
            supportedAttributes = node.attributes.keys.filter { isXmlnsAttribute(it) || isToolsAttribute(it) }.toSet(),
            unsupportedAttributes = emptySet(),
            children = analyzedChildren,
            indexInParent = node.indexInParent,
            parentTagName = parentTagName,
            isMerge = true,
            includedLayoutRef = includedLayoutRef
        )
    }

    /**
     * Analyze a ViewStub node.
     * ViewStub IS a real View. The referenced layout is NOT analyzed at compile time.
     * ViewStub has NO children.
     */
    private fun analyzeViewStub(node: LayoutNode, parentTagName: String?): AnalyzedNode {
        val nodeType = node.nodeType as LayoutNodeType.ViewStub
        if (nodeType.resolutionError != null) {
            return markAsFallback(node, parentTagName)
        }

        val supported = mutableSetOf<String>()
        val unsupported = mutableSetOf<String>()

        for (attrName in node.attributes.keys) {
            when {
                isXmlnsAttribute(attrName) -> { /* ignore */ }
                isToolsAttribute(attrName) -> supported.add(attrName)
                attrName in VIEW_STUB_SUPPORTED_ATTRS -> supported.add(attrName)
                attrName.startsWith("android:layout_") && viewRegistry.isKnownLayoutAttribute(attrName) -> {
                    supported.add(attrName)
                }
                else -> unsupported.add(attrName)
            }
        }

        val supportLevel = if (unsupported.isEmpty()) SupportLevel.FULL else SupportLevel.PARTIAL

        return AnalyzedNode(
            node = node,
            supportLevel = supportLevel,
            supportedAttributes = supported,
            unsupportedAttributes = unsupported,
            children = emptyList(),
            indexInParent = node.indexInParent,
            parentTagName = parentTagName,
            isViewStub = true
        )
    }

    private fun hasComplexDataBindingExpression(attrValue: String): Boolean {
        // 简单的 @{variable} / @{variable.property} / @={variable} / @={variable.property} 可以支持
        // 复杂表达式（操作符、方法调用、三元等）需要 fallback
        val expr = when {
            attrValue.startsWith("@={") && attrValue.endsWith("}") ->
                attrValue.substring(3, attrValue.length - 1).trim()
            attrValue.startsWith("@{") && attrValue.endsWith("}") ->
                attrValue.substring(2, attrValue.length - 1).trim()
            else -> return true // 不是合法的绑定表达式包装，按 fallback 处理
        }

        // 简单变量引用或属性访问
        if (expr.matches(Regex("""[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*"""))) {
            return false
        }

        return true
    }

    private fun markAsFallback(
        node: LayoutNode,
        parentTagName: String? = null,
        includedLayoutRef: String? = null
    ): AnalyzedNode {
        return AnalyzedNode(
            node = node,
            supportLevel = SupportLevel.FALLBACK,
            supportedAttributes = emptySet(),
            unsupportedAttributes = node.attributes.keys,
            children = emptyList(), // fallback 子树不再递归分析
            indexInParent = node.indexInParent,
            parentTagName = parentTagName,
            includedLayoutRef = includedLayoutRef
        )
    }
}
