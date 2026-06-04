package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.analyzer.SupportLevel
import com.github.donglua.layoutx2c.parser.isButton
import com.github.donglua.layoutx2c.registry.DefaultViewRegistry
import com.github.donglua.layoutx2c.registry.ViewEmitRegistry
import com.squareup.kotlinpoet.*

/**
 * 根据分析结果生成 Kotlin 代码。
 * 生成的类实现 LayoutFactory 接口。
 */
class LayoutCodeGenerator(
    private val packageName: String,
    private val rPackageName: String,
    viewEmitter: ViewEmitter? = null,
    attrEmitter: AttrEmitter? = null,
    private val layoutParamsEmitter: LayoutParamsEmitter = DefaultLayoutParamsEmitter(),
    private val viewRegistry: ViewEmitRegistry = DefaultViewRegistry
) {
    private val viewEmitter: ViewEmitter = viewEmitter ?: DefaultViewEmitter(viewRegistry)
    private val attrEmitter: AttrEmitter = attrEmitter ?: DefaultAttrEmitter(viewRegistry)

    fun generate(analyzedRoot: AnalyzedNode, layoutName: String, layoutResId: String): FileSpec {
        val className = layoutNameToClassName(layoutName)

        val createFun = FunSpec.builder("create")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("context", ClassName("android.content", "Context"))
            .addParameter(
                ParameterSpec.builder(
                    "parent",
                    ClassName("android.view", "ViewGroup").copy(nullable = true)
                ).build()
            )
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

    fun generateFacade(layoutName: String): FileSpec {
        val factoryClassName = layoutNameToClassName(layoutName)
        val facadeClassName = layoutNameToFacadeName(layoutName)

        val inflateFun = FunSpec.builder("inflate")
            .addParameter("context", ClassName("android.content", "Context"))
            .addParameter(
                ParameterSpec.builder(
                    "parent",
                    ClassName("android.view", "ViewGroup").copy(nullable = true)
                ).defaultValue("null").build()
            )
            .addParameter(
                ParameterSpec.builder("attachToParent", Boolean::class)
                    .defaultValue("false")
                    .build()
            )
            .returns(ClassName("android.view", "View"))
            .addStatement("val view = %N().create(context, parent)", factoryClassName)
            .beginControlFlow("if (attachToParent && parent != null)")
            .addStatement("parent.addView(view)")
            .endControlFlow()
            .addStatement("return view")
            .build()

        val typeSpec = TypeSpec.objectBuilder(facadeClassName)
            .addFunction(inflateFun)
            .build()

        return FileSpec.builder(packageName, facadeClassName)
            .addType(typeSpec)
            .build()
    }

    private fun generateCreateBody(node: AnalyzedNode, layoutResId: String): CodeBlock {
        val builder = CodeBlock.builder()

        if (node.isMerge) {
            builder.addStatement("requireNotNull(parent) { %S }", "Merge layout must have a non-null parent")
        }

        if (usesDensity(node, isRoot = true)) {
            builder.addStatement("val density = context.resources.displayMetrics.density")
            builder.add("\n")
        }

        generateNodeCode(builder, node, "root", "parent", layoutResId, isRoot = true, childPath = emptyList())

        builder.add("\n")
        if (node.isMerge) {
            builder.addStatement("return parent")
        } else {
            builder.addStatement("return root")
        }

        return builder.build()
    }

    private fun generateNodeCode(
        builder: CodeBlock.Builder,
        node: AnalyzedNode,
        varName: String,
        parentVarName: String,
        layoutResId: String,
        isRoot: Boolean,
        childPath: List<Int>
    ) {
        if (node.supportLevel == SupportLevel.FALLBACK || shouldCollapseToFallback(node)) {
            val fallbackInflater = ClassName("com.github.donglua.layoutx2c.runtime", "FallbackInflater")
            if (isRoot) {
                builder.addStatement(
                    "val %L = %T.inflate(context, %L, %L)",
                    varName,
                    fallbackInflater,
                    layoutResId,
                    parentVarName
                )
            } else {
                builder.addStatement(
                    "val %L = %T.inflateChild(context, %L, %L, %L)",
                    varName,
                    fallbackInflater,
                    layoutResId,
                    childPathToCode(childPath),
                    parentVarName
                )
                layoutParamsEmitter.emit(builder, varName, node, parentVarName)
            }
            return
        }

        if (node.isMerge) {
            for ((index, child) in node.children.withIndex()) {
                val childVarName = if (isRoot) "child$index" else "${varName}_child$index"
                builder.add("\n")
                generateNodeCode(
                    builder,
                    child,
                    childVarName,
                    parentVarName,
                    layoutResId,
                    isRoot = false,
                    childPath = childPath + child.indexInParent
                )
                if (isRoot) {
                    builder.addStatement("parent.addView(%L)", childVarName)
                } else {
                    builder.addStatement("%L.addView(%L)", parentVarName, childVarName)
                }
            }
            return
        }

        if (node.includedLayoutRef != null) {
            val layoutName = node.includedLayoutRef.removePrefix("@layout/")
            val factoryClassName = layoutNameToClassName(layoutName)
            builder.addStatement("val %L = %N().create(context, %L)", varName, factoryClassName, parentVarName)
            
            if (isRoot) {
                layoutParamsEmitter.emitRoot(builder, varName, node, parentVarName)
            } else {
                layoutParamsEmitter.emit(builder, varName, node, parentVarName)
            }
            return
        }

        val hasAttributes = hasEmittedAttributes(node)
        viewEmitter.emitCreate(builder, varName, node, hasAttributes)
        if (hasAttributes) {
            builder.indent()
            attrEmitter.emit(builder, node)
            builder.unindent()
            builder.addStatement("}")
        }

        // 生成 LayoutParams
        if (isRoot) {
            layoutParamsEmitter.emitRoot(builder, varName, node, parentVarName)
        } else {
            layoutParamsEmitter.emit(builder, varName, node, parentVarName)
        }

        // 递归生成子节点
        val fallbackChildren = node.children.withIndex()
            .filter { (_, child) -> isEffectiveFallback(child) }
        val fallbackBatchVarName = if (fallbackChildren.size > 1) "${varName}_fallbackChildren" else null
        val fallbackBatchIndexes = if (fallbackBatchVarName != null) {
            fallbackChildren.mapIndexed { batchIndex, indexedChild -> indexedChild.index to batchIndex }.toMap()
        } else {
            emptyMap()
        }

        if (fallbackBatchVarName != null) {
            val fallbackInflater = ClassName("com.github.donglua.layoutx2c.runtime", "FallbackInflater")
            val fallbackPaths = fallbackChildren.map { (_, child) -> childPath + child.indexInParent }
            builder.addStatement(
                "val %L = %T.inflateChildren(context, %L, %L, %L)",
                fallbackBatchVarName,
                fallbackInflater,
                layoutResId,
                childPathsToCode(fallbackPaths),
                varName
            )
        }

        for ((index, child) in node.children.withIndex()) {
            val childVarName = "${varName}_child$index"
            builder.add("\n")
            val fallbackBatchIndex = fallbackBatchIndexes[index]
            if (fallbackBatchVarName != null && fallbackBatchIndex != null) {
                builder.addStatement("val %L = %L[%L]", childVarName, fallbackBatchVarName, fallbackBatchIndex)
                layoutParamsEmitter.emit(builder, childVarName, child, varName)
            } else {
                generateNodeCode(
                    builder,
                    child,
                    childVarName,
                    varName,
                    layoutResId,
                    isRoot = false,
                    childPath = childPath + child.indexInParent
                )
            }
            // merge 子节点是虚拟容器，自身不会声明 childVarName，
            // 其孙子节点已经在 merge 分支里被 addView 到当前 varName。
            if (!child.isMerge) {
                builder.addStatement("%L.addView(%L)", varName, childVarName)
            }
        }
    }

    private fun childPathToCode(childPath: List<Int>): String {
        return childPath.joinToString(prefix = "intArrayOf(", postfix = ")")
    }

    private fun childPathsToCode(childPaths: List<List<Int>>): String {
        return childPaths.joinToString(prefix = "arrayOf(", postfix = ")") { childPath ->
            childPathToCode(childPath)
        }
    }

    private fun usesDensity(node: AnalyzedNode, isRoot: Boolean): Boolean {
        if (node.supportLevel == SupportLevel.FALLBACK || shouldCollapseToFallback(node)) {
            return !isRoot && fallbackChildLayoutParamsUseDensity(node)
        }
        return node.node.attributes.values.any { value -> value.endsWith("dp") } ||
            node.children.any { child -> usesDensity(child, isRoot = false) }
    }

    private fun fallbackChildLayoutParamsUseDensity(node: AnalyzedNode): Boolean {
        val attrs = node.node.attributes
        return fallbackChildLayoutParamAttributes.any { attrName ->
            attrs[attrName]?.endsWith("dp") == true
        }
    }

    private fun hasEmittedAttributes(node: AnalyzedNode): Boolean {
        if (node.node.isButton()) {
            return true
        }
        return node.supportedAttributes.any { attrName ->
            !attrName.startsWith("xmlns:") &&
                !attrName.startsWith("tools:") &&
                !attrName.startsWith("android:layout_") &&
                viewRegistry.canEmitAttribute(node, attrName)
        }
    }

    private fun shouldCollapseToFallback(node: AnalyzedNode): Boolean {
        if (node.supportLevel == SupportLevel.FALLBACK || node.isMerge || node.includedLayoutRef != null) {
            return false
        }
        if (hasEmittedAttributes(node)) {
            return false
        }
        return node.children.count(::isEffectiveFallback) > 1
    }

    private fun isEffectiveFallback(node: AnalyzedNode): Boolean {
        return node.supportLevel == SupportLevel.FALLBACK || shouldCollapseToFallback(node)
    }

    private fun layoutNameToClassName(layoutName: String): String {
        return "Layout_" + layoutName.split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        }
    }

    private fun layoutNameToFacadeName(layoutName: String): String {
        return layoutName.split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        } + "X2C"
    }

    private companion object {
        val fallbackChildLayoutParamAttributes = setOf(
            "android:layout_width",
            "android:layout_height",
            "android:layout_margin",
            "android:layout_marginLeft",
            "android:layout_marginStart",
            "android:layout_marginTop",
            "android:layout_marginRight",
            "android:layout_marginEnd",
            "android:layout_marginBottom"
        )
    }
}
