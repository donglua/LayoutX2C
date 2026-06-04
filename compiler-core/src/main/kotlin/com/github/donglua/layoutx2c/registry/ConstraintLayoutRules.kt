package com.github.donglua.layoutx2c.registry

import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.isConstraintLayout

/**
 * ConstraintLayout 安全子集规则：
 * - 仅当父节点是 ConstraintLayout 时，下列约束属性才被视作合法 layout 参数；
 * - 任何 `app:layout_constraint*` 之外的约束派生属性（chain、ratio、percent、circle、guideline 等）
 *   都会被识别为复杂特性，并触发整个 ConstraintLayout 子树 fallback。
 */
internal object ConstraintLayoutRules {

    /** 安全子集支持的锚点属性。 */
    val anchorAttributes: Set<String> = setOf(
        "app:layout_constraintStart_toStartOf",
        "app:layout_constraintStart_toEndOf",
        "app:layout_constraintEnd_toStartOf",
        "app:layout_constraintEnd_toEndOf",
        "app:layout_constraintLeft_toLeftOf",
        "app:layout_constraintLeft_toRightOf",
        "app:layout_constraintRight_toLeftOf",
        "app:layout_constraintRight_toRightOf",
        "app:layout_constraintTop_toTopOf",
        "app:layout_constraintTop_toBottomOf",
        "app:layout_constraintBottom_toTopOf",
        "app:layout_constraintBottom_toBottomOf"
    )

    /** 安全子集支持的 bias 属性。 */
    val biasAttributes: Set<String> = setOf(
        "app:layout_constraintHorizontal_bias",
        "app:layout_constraintVertical_bias"
    )

    /** Guideline 特定属性。 */
    val guidelineAttributes: Set<String> = setOf(
        "app:layout_constraintGuide_begin",
        "app:layout_constraintGuide_end",
        "app:layout_constraintGuide_percent"
    )

    /** 安全子集支持的全部 ConstraintLayout 子属性。 */
    val supportedAttributes: Set<String> = anchorAttributes + biasAttributes + guidelineAttributes

    /** 锚点属性 -> ConstraintLayout.LayoutParams 字段名。 */
    val anchorFieldByAttribute: Map<String, String> = mapOf(
        "app:layout_constraintStart_toStartOf" to "startToStart",
        "app:layout_constraintStart_toEndOf" to "startToEnd",
        "app:layout_constraintEnd_toStartOf" to "endToStart",
        "app:layout_constraintEnd_toEndOf" to "endToEnd",
        "app:layout_constraintLeft_toLeftOf" to "leftToLeft",
        "app:layout_constraintLeft_toRightOf" to "leftToRight",
        "app:layout_constraintRight_toLeftOf" to "rightToLeft",
        "app:layout_constraintRight_toRightOf" to "rightToRight",
        "app:layout_constraintTop_toTopOf" to "topToTop",
        "app:layout_constraintTop_toBottomOf" to "topToBottom",
        "app:layout_constraintBottom_toTopOf" to "bottomToTop",
        "app:layout_constraintBottom_toBottomOf" to "bottomToBottom"
    )

    /** ConstraintLayout 帮助类标签 / 复杂特性标签 -> 一旦出现就 fallback。
     * Guideline 已从此列表移除，现在支持生成。 */
    private val helperTagNames: Set<String> = setOf(
        "androidx.constraintlayout.widget.Barrier",
        "androidx.constraintlayout.widget.Group",
        "androidx.constraintlayout.widget.Placeholder",
        "androidx.constraintlayout.helper.widget.Flow",
        "androidx.constraintlayout.helper.widget.Layer",
        "Barrier",
        "Group",
        "Placeholder",
        "Flow"
    )

    fun isHelperTag(tagName: String): Boolean = tagName in helperTagNames

    /**
     * 该节点是否包含 ConstraintLayout 安全子集之外的复杂特性属性。
     * 任何以 `app:layout_constraint` 为前缀且不在白名单内的属性都会被视作复杂特性。
     */
    fun hasComplexConstraintAttribute(node: LayoutNode): Boolean {
        return node.attributes.keys.any { attrName ->
            attrName.startsWith("app:layout_constraint") && attrName !in supportedAttributes
        }
    }

    /** 锚点值是否为 `parent` / `@id/foo` / `@+id/foo` 之一。 */
    fun isSupportedAnchorValue(value: String): Boolean {
        return value == "parent" ||
            value.startsWith("@id/") ||
            value.startsWith("@+id/")
    }

    /** bias 值必须是 Kotlin `toFloatOrNull()` 接受的浮点字面量。 */
    fun isSupportedBiasValue(value: String): Boolean {
        return value.toFloatOrNull() != null
    }

    fun parentIsConstraintLayout(parentTagName: String?): Boolean {
        val tagName = parentTagName ?: return false
        return LayoutNode(tagName, emptyMap(), emptyList()).isConstraintLayout()
    }

    /** 检查节点是否为 Guideline。 */
    fun isGuideline(tagName: String): Boolean {
        return tagName == "androidx.constraintlayout.widget.Guideline" || tagName == "Guideline"
    }

    /** Guideline orientation 值是否有效（vertical 或 horizontal）。 */
    fun isSupportedGuidelineOrientation(value: String): Boolean {
        return value == "vertical" || value == "horizontal"
    }

    /** Guideline guide_percent 值是否有效（0.0 到 1.0 之间的浮点数）。 */
    fun isSupportedGuidelinePercent(value: String): Boolean {
        val percent = value.toFloatOrNull() ?: return false
        return percent in 0.0f..1.0f
    }
}
