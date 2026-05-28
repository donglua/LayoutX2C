package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.analyzer.SupportLevel
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

        if (usesDensity(node)) {
            builder.addStatement("val density = context.resources.displayMetrics.density")
            builder.add("\n")
        }

        generateNodeCode(builder, node, "root", "parent", layoutResId, isRoot = true, childPath = emptyList())

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
        isRoot: Boolean,
        childPath: List<Int>
    ) {
        if (node.supportLevel == SupportLevel.FALLBACK) {
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
        for ((index, child) in node.children.withIndex()) {
            val childVarName = "${varName}_child$index"
            builder.add("\n")
            generateNodeCode(
                builder,
                child,
                childVarName,
                varName,
                layoutResId,
                isRoot = false,
                childPath = childPath + child.indexInParent
            )
            builder.addStatement("%L.addView(%L)", varName, childVarName)
        }
    }

    private fun childPathToCode(childPath: List<Int>): String {
        return childPath.joinToString(prefix = "intArrayOf(", postfix = ")")
    }

    private fun usesDensity(node: AnalyzedNode): Boolean {
        if (node.supportLevel == SupportLevel.FALLBACK) return false
        return node.node.attributes.values.any { value -> value.endsWith("dp") } ||
            node.children.any(::usesDensity)
    }

    private fun hasEmittedAttributes(node: AnalyzedNode): Boolean {
        return node.supportedAttributes.any { attrName ->
            !attrName.startsWith("xmlns:") &&
                !attrName.startsWith("tools:") &&
                !attrName.startsWith("android:layout_") &&
                viewRegistry.canEmitAttribute(node, attrName)
        }
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
}
