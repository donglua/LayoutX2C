package com.github.donglua.layoutx2c.parser

/**
 * XML layout 解析后的树模型。
 */
data class LayoutTree(
    val root: LayoutNode,
    val fileName: String
)

data class LayoutNode(
    val tagName: String,
    val attributes: Map<String, String>,
    val children: List<LayoutNode>,
    /** 在父节点中的 index */
    val indexInParent: Int = 0
)
