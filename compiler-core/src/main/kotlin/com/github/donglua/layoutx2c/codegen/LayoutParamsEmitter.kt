package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

interface LayoutParamsEmitter {
    fun emit(
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
        val attrs = node.node.attributes
        val width = layoutDimensionToCode(attrs["android:layout_width"] ?: "wrap_content")
        val height = layoutDimensionToCode(attrs["android:layout_height"] ?: "wrap_content")

        builder.addStatement("%L.layoutParams = %L", varName, layoutParamsToCode(node, parentVarName, width, height))

        val marginLeft = attrs["android:layout_marginLeft"] ?: attrs["android:layout_marginStart"] ?: attrs["android:layout_margin"]
        val marginTop = attrs["android:layout_marginTop"] ?: attrs["android:layout_margin"]
        val marginRight = attrs["android:layout_marginRight"] ?: attrs["android:layout_marginEnd"] ?: attrs["android:layout_margin"]
        val marginBottom = attrs["android:layout_marginBottom"] ?: attrs["android:layout_margin"]

        if (marginLeft != null || marginTop != null || marginRight != null || marginBottom != null) {
            builder.addStatement(
                "(%L.layoutParams as %T).setMargins(%L, %L, %L, %L)",
                varName,
                ClassName("android.view", "ViewGroup.MarginLayoutParams"),
                dimensionToCode(marginLeft ?: "0dp"),
                dimensionToCode(marginTop ?: "0dp"),
                dimensionToCode(marginRight ?: "0dp"),
                dimensionToCode(marginBottom ?: "0dp")
            )
        }
    }

    private fun layoutParamsToCode(
        node: AnalyzedNode,
        parentVarName: String,
        width: String,
        height: String
    ): String {
        val attrs = node.node.attributes
        val weight = attrs["android:layout_weight"]?.toFloatOrNull()
        return when (node.parentTagName) {
            "LinearLayout", "android.widget.LinearLayout" -> {
                if (weight != null) {
                    "android.widget.LinearLayout.LayoutParams($width, $height, ${weight}f)"
                } else {
                    "android.widget.LinearLayout.LayoutParams($width, $height)"
                }
            }
            "FrameLayout", "android.widget.FrameLayout" ->
                "android.widget.FrameLayout.LayoutParams($width, $height)"
            else ->
                "$parentVarName.generateLayoutParams(android.view.ViewGroup.MarginLayoutParams($width, $height))"
        }
    }

    private fun layoutDimensionToCode(value: String): String {
        return when (value) {
            "match_parent", "fill_parent" -> "android.view.ViewGroup.LayoutParams.MATCH_PARENT"
            "wrap_content" -> "android.view.ViewGroup.LayoutParams.WRAP_CONTENT"
            else -> dimensionToCode(value)
        }
    }

    private fun dimensionToCode(value: String): String {
        return when {
            value == "0" || value == "0dp" || value == "0px" -> "0"
            value.endsWith("dp") -> {
                val num = value.removeSuffix("dp")
                "(${num}f * context.resources.displayMetrics.density + 0.5f).toInt()"
            }
            value.endsWith("sp") -> {
                val num = value.removeSuffix("sp")
                "(${num}f * context.resources.displayMetrics.scaledDensity + 0.5f).toInt()"
            }
            value.endsWith("px") -> value.removeSuffix("px")
            else -> "0"
        }
    }
}
