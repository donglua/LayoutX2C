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

        attrs["android:background"]?.let { value ->
            emitBackground(builder, value)
        }

        if (node.node.isTextView()) {
            emitTextAttrs(builder, attrs)
        }

        if (node.node.isImageView()) {
            emitImageAttrs(builder, attrs)
        }

        emitPadding(builder, attrs)

        attrs["android:gravity"]?.let { value ->
            builder.addStatement("gravity = %L", gravityToCode(value))
        }
    }

    private fun emitTextAttrs(builder: CodeBlock.Builder, attrs: Map<String, String>) {
        attrs["android:text"]?.let { value ->
            if (value.startsWith("@string/")) {
                val resName = value.removePrefix("@string/")
                builder.addStatement("text = context.getString(R.string.%L)", resName)
            } else {
                builder.addStatement("text = %S", value)
            }
        }

        attrs["android:textColor"]?.let { value ->
            emitColorAssignment(builder, value, "setTextColor")
        }

        attrs["android:textSize"]?.let { value ->
            builder.addStatement("setTextSize(%T.COMPLEX_UNIT_PX, %L)", ClassName("android.util", "TypedValue"), dimensionToPxFloatCode(value))
        }

        attrs["android:textStyle"]?.let { value ->
            builder.addStatement("setTypeface(typeface, %L)", textStyleToCode(value))
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

    private fun emitImageAttrs(builder: CodeBlock.Builder, attrs: Map<String, String>) {
        attrs["android:src"]?.let { value ->
            if (value.startsWith("@drawable/")) {
                val resName = value.removePrefix("@drawable/")
                builder.addStatement("setImageResource(R.drawable.%L)", resName)
            }
        }

        attrs["android:scaleType"]?.let { value ->
            ImageScaleTypes.enumName(value)?.let { scaleType ->
                builder.addStatement("scaleType = %T.ScaleType.%L", ClassName("android.widget", "ImageView"), scaleType)
            }
        }

        (attrs["app:tint"] ?: attrs["android:tint"])?.let { value ->
            if (value.startsWith("@color/")) {
                val resName = value.removePrefix("@color/")
                builder.addStatement(
                    "imageTintList = %T.getColorStateList(context, R.color.%L)",
                    ClassName("androidx.core.content", "ContextCompat"),
                    resName
                )
            }
        }
    }

    private fun emitBackground(builder: CodeBlock.Builder, value: String) {
        when {
            value.startsWith("@drawable/") -> {
                val resName = value.removePrefix("@drawable/")
                builder.addStatement("setBackgroundResource(R.drawable.%L)", resName)
            }
            value.startsWith("@color/") -> {
                val resName = value.removePrefix("@color/")
                builder.addStatement("setBackgroundColor(%T.getColor(context, R.color.%L))", ClassName("androidx.core.content", "ContextCompat"), resName)
            }
            value.startsWith("#") -> {
                builder.addStatement("setBackgroundColor(%T.parseColor(%S))", ClassName("android.graphics", "Color"), value)
            }
        }
    }

    private fun emitColorAssignment(builder: CodeBlock.Builder, value: String, methodName: String) {
        when {
            value.startsWith("@color/") -> {
                val resName = value.removePrefix("@color/")
                builder.addStatement("%L(%T.getColor(context, R.color.%L))", methodName, ClassName("androidx.core.content", "ContextCompat"), resName)
            }
            value.startsWith("#") -> {
                builder.addStatement("%L(%T.parseColor(%S))", methodName, ClassName("android.graphics", "Color"), value)
            }
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

    private fun com.github.donglua.layoutx2c.parser.LayoutNode.isTextView(): Boolean {
        return tagName == "TextView" || tagName == "android.widget.TextView"
    }

    private fun com.github.donglua.layoutx2c.parser.LayoutNode.isImageView(): Boolean {
        return tagName == "ImageView" ||
            tagName == "android.widget.ImageView" ||
            tagName == "androidx.appcompat.widget.AppCompatImageView"
    }

    private fun textStyleToCode(value: String): String {
        val styles = value.split("|").map { it.trim() }.toSet()
        return when {
            "bold" in styles && "italic" in styles -> "android.graphics.Typeface.BOLD_ITALIC"
            "bold" in styles -> "android.graphics.Typeface.BOLD"
            "italic" in styles -> "android.graphics.Typeface.ITALIC"
            else -> "android.graphics.Typeface.NORMAL"
        }
    }
}
