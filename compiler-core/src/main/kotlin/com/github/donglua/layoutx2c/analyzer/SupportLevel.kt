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
    val dataBindingAttributes: Set<String> = emptySet(),
    /**
     * 双向绑定 @={} 属性子集（同时也属于 [dataBindingAttributes]）。
     * 由 BindingFacade 额外生成反向监听器（TextWatcher / OnCheckedChangeListener 等）。
     */
    val twoWayBindingAttributes: Set<String> = emptySet(),
    /** 对于 include 节点，被包含的布局资源名（如 "toolbar_common"） */
    val includedLayoutRef: String? = null,
    /** 是否为 merge 虚拟容器节点 */
    val isMerge: Boolean = false,
    /** 是否为 ViewStub 延迟加载节点 */
    val isViewStub: Boolean = false
)
