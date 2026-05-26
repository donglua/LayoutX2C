package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.codegen.ImageScaleTypes
import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.isEditText
import com.github.donglua.layoutx2c.parser.isImageView
import com.github.donglua.layoutx2c.parser.isLinearLayout
import com.github.donglua.layoutx2c.parser.isRecyclerView
import com.github.donglua.layoutx2c.parser.isScrollView
import com.github.donglua.layoutx2c.parser.isTextLikeView

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
            "RelativeLayout", "android.widget.RelativeLayout",
            "androidx.recyclerview.widget.RecyclerView",
            "ScrollView", "android.widget.ScrollView",
            "HorizontalScrollView", "android.widget.HorizontalScrollView",
            "TextView", "android.widget.TextView",
            "Button", "android.widget.Button", "androidx.appcompat.widget.AppCompatButton",
            "EditText", "android.widget.EditText", "androidx.appcompat.widget.AppCompatEditText",
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
            "android:textColor",
            "android:textSize",
            "android:textStyle",
            "android:hint",
            "android:inputType",
            "android:background",
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
            "android:layout_above",
            "android:layout_below",
            "android:layout_toStartOf",
            "android:layout_toEndOf",
            "android:layout_toLeftOf",
            "android:layout_toRightOf",
            "android:layout_alignStart",
            "android:layout_alignEnd",
            "android:layout_alignLeft",
            "android:layout_alignRight",
            "android:layout_alignTop",
            "android:layout_alignBottom",
            "android:layout_alignParentStart",
            "android:layout_alignParentEnd",
            "android:layout_alignParentLeft",
            "android:layout_alignParentRight",
            "android:layout_alignParentTop",
            "android:layout_alignParentBottom",
            "android:layout_centerInParent",
            "android:layout_centerHorizontal",
            "android:layout_centerVertical",
            "android:gravity",
            "android:fillViewport",
            "app:layoutManager",
            "android:enabled",
            "android:clickable",
            "android:focusable",
            "android:elevation",
            "android:minWidth",
            "android:minHeight"
        )

        /** 遇到这些属性直接标记整个节点为 FALLBACK */
        val FORCE_FALLBACK_ATTRIBUTES = setOf(
            "style",
            "android:theme"
        )

        private val SUPPORTED_INPUT_TYPES = setOf(
            "none",
            "text",
            "textCapCharacters",
            "textCapWords",
            "textCapSentences",
            "textAutoCorrect",
            "textAutoComplete",
            "textMultiLine",
            "textNoSuggestions",
            "textEmailAddress",
            "textEmailSubject",
            "textUri",
            "textPersonName",
            "textPassword",
            "textVisiblePassword",
            "textWebEditText",
            "textFilter",
            "textPostalAddress",
            "number",
            "numberSigned",
            "numberDecimal",
            "numberPassword",
            "phone",
            "datetime",
            "date",
            "time"
        )

        private val INPUT_TYPE_CLASS_OR_VARIATION_PARTS = setOf(
            "text",
            "textEmailAddress",
            "textEmailSubject",
            "textUri",
            "textPersonName",
            "textPassword",
            "textVisiblePassword",
            "textWebEditText",
            "textFilter",
            "textPostalAddress",
            "number",
            "numberPassword",
            "phone",
            "datetime",
            "date",
            "time"
        )

        /** xmlns 声明，忽略不计 */
        private fun isXmlnsAttribute(name: String) = name.startsWith("xmlns:")
        private fun isToolsAttribute(name: String) = name.startsWith("tools:")

        private val RELATIVE_LAYOUT_ID_RULE_ATTRIBUTES = setOf(
            "android:layout_above",
            "android:layout_below",
            "android:layout_toStartOf",
            "android:layout_toEndOf",
            "android:layout_toLeftOf",
            "android:layout_toRightOf",
            "android:layout_alignStart",
            "android:layout_alignEnd",
            "android:layout_alignLeft",
            "android:layout_alignRight",
            "android:layout_alignTop",
            "android:layout_alignBottom"
        )

        private val RELATIVE_LAYOUT_BOOLEAN_RULE_ATTRIBUTES = setOf(
            "android:layout_alignParentStart",
            "android:layout_alignParentEnd",
            "android:layout_alignParentLeft",
            "android:layout_alignParentRight",
            "android:layout_alignParentTop",
            "android:layout_alignParentBottom",
            "android:layout_centerInParent",
            "android:layout_centerHorizontal",
            "android:layout_centerVertical"
        )

        private val RELATIVE_LAYOUT_RULE_ATTRIBUTES =
            RELATIVE_LAYOUT_ID_RULE_ATTRIBUTES + RELATIVE_LAYOUT_BOOLEAN_RULE_ATTRIBUTES
    }

    fun analyze(node: LayoutNode): AnalyzedNode {
        if (hasUnsupportedLayoutParam(node) || hasInvalidRelativeLayoutParam(node)) {
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

    private fun hasInvalidRelativeLayoutParam(node: LayoutNode, parentTagName: String? = null): Boolean {
        return hasUnsupportedRelativeLayoutRuleValue(node, parentTagName) ||
            node.children.any { child -> hasInvalidRelativeLayoutParam(child, node.tagName) }
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

        if (hasUnsupportedAttributeValue(node, parentTagName)) {
            return markAsFallback(node, parentTagName)
        }

        // 4. 分类属性
        val supported = mutableSetOf<String>()
        val unsupported = mutableSetOf<String>()

        for (attrName in node.attributes.keys) {
            when {
                isXmlnsAttribute(attrName) -> { /* 忽略 */ }
                isToolsAttribute(attrName) -> supported.add(attrName)
                isSupportedAttribute(node, parentTagName, attrName) -> supported.add(attrName)
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

    private fun isSupportedAttribute(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
        if (attrName !in SUPPORTED_ATTRIBUTES) return false
        return when (attrName) {
            "android:orientation" -> node.isLinearLayout()
            "android:text",
            "android:textColor",
            "android:textSize",
            "android:textStyle" -> node.isTextLikeView()
            "android:hint",
            "android:inputType" -> node.isEditText()
            "android:src",
            "android:scaleType",
            "android:tint",
            "app:tint" -> node.isImageView()
            "android:gravity" -> node.isLinearLayout() || node.isTextLikeView()
            "android:fillViewport" -> node.isScrollView()
            "app:layoutManager" -> node.isRecyclerView()
            in RELATIVE_LAYOUT_RULE_ATTRIBUTES -> isRelativeLayoutTag(parentTagName)
            else -> true
        }
    }

    private fun hasUnsupportedAttributeValue(node: LayoutNode, parentTagName: String?): Boolean {
        val scaleType = node.attributes["android:scaleType"]
        return scaleType != null && node.isImageView() && !ImageScaleTypes.supports(scaleType) ||
            node.isTextLikeView() && node.attributes["android:textSize"]?.let { !isSupportedDimension(it) } == true ||
            node.isTextLikeView() && node.attributes["android:textStyle"]?.let { !isSupportedTextStyle(it) } == true ||
            node.isTextLikeView() && node.attributes["android:textColor"]?.let { !isSupportedColor(it) } == true ||
            node.isEditText() && node.attributes["android:inputType"]?.let { !isSupportedInputType(it) } == true ||
            node.attributes["android:background"]?.let { !isSupportedBackground(it) } == true ||
            node.attributes["android:enabled"]?.let { !isSupportedBoolean(it) } == true ||
            node.attributes["android:clickable"]?.let { !isSupportedBoolean(it) } == true ||
            node.attributes["android:focusable"]?.let { !isSupportedBoolean(it) } == true ||
            node.attributes["android:elevation"]?.let { !isSupportedDimension(it) } == true ||
            node.attributes["android:minWidth"]?.let { !isSupportedDimension(it) } == true ||
            node.attributes["android:minHeight"]?.let { !isSupportedDimension(it) } == true ||
            node.isScrollView() && node.attributes["android:fillViewport"]?.let { !isSupportedBoolean(it) } == true ||
            hasUnsupportedRelativeLayoutRuleValue(node, parentTagName)
    }

    private fun hasUnsupportedRelativeLayoutRuleValue(node: LayoutNode, parentTagName: String?): Boolean {
        for ((attrName, value) in node.attributes) {
            if (attrName in RELATIVE_LAYOUT_ID_RULE_ATTRIBUTES) {
                if (!isRelativeLayoutTag(parentTagName) || !isSupportedIdReference(value)) return true
            }
            if (attrName in RELATIVE_LAYOUT_BOOLEAN_RULE_ATTRIBUTES) {
                if (!isRelativeLayoutTag(parentTagName) || !isSupportedBoolean(value)) return true
            }
        }
        return false
    }

    private fun isSupportedIdReference(value: String): Boolean {
        return value.startsWith("@id/") || value.startsWith("@+id/")
    }

    private fun isRelativeLayoutTag(tagName: String?): Boolean {
        return tagName == "RelativeLayout" || tagName == "android.widget.RelativeLayout"
    }

    private fun isSupportedDimension(value: String): Boolean {
        return value.startsWith("@dimen/") ||
            value.endsWith("dp") ||
            value.endsWith("sp") ||
            value.endsWith("px") ||
            value == "0"
    }

    private fun isSupportedColor(value: String): Boolean {
        return value.startsWith("@color/") || value.startsWith("#")
    }

    private fun isSupportedBackground(value: String): Boolean {
        return value.startsWith("@drawable/") || isSupportedColor(value)
    }

    private fun isSupportedTextStyle(value: String): Boolean {
        return value.split("|").map { it.trim() }.all { it in setOf("normal", "bold", "italic") }
    }

    private fun isSupportedBoolean(value: String): Boolean {
        return value == "true" || value == "false"
    }

    private fun isSupportedInputType(value: String): Boolean {
        val parts = value.split("|").map { it.trim() }
        if (parts.any { it !in SUPPORTED_INPUT_TYPES }) return false
        if ("none" in parts) return parts.size == 1

        val classOrVariationCount = parts.count { it in INPUT_TYPE_CLASS_OR_VARIATION_PARTS }
        return classOrVariationCount <= 1
    }
}
