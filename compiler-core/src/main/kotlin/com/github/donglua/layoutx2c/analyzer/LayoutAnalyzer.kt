package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.parser.LayoutNode

/**
 * 分析 LayoutTree 中每个节点的支持度。
 * 从外到内逐层分析：如果父节点不支持，子树整体 fallback。
 */
class LayoutAnalyzer {

    companion object {
        /** MVP 支持的 View 类型 */
        val SUPPORTED_VIEWS = setOf(
            "LinearLayout", "android.widget.LinearLayout",
            "FrameLayout", "android.widget.FrameLayout",
            "TextView", "android.widget.TextView",
            "ImageView", "android.widget.ImageView",
            "androidx.appcompat.widget.AppCompatImageView",
            "View", "android.view.View"
        )

        /** MVP 支持的属性（含 android: 前缀） */
        val SUPPORTED_ATTRIBUTES = setOf(
            "android:layout_width",
            "android:layout_height",
            "android:id",
            "android:orientation",
            "android:visibility",
            "android:text",
            "android:src",
            "android:scaleType",
            "android:tint",
            "app:tint",
            "android:padding",
            "android:paddingLeft",
            "android:paddingRight",
            "android:paddingTop",
            "android:paddingBottom",
            "android:paddingStart",
            "android:paddingEnd",
            "android:layout_margin",
            "android:layout_marginLeft",
            "android:layout_marginRight",
            "android:layout_marginTop",
            "android:layout_marginBottom",
            "android:layout_marginStart",
            "android:layout_marginEnd",
            "android:layout_weight",
            "android:layout_gravity",
            "android:gravity"
        )

        /** 遇到这些属性直接标记整个节点为 FALLBACK */
        val FORCE_FALLBACK_ATTRIBUTES = setOf(
            "style",
            "android:theme"
        )

        private val SUPPORTED_SCALE_TYPES = setOf(
            "center",
            "centerCrop",
            "centerInside",
            "fitCenter",
            "fitEnd",
            "fitStart",
            "fitXY",
            "matrix"
        )

        /** xmlns 声明，忽略不计 */
        private fun isXmlnsAttribute(name: String) = name.startsWith("xmlns:")
    }

    fun analyze(node: LayoutNode): AnalyzedNode {
        if (hasUnsupportedLayoutParam(node)) {
            return markAsFallback(node, parentTagName = null)
        }
        return analyzeNode(node, parentTagName = null)
    }

    private fun hasUnsupportedLayoutParam(node: LayoutNode): Boolean {
        return node.attributes.keys.any { attrName ->
            !isXmlnsAttribute(attrName) &&
                attrName.startsWith("android:layout_") &&
                attrName !in SUPPORTED_ATTRIBUTES
        } || node.children.any(::hasUnsupportedLayoutParam)
    }

    private fun analyzeNode(node: LayoutNode, parentTagName: String?): AnalyzedNode {
        // 1. 检查 View 类型是否支持
        if (node.tagName !in SUPPORTED_VIEWS) {
            return markAsFallback(node, parentTagName)
        }

        // 2. 检查是否有强制 fallback 的属性（style, theme）
        val hasForceFallback = node.attributes.keys.any { it in FORCE_FALLBACK_ATTRIBUTES }
        if (hasForceFallback) {
            return markAsFallback(node, parentTagName)
        }

        // 3. 检查是否有 ?attr/ 引用（运行时 theme 依赖）
        val hasThemeRef = node.attributes.values.any { it.startsWith("?") }
        if (hasThemeRef) {
            return markAsFallback(node, parentTagName)
        }

        if (hasUnsupportedAttributeValue(node)) {
            return markAsFallback(node, parentTagName)
        }

        // 4. 分类属性
        val supported = mutableSetOf<String>()
        val unsupported = mutableSetOf<String>()

        for (attrName in node.attributes.keys) {
            when {
                isXmlnsAttribute(attrName) -> { /* 忽略 */ }
                isSupportedAttribute(node, attrName) -> supported.add(attrName)
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

    private fun isSupportedAttribute(node: LayoutNode, attrName: String): Boolean {
        if (attrName !in SUPPORTED_ATTRIBUTES) return false
        return when (attrName) {
            "android:orientation" -> node.isLinearLayout()
            "android:src",
            "android:scaleType",
            "android:tint",
            "app:tint" -> node.isImageView()
            else -> true
        }
    }

    private fun hasUnsupportedAttributeValue(node: LayoutNode): Boolean {
        val scaleType = node.attributes["android:scaleType"]
        return scaleType != null && node.isImageView() && scaleType !in SUPPORTED_SCALE_TYPES
    }

    private fun LayoutNode.isLinearLayout(): Boolean {
        return tagName == "LinearLayout" || tagName == "android.widget.LinearLayout"
    }

    private fun LayoutNode.isImageView(): Boolean {
        return tagName == "ImageView" ||
            tagName == "android.widget.ImageView" ||
            tagName == "androidx.appcompat.widget.AppCompatImageView"
    }
}
