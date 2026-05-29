package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.parser.LayoutNode

/**
 * 节点支持度级别。
 */
enum class SupportLevel {
    /** 完全支持，生成代码 */
    FULL,
    /** 节点本身支持，但部分属性不支持（跳过不支持的属性） */
    PARTIAL,
    /** 不支持，整个子树 fallback 到 LayoutInflater */
    FALLBACK
}

/**
 * 分析结果：每个节点的支持度。
 */
data class AnalyzedNode(
    val node: LayoutNode,
    val supportLevel: SupportLevel,
    val supportedAttributes: Set<String>,
    val unsupportedAttributes: Set<String>,
    val children: List<AnalyzedNode>,
    val indexInParent: Int,
    val parentTagName: String?,
    /** 简单 @{} 表达式属性，由 BindingFacade 在 executePendingBindings() 中处理，不参与静态 codegen */
    val dataBindingAttributes: Set<String> = emptySet()
)
