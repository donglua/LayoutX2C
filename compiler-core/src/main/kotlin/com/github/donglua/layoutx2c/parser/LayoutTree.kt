package com.github.donglua.layoutx2c.parser

/**
 * XML layout 解析后的树模型。
 */
data class LayoutTree(
    val root: LayoutNode,
    val fileName: String,
    val rootMetadata: LayoutRootMetadata = LayoutRootMetadata(root.tagName)
)

data class LayoutRootMetadata(
    val originalRootTagName: String,
    val isDataBindingLayout: Boolean = originalRootTagName == "layout",
    val isMalformedDataBindingLayout: Boolean = false,
    val dataBindingVariables: List<DataBindingVariable> = emptyList(),
    val dataBindingImports: List<DataBindingImport> = emptyList()
)

data class DataBindingVariable(
    val name: String,
    val type: String
)

data class DataBindingImport(
    val type: String,
    val alias: String?
)

data class LayoutNode(
    val tagName: String,
    val attributes: Map<String, String>,
    val children: List<LayoutNode>,
    val xmlAttributes: List<XmlAttribute> = attributes.map { (qualifiedName, value) ->
        XmlAttribute(
            qualifiedName = qualifiedName,
            namespaceUri = null,
            name = qualifiedName.substringAfter(':'),
            value = value
        )
    },
    /** 在父节点中的 index */
    val indexInParent: Int = 0,
    /** 节点语义类型：普通 View / include / merge / ViewStub */
    val nodeType: LayoutNodeType = LayoutNodeType.Regular
)

data class XmlAttribute(
    val qualifiedName: String,
    val namespaceUri: String?,
    val name: String,
    val value: String
)
