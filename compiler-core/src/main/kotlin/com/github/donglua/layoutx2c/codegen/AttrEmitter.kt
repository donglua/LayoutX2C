package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

interface AttrEmitter {
    fun emit(builder: CodeBlock.Builder, node: AnalyzedNode)
}

class DefaultAttrEmitter : AttrEmitter {

    override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
        val attrs = node.node.attributes

        attrs["android:id"]?.let { idValue ->
            val idName = idValue.removePrefix("@+id/").removePrefix("@id/")
            builder.addStatement("id = R.id.%L", idName)
        }

        attrs["android:orientation"]?.takeIf { node.node.isLinearLayout() }?.let { value ->
            val orientation = if (value == "horizontal") "HORIZONTAL" else "VERTICAL"
            builder.addStatement(
                "orientation = %T.%L",
                ClassName("android.widget", "LinearLayout"),
                orientation
            )
        }

        attrs["android:visibility"]?.let { value ->
            val visibility = when (value) {
                "gone" -> "GONE"
                "invisible" -> "INVISIBLE"
                else -> "VISIBLE"
            }
            builder.addStatement(
                "visibility = %T.%L",
                ClassName("android.view", "View"),
                visibility
            )
        }

        attrs["android:text"]?.let { value ->
            if (value.startsWith("@string/")) {
                val resName = value.removePrefix("@string/")
                builder.addStatement("text = context.getString(R.string.%L)", resName)
            } else {
                builder.addStatement("text = %S", value)
            }
        }

        emitPadding(builder, attrs)

        attrs["android:gravity"]?.let { value ->
            builder.addStatement("gravity = %L", gravityToCode(value))
        }
    }

    private fun emitPadding(builder: CodeBlock.Builder, attrs: Map<String, String>) {
        val left = attrs["android:paddingLeft"] ?: attrs["android:paddingStart"] ?: attrs["android:padding"]
        val top = attrs["android:paddingTop"] ?: attrs["android:padding"]
        val right = attrs["android:paddingRight"] ?: attrs["android:paddingEnd"] ?: attrs["android:padding"]
        val bottom = attrs["android:paddingBottom"] ?: attrs["android:padding"]

        if (left != null || top != null || right != null || bottom != null) {
            builder.addStatement(
                "setPadding(%L, %L, %L, %L)",
                dimensionToCode(left ?: "0dp"),
                dimensionToCode(top ?: "0dp"),
                dimensionToCode(right ?: "0dp"),
                dimensionToCode(bottom ?: "0dp")
            )
        }
    }

    private fun gravityToCode(value: String): String {
        val parts = value.split("|")
        return parts.joinToString(" or ") { part ->
            when (part.trim()) {
                "center" -> "android.view.Gravity.CENTER"
                "center_horizontal" -> "android.view.Gravity.CENTER_HORIZONTAL"
                "center_vertical" -> "android.view.Gravity.CENTER_VERTICAL"
                "top" -> "android.view.Gravity.TOP"
                "bottom" -> "android.view.Gravity.BOTTOM"
                "left", "start" -> "android.view.Gravity.START"
                "right", "end" -> "android.view.Gravity.END"
                else -> "android.view.Gravity.NO_GRAVITY"
            }
        }
    }

    private fun com.github.donglua.layoutx2c.parser.LayoutNode.isLinearLayout(): Boolean {
        return tagName == "LinearLayout" || tagName == "android.widget.LinearLayout"
    }
}
