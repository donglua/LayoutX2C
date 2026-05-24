package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.analyzer.SupportLevel
import com.squareup.kotlinpoet.*

/**
 * 根据分析结果生成 Kotlin 代码。
 * 生成的类实现 LayoutFactory 接口。
 */
class LayoutCodeGenerator(
    private val packageName: String,
    private val rPackageName: String
) {

    fun generate(analyzedRoot: AnalyzedNode, layoutName: String, layoutResId: String): FileSpec {
        val className = layoutNameToClassName(layoutName)

        val createFun = FunSpec.builder("create")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("parent", ClassName("android.view", "ViewGroup").copy(nullable = true))
            .returns(ClassName("android.view", "View"))
            .addCode(generateCreateBody(analyzedRoot, layoutResId))
            .build()

        val typeSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(ClassName("com.github.donglua.layoutx2c.runtime", "LayoutFactory"))
            .addFunction(createFun)
            .build()

        return FileSpec.builder(packageName, className)
            .addImport(rPackageName, "R")
            .addType(typeSpec)
            .build()
    }

    private fun generateCreateBody(node: AnalyzedNode, layoutResId: String): CodeBlock {
        val builder = CodeBlock.builder()

        builder.addStatement("val context = parent?.context ?: throw IllegalArgumentException(\"parent must not be null\")")
        builder.add("\n")

        generateNodeCode(builder, node, "root", "parent", layoutResId, isRoot = true)

        builder.add("\n")
        builder.addStatement("return root")

        return builder.build()
    }

    private fun generateNodeCode(
        builder: CodeBlock.Builder,
        node: AnalyzedNode,
        varName: String,
        parentVarName: String,
        layoutResId: String,
        isRoot: Boolean
    ) {
        if (node.supportLevel == SupportLevel.FALLBACK) {
            // Fallback: 使用 FallbackInflater
            builder.addStatement(
                "val %L = %T.inflateChild(context, %L, %L, %L)",
                varName,
                ClassName("com.github.donglua.layoutx2c.runtime", "FallbackInflater"),
                layoutResId,
                node.indexInParent,
                parentVarName
            )
            return
        }

        // 生成 View 创建代码
        val viewClass = resolveViewClass(node.node.tagName)
        builder.addStatement("val %L = %T(context).apply {", varName, viewClass)
        builder.indent()

        // 生成属性设置代码
        generateAttributeCode(builder, node)

        builder.unindent()
        builder.addStatement("}")

        // 生成 LayoutParams
        if (!isRoot) {
            generateLayoutParamsCode(builder, varName, node, parentVarName)
        }

        // 递归生成子节点
        for ((index, child) in node.children.withIndex()) {
            val childVarName = "${varName}_child$index"
            builder.add("\n")
            generateNodeCode(builder, child, childVarName, varName, layoutResId, isRoot = false)
            builder.addStatement("%L.addView(%L)", varName, childVarName)
        }
    }

    private fun generateAttributeCode(builder: CodeBlock.Builder, node: AnalyzedNode) {
        val attrs = node.node.attributes

        attrs["android:id"]?.let { idValue ->
            val idName = idValue.removePrefix("@+id/").removePrefix("@id/")
            builder.addStatement("id = R.id.%L", idName)
        }

        attrs["android:orientation"]?.let { value ->
            val orientation = if (value == "horizontal") "HORIZONTAL" else "VERTICAL"
            builder.addStatement("orientation = %T.%L",
                ClassName("android.widget", "LinearLayout"), orientation)
        }

        attrs["android:visibility"]?.let { value ->
            val visibility = when (value) {
                "gone" -> "GONE"
                "invisible" -> "INVISIBLE"
                else -> "VISIBLE"
            }
            builder.addStatement("visibility = %T.%L",
                ClassName("android.view", "View"), visibility)
        }

        attrs["android:text"]?.let { value ->
            if (value.startsWith("@string/")) {
                val resName = value.removePrefix("@string/")
                builder.addStatement("text = context.getString(R.string.%L)", resName)
            } else {
                builder.addStatement("text = %S", value)
            }
        }

        // Padding
        generatePaddingCode(builder, attrs)

        // Gravity
        attrs["android:gravity"]?.let { value ->
            builder.addStatement("gravity = %L", gravityToCode(value))
        }
    }

    private fun generatePaddingCode(builder: CodeBlock.Builder, attrs: Map<String, String>) {
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

    private fun generateLayoutParamsCode(
        builder: CodeBlock.Builder,
        varName: String,
        node: AnalyzedNode,
        parentVarName: String
    ) {
        val attrs = node.node.attributes
        val width = layoutDimensionToCode(attrs["android:layout_width"] ?: "wrap_content")
        val height = layoutDimensionToCode(attrs["android:layout_height"] ?: "wrap_content")

        // TODO: 根据 parent 类型生成正确的 LayoutParams 类型
        builder.addStatement(
            "%L.layoutParams = %T(%L, %L)",
            varName,
            ClassName("android.view", "ViewGroup.MarginLayoutParams"),
            width, height
        )

        // Margins
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

    private fun resolveViewClass(tagName: String): ClassName {
        return when (tagName) {
            "LinearLayout", "android.widget.LinearLayout" ->
                ClassName("android.widget", "LinearLayout")
            "FrameLayout", "android.widget.FrameLayout" ->
                ClassName("android.widget", "FrameLayout")
            "TextView", "android.widget.TextView" ->
                ClassName("androidx.appcompat.widget", "AppCompatTextView")
            "View", "android.view.View" ->
                ClassName("android.view", "View")
            else -> ClassName.bestGuess(tagName)
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
            value.endsWith("dp") -> {
                val num = value.removeSuffix("dp")
                "(${num}f * context.resources.displayMetrics.density + 0.5f).toInt()"
            }
            value.endsWith("sp") -> {
                val num = value.removeSuffix("sp")
                "(${num}f * context.resources.displayMetrics.scaledDensity + 0.5f).toInt()"
            }
            value.endsWith("px") -> value.removeSuffix("px")
            value == "0" || value == "0dp" || value == "0px" -> "0"
            else -> "0"
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

    private fun layoutNameToClassName(layoutName: String): String {
        return "Layout_" + layoutName.split("_").joinToString("") { it.capitalize() }
    }
}
