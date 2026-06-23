package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.analyzer.SupportLevel
import com.github.donglua.layoutx2c.parser.XmlAttribute
import com.github.donglua.layoutx2c.parser.isButton
import com.github.donglua.layoutx2c.registry.DefaultViewRegistry
import com.github.donglua.layoutx2c.registry.ViewEmitRegistry
import com.github.donglua.layoutx2c.resources.ResourceReferenceResolver
import com.github.donglua.layoutx2c.resources.referenceCode
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

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
    private val viewRegistry: ViewEmitRegistry = DefaultViewRegistry,
    private val resourceResolver: ResourceReferenceResolver? = null,
    private val enableSyntheticAttributeSet: Boolean = true
) {
    private val viewEmitter: ViewEmitter = viewEmitter ?: DefaultViewEmitter(viewRegistry)
    private val attrEmitter: AttrEmitter = attrEmitter ?: DefaultAttrEmitter(viewRegistry)

    fun generate(analyzedRoot: AnalyzedNode, layoutName: String, layoutResId: String): FileSpec {
        val className = layoutNameToClassName(layoutName)
        val contextClass = ClassName("android.content", "Context")
        val viewClass = ClassName("android.view", "View")
        val viewGroupClass = ClassName("android.view", "ViewGroup")
        val sparseArrayClass = ClassName("android.util", "SparseArray").parameterizedBy(viewClass)

        val createFun = FunSpec.builder("create")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("context", contextClass)
            .addParameter(
                ParameterSpec.builder(
                    "parent",
                    viewGroupClass.copy(nullable = true)
                ).build()
            )
            .returns(viewClass)
            .addStatement("return create(context, parent, null)")
            .build()

        val createWithRefsFun = FunSpec.builder("create")
            .addParameter("context", contextClass)
            .addParameter(
                ParameterSpec.builder(
                    "parent",
                    viewGroupClass.copy(nullable = true)
                ).build()
            )
            .addParameter("refs", sparseArrayClass.copy(nullable = true))
            .returns(viewClass)
            .addCode(generateCreateBody(analyzedRoot, layoutResId))
            .build()

        val typeSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(ClassName("com.github.donglua.layoutx2c.runtime", "LayoutFactory"))
            .addFunction(createFun)
            .addFunction(createWithRefsFun)
            .build()

        return FileSpec.builder(packageName, className)
            .addImport(rPackageName, "R")
            .addType(typeSpec)
            .build()
    }

    fun generateFacade(layoutName: String): FileSpec {
        val factoryClassName = layoutNameToClassName(layoutName)
        val facadeClassName = layoutNameToFacadeName(layoutName)
        val viewClass = ClassName("android.view", "View")
        val sparseArrayClass = ClassName("android.util", "SparseArray").parameterizedBy(viewClass)

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
            .addStatement("val view = factory.create(context, parent)")
            .beginControlFlow("if (attachToParent && parent != null)")
            .addStatement("parent.addView(view)")
            .endControlFlow()
            .addStatement("return view")
            .build()

        val inflateWithRefsFun = FunSpec.builder("inflateWithRefs")
            .addModifiers(KModifier.INTERNAL)
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
            .returns(Pair::class.asClassName().parameterizedBy(viewClass, sparseArrayClass))
            .addStatement("val refs = %T()", sparseArrayClass)
            .addStatement("val view = factory.create(context, parent, refs)")
            .beginControlFlow("if (attachToParent && parent != null)")
            .addStatement("parent.addView(view)")
            .endControlFlow()
            .addStatement("return view to refs")
            .build()

        val typeSpec = TypeSpec.objectBuilder(facadeClassName)
            .addProperty(
                PropertySpec.builder("factory", ClassName(packageName, factoryClassName), KModifier.PRIVATE)
                    .initializer("%N()", factoryClassName)
                    .build()
            )
            .addFunction(inflateFun)
            .addFunction(inflateWithRefsFun)
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
        val collapseToFallback = !isRoot && shouldCollapseToFallback(node)
        if (node.supportLevel == SupportLevel.FALLBACK || collapseToFallback) {
            val fallbackInflater = ClassName("com.github.donglua.layoutx2c.runtime", "FallbackInflater")
            if (isRoot) {
                builder.addStatement(
                    "val %L = %T.inflate(context, %L, %L)",
                    varName,
                    fallbackInflater,
                    layoutResId,
                    parentVarName
                )
                emitRefCapture(builder, varName, node)
            } else {
                builder.addStatement(
                    "val %L = %T.inflateChild(context, %L, %L, %L)",
                    varName,
                    fallbackInflater,
                    layoutResId,
                    childPlanToCode(childPath, node),
                    parentVarName
                )
                emitRefCapture(builder, varName, node)
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
            val facadeClassName = layoutNameToFacadeName(layoutName)
            builder.addStatement("val %L = %N.inflate(context, %L)", varName, facadeClassName, parentVarName)
            emitIdAssignment(builder, varName, node)
            emitRefCapture(builder, varName, node)
            
            if (isRoot) {
                layoutParamsEmitter.emitRoot(builder, varName, node, parentVarName)
            } else {
                layoutParamsEmitter.emit(builder, varName, node, parentVarName)
            }
            return
        }

        val hasAttributes = hasEmittedAttributes(node)
        val syntheticAttrsVarName = emitSyntheticAttributeSetIfNeeded(builder, varName, node)
        viewEmitter.emitCreate(builder, varName, node, hasAttributes, syntheticAttrsVarName)
        if (hasAttributes) {
            builder.indent()
            attrEmitter.emit(builder, node)
            builder.unindent()
            builder.addStatement("}")
        }
        emitRefCapture(builder, varName, node)

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
            val fallbackPlans = fallbackChildren.map { (_, child) -> (childPath + child.indexInParent) to child }
            builder.addStatement(
                "val %L = %T.inflateChildren(context, %L, %L, %L)",
                fallbackBatchVarName,
                fallbackInflater,
                layoutResId,
                childPlansToCode(fallbackPlans),
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

    private fun childPlanToCode(childPath: List<Int>, node: AnalyzedNode): CodeBlock {
        return CodeBlock.of(
            "%T(%L, %S)",
            fallbackChildPlan,
            childPathToCode(childPath),
            node.node.tagName
        )
    }

    private fun childPlansToCode(childPlans: List<Pair<List<Int>, AnalyzedNode>>): CodeBlock {
        val builder = CodeBlock.builder()
        builder.add("arrayOf(")
        for ((index, childPlan) in childPlans.withIndex()) {
            if (index > 0) {
                builder.add(", ")
            }
            val (childPath, node) = childPlan
            builder.add("%L", childPlanToCode(childPath, node))
        }
        builder.add(")")
        return builder.build()
    }

    private companion object {
        val fallbackChildPlan = ClassName("com.github.donglua.layoutx2c.runtime", "FallbackChildPlan")
        val syntheticAttributeSet = ClassName("com.github.donglua.layoutx2c.runtime", "SyntheticAttributeSet")
        val syntheticAttribute = syntheticAttributeSet.nestedClass("Attribute")

        const val androidNamespace = "http://schemas.android.com/apk/res/android"
        const val xmlNsNamespace = "http://www.w3.org/2000/xmlns/"
        const val resAutoNamespace = "http://schemas.android.com/apk/res-auto"

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

    private fun emitSyntheticAttributeSetIfNeeded(
        builder: CodeBlock.Builder,
        varName: String,
        node: AnalyzedNode
    ): String? {
        if (!enableSyntheticAttributeSet || !shouldUseSyntheticAttributeSet(node.node.tagName)) {
            return null
        }
        val attributes = node.node.xmlAttributes
            .filterNot(::isSyntheticAttributeSetIgnored)
        if (attributes.isEmpty()) {
            return null
        }

        val attrsVarName = "${varName}_attrs"
        val attrsCode = CodeBlock.builder()
        attributes.forEachIndexed { index, attribute ->
            if (index > 0) {
                attrsCode.add(",·\n")
            }
            attrsCode.add(
                "%T(namespace = %L, name = %S, value = %S, nameResourceId = %L, valueResourceId = %L)",
                syntheticAttribute,
                attribute.namespaceUri?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"),
                attribute.name,
                attribute.value,
                attributeNameResourceCode(attribute),
                attributeValueResourceCode(attribute.value)
            )
        }

        builder.addStatement("val %L = %T.of(%L)", attrsVarName, syntheticAttributeSet, attrsCode.build())
        return attrsVarName
    }

    private fun isSyntheticAttributeSetIgnored(attribute: XmlAttribute): Boolean {
        return attribute.qualifiedName == "xmlns" ||
            attribute.qualifiedName.startsWith("xmlns:") ||
            attribute.namespaceUri == xmlNsNamespace ||
            attribute.qualifiedName.startsWith("tools:")
    }

    private fun shouldUseSyntheticAttributeSet(tagName: String): Boolean {
        if (!tagName.contains('.')) return false
        val frameworkPrefixes = listOf(
            "android.",
            "androidx.",
            "com.google.android.material."
        )
        return frameworkPrefixes.none { prefix -> tagName.startsWith(prefix) }
    }

    private fun attributeNameResourceCode(attribute: XmlAttribute): String {
        if (!attribute.name.isValidResourceFieldName()) return "0"
        return when {
            attribute.namespaceUri == androidNamespace || attribute.qualifiedName.startsWith("android:") ->
                "android.R.attr.${attribute.name}"
            attribute.namespaceUri == resAutoNamespace || attribute.qualifiedName.startsWith("app:") ->
                resourceResolver?.referenceCode("attr", attribute.name, rPackageName) ?: "0"
            attribute.namespaceUri?.startsWith("http://schemas.android.com/apk/res/") == true -> {
                val packageName = attribute.namespaceUri.removePrefix("http://schemas.android.com/apk/res/")
                if (packageName == rPackageName) {
                    resourceResolver?.referenceCode("attr", attribute.name, rPackageName) ?: "R.attr.${attribute.name}"
                } else {
                    "$packageName.R.attr.${attribute.name}"
                }
            }
            attribute.namespaceUri == null && attribute.qualifiedName == "style" -> 0.toString()
            else -> "0"
        }
    }

    private fun attributeValueResourceCode(value: String): String {
        if (value == "@null") return "0"
        androidResourceReference(value)?.let { return it }
        localResourceReference(value)?.let { (type, name) ->
            if (!type.isValidResourceFieldName() || !name.isValidResourceFieldName()) return "0"
            if (type == "id") return "R.id.$name"
            return resourceResolver?.referenceCode(type, name, rPackageName) ?: "0"
        }
        themeAttributeReference(value)?.let { return it }
        return "0"
    }

    private fun androidResourceReference(value: String): String? {
        val prefix = when {
            value.startsWith("@android:") -> "@android:"
            value.startsWith("?android:attr/") -> "?android:attr/"
            else -> return null
        }
        val body = value.removePrefix(prefix)
        val parts = body.split('/', limit = 2)
        return if (parts.size == 2 && parts[0].isValidResourceFieldName() && parts[1].isValidResourceFieldName()) {
            "android.R.${parts[0]}.${parts[1]}"
        } else if (prefix == "?android:attr/" && body.isValidResourceFieldName()) {
            "android.R.attr.$body"
        } else {
            null
        }
    }

    private fun localResourceReference(value: String): Pair<String, String>? {
        if (!value.startsWith("@") || value.startsWith("@android:")) return null
        val body = value.removePrefix("@").removePrefix("+")
        val parts = body.split('/', limit = 2)
        if (parts.size != 2) return null
        return parts[0] to parts[1]
    }

    private fun themeAttributeReference(value: String): String? {
        if (!value.startsWith("?") || value.startsWith("?android:")) return null
        val name = value.removePrefix("?attr/").removePrefix("?")
        if (!name.isValidResourceFieldName()) return null
        return resourceResolver?.referenceCode("attr", name, rPackageName)
    }

    private fun String.isValidResourceFieldName(): Boolean {
        return matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))
    }

    private fun usesDensity(node: AnalyzedNode, isRoot: Boolean): Boolean {
        val collapseToFallback = !isRoot && shouldCollapseToFallback(node)
        if (node.supportLevel == SupportLevel.FALLBACK || collapseToFallback) {
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

    private fun emitRefCapture(builder: CodeBlock.Builder, varName: String, node: AnalyzedNode) {
        val idName = node.idName() ?: return
        if (idName.isBlank()) return
        builder.addStatement("refs?.put(R.id.%L, %L)", idName, varName)
    }

    private fun emitIdAssignment(builder: CodeBlock.Builder, varName: String, node: AnalyzedNode) {
        val idName = node.idName() ?: return
        if (idName.isBlank()) return
        builder.addStatement("%L.id = R.id.%L", varName, idName)
    }

    private fun AnalyzedNode.idName(): String? {
        return node.attributes["android:id"]
            ?.removePrefix("@+id/")
            ?.removePrefix("@id/")
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
