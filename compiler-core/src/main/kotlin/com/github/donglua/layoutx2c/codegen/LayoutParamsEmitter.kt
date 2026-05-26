package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.isFrameLayout
import com.github.donglua.layoutx2c.parser.isLinearLayout
import com.github.donglua.layoutx2c.parser.isRelativeLayout
import com.github.donglua.layoutx2c.parser.isScrollView
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

interface LayoutParamsEmitter {
    fun emit(
        builder: CodeBlock.Builder,
        varName: String,
        node: AnalyzedNode,
        parentVarName: String
    )

    fun emitRoot(
        builder: CodeBlock.Builder,
        varName: String,
        node: AnalyzedNode,
        parentVarName: String
    )
}

class DefaultLayoutParamsEmitter : LayoutParamsEmitter {

    override fun emit(
        builder: CodeBlock.Builder,
        varName: String,
        node: AnalyzedNode,
        parentVarName: String
    ) {
        emitLayoutParams(builder, varName, node, parentVarName)
        emitLayoutGravity(builder, varName, node)
        emitRelativeLayoutRules(builder, varName, node)
    }

    override fun emitRoot(
        builder: CodeBlock.Builder,
        varName: String,
        node: AnalyzedNode,
        parentVarName: String
    ) {
        emitRootLayoutParams(builder, varName, node, parentVarName)
    }

    private fun emitLayoutParams(
        builder: CodeBlock.Builder,
        varName: String,
        node: AnalyzedNode,
        parentVarName: String
    ) {
        val attrs = node.node.attributes
        val width = layoutDimensionToCode(attrs["android:layout_width"] ?: "wrap_content")
        val height = layoutDimensionToCode(attrs["android:layout_height"] ?: "wrap_content")

        builder.addStatement("%L.layoutParams = %L", varName, layoutParamsToCode(node, parentVarName, width, height))
        emitMargins(builder, varName, attrs)
    }

    private fun emitRootLayoutParams(
        builder: CodeBlock.Builder,
        varName: String,
        node: AnalyzedNode,
        parentVarName: String
    ) {
        val attrs = node.node.attributes
        val width = layoutDimensionToCode(attrs["android:layout_width"] ?: "wrap_content")
        val height = layoutDimensionToCode(attrs["android:layout_height"] ?: "wrap_content")

        builder.beginControlFlow("%L?.let { parentView ->", parentVarName)
        builder.addStatement(
            "%L.layoutParams = when (parentView) {\n" +
                "  is %T -> %T(%L, %L)\n" +
                "  is %T -> %T(%L, %L)\n" +
                "  is %T -> %T(%L, %L)\n" +
                "  else -> %T(%L, %L)\n" +
                "}",
            varName,
            linearLayoutClass,
            linearLayoutParamsClass,
            width,
            height,
            frameLayoutClass,
            frameLayoutParamsClass,
            width,
            height,
            relativeLayoutClass,
            relativeLayoutParamsClass,
            width,
            height,
            viewGroupMarginLayoutParamsClass,
            width,
            height
        )
        emitMargins(builder, varName, attrs)
        builder.endControlFlow()
    }

    private fun emitMargins(builder: CodeBlock.Builder, varName: String, attrs: Map<String, String>) {
        val marginAll = attrs["android:layout_margin"]
        val marginLeft = attrs["android:layout_marginLeft"] ?: attrs["android:layout_marginStart"]
        val marginTop = attrs["android:layout_marginTop"]
        val marginRight = attrs["android:layout_marginRight"] ?: attrs["android:layout_marginEnd"]
        val marginBottom = attrs["android:layout_marginBottom"]

        if (marginAll != null) {
            builder.addStatement(
                "(%L.layoutParams as %T).setMargins(%L, %L, %L, %L)",
                varName,
                ClassName("android.view", "ViewGroup", "MarginLayoutParams"),
                dimensionToCode(marginLeft ?: marginAll),
                dimensionToCode(marginTop ?: marginAll),
                dimensionToCode(marginRight ?: marginAll),
                dimensionToCode(marginBottom ?: marginAll)
            )
            return
        }

        marginLeft?.let { emitMarginAssignment(builder, varName, "leftMargin", it) }
        marginTop?.let { emitMarginAssignment(builder, varName, "topMargin", it) }
        marginRight?.let { emitMarginAssignment(builder, varName, "rightMargin", it) }
        marginBottom?.let { emitMarginAssignment(builder, varName, "bottomMargin", it) }
    }

    private fun emitMarginAssignment(
        builder: CodeBlock.Builder,
        varName: String,
        propertyName: String,
        value: String
    ) {
        builder.addStatement(
            "(%L.layoutParams as %T).%L = %L",
            varName,
            ClassName("android.view", "ViewGroup", "MarginLayoutParams"),
            propertyName,
            dimensionToCode(value)
        )
    }

    private fun emitLayoutGravity(builder: CodeBlock.Builder, varName: String, node: AnalyzedNode) {
        val attrs = node.node.attributes
        val layoutGravity = attrs["android:layout_gravity"] ?: return
        val layoutParamsClass = layoutGravityParamsClass(node.parentTagName) ?: return
        builder.addStatement(
            "(%L.layoutParams as %T).gravity = %L",
            varName,
            layoutParamsClass,
            gravityToCode(layoutGravity)
        )
    }

    private fun layoutParamsToCode(
        node: AnalyzedNode,
        parentVarName: String,
        width: CodeBlock,
        height: CodeBlock
    ): CodeBlock {
        val attrs = node.node.attributes
        val weight = attrs["android:layout_weight"]?.toFloatOrNull()
        return when {
            node.parentIs(LayoutNode::isLinearLayout) -> {
                if (weight != null) {
                    CodeBlock.of("%T(%L, %L, %Lf)", linearLayoutParamsClass, width, height, weight)
                } else {
                    CodeBlock.of("%T(%L, %L)", linearLayoutParamsClass, width, height)
                }
            }
            node.parentIs(LayoutNode::isFrameLayout) || node.parentIs(LayoutNode::isScrollView) ->
                CodeBlock.of("%T(%L, %L)", frameLayoutParamsClass, width, height)
            node.parentIs(LayoutNode::isRelativeLayout) ->
                CodeBlock.of("%T(%L, %L)", relativeLayoutParamsClass, width, height)
            else ->
                CodeBlock.of("%T(%L, %L)", viewGroupMarginLayoutParamsClass, width, height)
        }
    }

    private fun AnalyzedNode.parentIs(predicate: LayoutNode.() -> Boolean): Boolean {
        val tagName = parentTagName ?: return false
        return LayoutNode(tagName, emptyMap(), emptyList()).predicate()
    }

    private fun layoutGravityParamsClass(parentTagName: String?): ClassName? {
        val parent = parentTagName?.let { LayoutNode(it, emptyMap(), emptyList()) } ?: return null
        return when {
            parent.isLinearLayout() -> linearLayoutParamsClass
            parent.isFrameLayout() || parent.isScrollView() -> frameLayoutParamsClass
            else -> null
        }
    }

    private fun emitRelativeLayoutRules(builder: CodeBlock.Builder, varName: String, node: AnalyzedNode) {
        if (!node.parentIs(LayoutNode::isRelativeLayout)) return

        for ((attrName, value) in node.node.attributes) {
            val rule = relativeLayoutIdRules[attrName]
            if (rule != null) {
                builder.addStatement(
                    "(%L.layoutParams as %T).addRule(%T.%L, R.id.%L)",
                    varName,
                    relativeLayoutParamsClass,
                    relativeLayoutClass,
                    rule,
                    idName(value)
                )
                continue
            }

            val booleanRule = relativeLayoutBooleanRules[attrName]
            if (booleanRule != null && value == "true") {
                builder.addStatement(
                    "(%L.layoutParams as %T).addRule(%T.%L)",
                    varName,
                    relativeLayoutParamsClass,
                    relativeLayoutClass,
                    booleanRule
                )
            }
        }
    }

    private fun idName(value: String): String {
        return value.removePrefix("@+id/").removePrefix("@id/")
    }

    private fun layoutDimensionToCode(value: String): CodeBlock {
        return when (value) {
            "match_parent", "fill_parent" -> CodeBlock.of("%T.MATCH_PARENT", viewGroupLayoutParamsClass)
            "wrap_content" -> CodeBlock.of("%T.WRAP_CONTENT", viewGroupLayoutParamsClass)
            else -> CodeBlock.of("%L", dimensionToCode(value))
        }
    }

    private companion object {
        val viewGroupLayoutParamsClass = ClassName("android.view", "ViewGroup", "LayoutParams")
        val viewGroupMarginLayoutParamsClass = ClassName("android.view", "ViewGroup", "MarginLayoutParams")
        val linearLayoutClass = ClassName("android.widget", "LinearLayout")
        val linearLayoutParamsClass = ClassName("android.widget", "LinearLayout", "LayoutParams")
        val frameLayoutClass = ClassName("android.widget", "FrameLayout")
        val frameLayoutParamsClass = ClassName("android.widget", "FrameLayout", "LayoutParams")
        val relativeLayoutClass = ClassName("android.widget", "RelativeLayout")
        val relativeLayoutParamsClass = ClassName("android.widget", "RelativeLayout", "LayoutParams")
        val relativeLayoutIdRules = mapOf(
            "android:layout_above" to "ABOVE",
            "android:layout_below" to "BELOW",
            "android:layout_toStartOf" to "START_OF",
            "android:layout_toEndOf" to "END_OF",
            "android:layout_toLeftOf" to "LEFT_OF",
            "android:layout_toRightOf" to "RIGHT_OF",
            "android:layout_alignStart" to "ALIGN_START",
            "android:layout_alignEnd" to "ALIGN_END",
            "android:layout_alignLeft" to "ALIGN_LEFT",
            "android:layout_alignRight" to "ALIGN_RIGHT",
            "android:layout_alignTop" to "ALIGN_TOP",
            "android:layout_alignBottom" to "ALIGN_BOTTOM"
        )
        val relativeLayoutBooleanRules = mapOf(
            "android:layout_alignParentStart" to "ALIGN_PARENT_START",
            "android:layout_alignParentEnd" to "ALIGN_PARENT_END",
            "android:layout_alignParentLeft" to "ALIGN_PARENT_LEFT",
            "android:layout_alignParentRight" to "ALIGN_PARENT_RIGHT",
            "android:layout_alignParentTop" to "ALIGN_PARENT_TOP",
            "android:layout_alignParentBottom" to "ALIGN_PARENT_BOTTOM",
            "android:layout_centerInParent" to "CENTER_IN_PARENT",
            "android:layout_centerHorizontal" to "CENTER_HORIZONTAL",
            "android:layout_centerVertical" to "CENTER_VERTICAL"
        )
    }
}
