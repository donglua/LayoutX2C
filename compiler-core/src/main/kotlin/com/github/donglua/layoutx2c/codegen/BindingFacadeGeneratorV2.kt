package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.parser.DataBindingVariable
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName

/**
 * 增强版 BindingFacadeGenerator，支持：
 * - 类型化的 DataBinding 变量（nullable 实际类型，而不是 Any?）
 * - 类型化的 lifecycleOwner（LifecycleOwner?）
 * - executePendingBindings() 占位实现
 *
 * 变量作为 mutable 属性（不在构造函数中），初始值为 null。
 * 这样 bind() 方法不需要传变量值，与 V1 行为一致。
 */
class BindingFacadeGeneratorV2(
    private val packageName: String,
    private val rPackageName: String,
    private val fieldCollector: BindingFieldCollector = BindingFieldCollector()
) {
    fun generate(
        analyzedRoot: AnalyzedNode,
        layoutName: String,
        layoutResId: String,
        useFastPath: Boolean,
        dataBindingVariables: List<DataBindingVariable> = emptyList()
    ): FileSpec {
        val bindingClassName = layoutNameToBindingClassName(layoutName)
        val fields = when (val result = fieldCollector.collect(analyzedRoot.node)) {
            is BindingFieldResult.Success -> result.fields
            is BindingFieldResult.DuplicateIds -> emptyList()
        }
        val dataBindingProperties = dataBindingVariables.toCompatibilityProperties(fields)

        // 构造函数只包含 root + view 字段（与 V1 一致）
        val constructor = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
            .addParameter("rootView", ClassName("android.view", "View"))
            .apply {
                fields.forEach { field ->
                    addParameter(field.propertyName, field.viewClass)
                }
            }
            .build()
        val dirtyFlagBits = dataBindingProperties
            .mapIndexed { index, variable -> variable.name to (1L shl index) }
            .toMap()
        val brIdProperties = dataBindingProperties
            .mapIndexed { index, variable -> variable.name to BrIdProperty(variable.name.toBrIdPropertyName(index), index + 1) }
            .toMap()
        val invalidateDirtyFlag = dirtyFlagBits.values.fold(1L) { acc, flag -> acc or flag }

        val typeSpec = TypeSpec.classBuilder(bindingClassName)
            .primaryConstructor(constructor)
            .superclass(ClassName("androidx.databinding", "ViewDataBinding"))
            .addSuperclassConstructorParameter("null, rootView, 0")
            .apply {
                // View 字段（非空，来自构造函数）
                fields.forEach { field ->
                    addProperty(
                        PropertySpec.builder(field.propertyName, field.viewClass)
                            .initializer(field.propertyName)
                            .build()
                    )
                }
                // DataBinding 变量（nullable 实际类型，mutable，初始 null）
                dataBindingProperties.forEach { variable ->
                    val resolvedType = DataBindingTypeResolver.resolve(variable.type)
                    val propertyType = resolvedType.copy(nullable = true)
                    addProperty(
                        PropertySpec.builder(variable.name, propertyType)
                            .mutable(true)
                            .addAnnotation(
                                AnnotationSpec.builder(ClassName("androidx.databinding", "Bindable"))
                                    .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                                    .build()
                            )
                            .initializer("null")
                            .setter(
                                FunSpec.setterBuilder()
                                    .addParameter("value", propertyType)
                                    .addCode(
                                        variableSetterBody(
                                            variable.name,
                                            dirtyFlagBits.getValue(variable.name),
                                            brIdProperties.getValue(variable.name).propertyName
                                        )
                                    )
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .addProperty(
                PropertySpec.builder(
                    "mDirtyFlags",
                    Long::class
                ).addModifiers(KModifier.PRIVATE)
                    .mutable(true)
                    .initializer("0L")
                    .build()
            )
            .addFunction(buildSetVariable(dataBindingProperties, brIdProperties))
            .addFunction(buildInvalidateAll(invalidateDirtyFlag))
            .addFunction(buildHasPendingBindings())
            .addFunction(buildOnFieldChange())
            .addFunction(buildExecuteBindings(analyzedRoot, fields))
            .addFunction(buildSetupTwoWayBindings(analyzedRoot, fields))
            .addType(companionObject(layoutName, layoutResId, bindingClassName, fields, useFastPath, brIdProperties))
            .build()

        return FileSpec.builder(packageName, bindingClassName)
            .addImport(rPackageName, "R")
            .addType(typeSpec)
            .build()
    }

    private fun variableSetterBody(variableName: String, dirtyFlag: Long, brIdPropertyName: String): CodeBlock {
        return CodeBlock.builder()
            .beginControlFlow("if (field != value)")
            .addStatement("field = value")
            .beginControlFlow("synchronized(this)")
            .addStatement("mDirtyFlags = mDirtyFlags or %LL", dirtyFlag)
            .endControlFlow()
            .addStatement("notifyPropertyChanged(%L)", brIdPropertyName)
            .addStatement("requestRebind()")
            .endControlFlow()
            .build()
    }

    private fun buildSetVariable(
        dataBindingProperties: List<DataBindingVariable>,
        brIdProperties: Map<String, BrIdProperty>
    ): FunSpec {
        val builder = FunSpec.builder("setVariable")
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "UNCHECKED_CAST")
                    .build()
            )
            .addParameter("variableId", Int::class)
            .addParameter("variable", Any::class.asTypeName().copy(nullable = true))
            .returns(Boolean::class)

        if (dataBindingProperties.isEmpty()) {
            return builder.addStatement("return false").build()
        }

        builder.beginControlFlow("return when (variableId)")
        dataBindingProperties.forEach { variable ->
            val resolvedType = DataBindingTypeResolver.resolve(variable.type).copy(nullable = true)
            builder.beginControlFlow("%L ->", brIdProperties.getValue(variable.name).propertyName)
            builder.addStatement("%L = variable as %T", variable.name, resolvedType)
            builder.addStatement("true")
            builder.endControlFlow()
        }
        builder.addStatement("else -> false")
        builder.endControlFlow()
        return builder.build()
    }

    private fun buildInvalidateAll(invalidateDirtyFlag: Long): FunSpec {
        return FunSpec.builder("invalidateAll")
            .addModifiers(KModifier.OVERRIDE)
            .beginControlFlow("synchronized(this)")
            .addStatement("mDirtyFlags = mDirtyFlags or %LL", invalidateDirtyFlag)
            .endControlFlow()
            .addStatement("requestRebind()")
            .build()
    }

    private fun buildHasPendingBindings(): FunSpec {
        return FunSpec.builder("hasPendingBindings")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Boolean::class)
            .beginControlFlow("synchronized(this)")
            .addStatement("return mDirtyFlags != 0L")
            .endControlFlow()
            .build()
    }

    private fun buildOnFieldChange(): FunSpec {
        return FunSpec.builder("onFieldChange")
            .addModifiers(KModifier.PROTECTED, KModifier.OVERRIDE)
            .addParameter("localFieldId", Int::class)
            .addParameter("obj", Any::class.asTypeName().copy(nullable = true))
            .addParameter("fieldId", Int::class)
            .returns(Boolean::class)
            .addStatement("return false")
            .build()
    }

    private fun companionObject(
        layoutName: String,
        layoutResId: String,
        bindingClassName: String,
        fields: List<BindingField>,
        useFastPath: Boolean,
        brIdProperties: Map<String, BrIdProperty>
    ): TypeSpec {
        val inflaterClass = ClassName("android.view", "LayoutInflater")
        val viewGroupClass = ClassName("android.view", "ViewGroup")
        val viewClass = ClassName("android.view", "View")

        val inflateFun = FunSpec.builder("inflate")
            .addParameter("inflater", inflaterClass)
            .addParameter(
                ParameterSpec.builder("parent", viewGroupClass.copy(nullable = true))
                    .defaultValue("null")
                    .build()
            )
            .addParameter(
                ParameterSpec.builder("attachToParent", Boolean::class)
                    .defaultValue("false")
                    .build()
            )
            .returns(ClassName(packageName, bindingClassName))
            .apply {
                if (useFastPath) {
                    addStatement(
                        "val root = %N.inflate(inflater.context, parent, attachToParent)",
                        layoutNameToFacadeName(layoutName)
                    )
                } else {
                    addStatement("val root = inflater.inflate(%L, parent, attachToParent)", layoutResId)
                }
                addStatement("return bind(root)")
            }
            .build()

        val bindFun = FunSpec.builder("bind")
            .addParameter("rootView", viewClass)
            .returns(ClassName(packageName, bindingClassName))
            .addCode(bindBody(bindingClassName, fields))
            .build()

        return TypeSpec.companionObjectBuilder()
            .apply {
                brIdProperties.forEach { (variableName, property) ->
                    addProperty(
                        PropertySpec.builder(property.propertyName, Int::class)
                            .addModifiers(KModifier.PRIVATE)
                            .initializer("resolveBrId(%S, %L)", variableName, property.fallbackId)
                            .build()
                    )
                }
                if (brIdProperties.isNotEmpty()) {
                    addFunction(buildResolveBrId())
                }
            }
            .addFunction(inflateFun)
            .addFunction(bindFun)
            .build()
    }

    private fun buildResolveBrId(): FunSpec {
        return FunSpec.builder("resolveBrId")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("name", String::class)
            .addParameter("fallbackId", Int::class)
            .returns(Int::class)
            .beginControlFlow("return try")
            .addStatement("%T.forName(%S).getField(name).getInt(null)", Class::class, "$rPackageName.BR")
            .nextControlFlow("catch (_: Throwable)")
            .addStatement("fallbackId")
            .endControlFlow()
            .build()
    }

    private fun bindBody(bindingClassName: String, fields: List<BindingField>): CodeBlock {
        val builder = CodeBlock.builder()
        fields.forEach { field ->
            builder.addStatement(
                "val %L = rootView.findViewById<%T>(R.id.%L)\n⇥?: error(%S)⇤",
                field.propertyName,
                field.viewClass,
                field.idName,
                "Missing required view with ID: ${field.idName}"
            )
        }
        builder.addStatement(
            "val binding = %N(%L)",
            bindingClassName,
            (listOf("rootView") + fields.map { it.propertyName }).joinToString(", ")
        )
        builder.addStatement("binding.setRootTag(rootView)")
        builder.addStatement("binding.setupTwoWayBindings()")
        builder.addStatement("binding.invalidateAll()")
        builder.addStatement("return binding")
        return builder.build()
    }

    private fun layoutNameToFacadeName(layoutName: String): String {
        return layoutName.toPascalCase() + "X2C"
    }

    private fun layoutNameToBindingClassName(layoutName: String): String {
        return layoutName.toPascalCase() + "X2CBinding"
    }

    /**
     * 构建 executeBindings() 方法，生成 @{} 表达式的绑定代码。
     */
    private fun buildExecuteBindings(
        analyzedRoot: AnalyzedNode,
        fields: List<BindingField>
    ): FunSpec {
        val builder = FunSpec.builder("executeBindings")
            .addModifiers(KModifier.PROTECTED, KModifier.OVERRIDE)
            .addStatement("val dirtyFlags: Long")
            .beginControlFlow("synchronized(this)")
            .addStatement("dirtyFlags = mDirtyFlags")
            .addStatement("mDirtyFlags = 0L")
            .endControlFlow()
            .beginControlFlow("if (dirtyFlags == 0L)")
            .addStatement("return")
            .endControlFlow()

        // 收集所有有 @{} 表达式的属性绑定
        val bindings = collectDataBindingExpressions(analyzedRoot, fields)

        // 为每个绑定生成代码
        bindings.forEach { binding ->
            val code = DataBindingAttributeMapper.generateBindingCode(
                viewFieldName = binding.viewFieldName,
                attrName = binding.attributeName,
                variableName = binding.variableName,
                propertyPath = binding.propertyPath
            )
            if (code != null) {
                builder.addStatement(code)
            }
        }

        return builder.build()
    }

    /**
     * 构建 setupTwoWayBindings() 方法，给视图安装反向监听器，
     * 把视图变化回写到 binding 类的变量属性（如 userName: String?）。
     *
     * 仅对 [DataBindingAttributeMapper.isTwoWayBindingSupported] 白名单内的
     * (viewType, attrName) 组合生效。其他双向绑定属性目前不会生成监听器代码。
     */
    private fun buildSetupTwoWayBindings(
        analyzedRoot: AnalyzedNode,
        fields: List<BindingField>
    ): FunSpec {
        val builder = FunSpec.builder("setupTwoWayBindings")

        val bindings = collectDataBindingExpressions(analyzedRoot, fields)
            .filter { it.isTwoWay }

        bindings.forEach { binding ->
            val code = DataBindingAttributeMapper.generateTwoWayListenerCode(
                viewTagName = binding.viewTagName,
                viewFieldName = binding.viewFieldName,
                attrName = binding.attributeName,
                variableName = binding.variableName,
                propertyPath = binding.propertyPath
            )
            if (code != null) {
                builder.addCode("%L\n", code)
            }
        }

        return builder.build()
    }

    /**
     * 收集所有有 @{} / @={} 表达式的属性绑定。
     */
    private fun collectDataBindingExpressions(
        node: AnalyzedNode,
        fields: List<BindingField>
    ): List<DataBindingExpressionBinding> {
        val bindings = mutableListOf<DataBindingExpressionBinding>()

        fun traverse(analyzed: AnalyzedNode) {
            // 只看 V2 Analyzer 标记为 dataBindingAttributes 的属性
            analyzed.dataBindingAttributes.forEach { attrName ->
                if (!DataBindingAttributeMapper.isSupportedAttribute(attrName)) return@forEach

                val attrValue = analyzed.node.attributes[attrName] ?: return@forEach
                val expr = parseDataBindingExpression(attrValue) ?: return@forEach

                // 找到对应的 View 字段（按 android:id 匹配）
                val rawId = analyzed.node.attributes["android:id"] ?: return@forEach
                val idName = rawId.removePrefix("@+id/").removePrefix("@id/")
                val viewField = fields.find { it.idName == idName } ?: return@forEach

                bindings.add(
                    DataBindingExpressionBinding(
                        viewFieldName = viewField.propertyName,
                        attributeName = attrName,
                        variableName = expr.first,
                        propertyPath = expr.second,
                        isTwoWay = attrName in analyzed.twoWayBindingAttributes,
                        viewTagName = analyzed.node.tagName
                    )
                )
            }

            // 递归处理子节点
            analyzed.children.forEach { traverse(it) }
        }

        traverse(node)
        return bindings
    }

    /**
     * 解析 @{variable} / @{variable.property} / @={variable} / @={variable.property} 表达式。
     * 返回 Pair(variableName, propertyPath) 或 null 如果格式不正确。
     */
    private fun parseDataBindingExpression(attrValue: String): Pair<String, String?>? {
        val expr = when {
            attrValue.startsWith("@={") && attrValue.endsWith("}") ->
                attrValue.substring(3, attrValue.length - 1).trim()
            attrValue.startsWith("@{") && attrValue.endsWith("}") ->
                attrValue.substring(2, attrValue.length - 1).trim()
            else -> return null
        }

        // 简单变量引用或属性访问
        val parts = expr.split(".")
        if (parts.isEmpty()) return null

        val variableName = parts[0]
        val propertyPath = if (parts.size > 1) parts.drop(1).joinToString(".") else null

        return variableName to propertyPath
    }

    /**
     * 数据绑定表达式绑定信息。
     */
    private data class DataBindingExpressionBinding(
        val viewFieldName: String,
        val attributeName: String,
        val variableName: String,
        val propertyPath: String?,
        val isTwoWay: Boolean = false,
        val viewTagName: String = ""
    )

    private data class BrIdProperty(
        val propertyName: String,
        val fallbackId: Int
    )


    private fun List<DataBindingVariable>.toCompatibilityProperties(fields: List<BindingField>): List<DataBindingVariable> {
        val reservedNames = mutableSetOf(
            "root",
            "Companion",
            "lifecycleOwner",
            "executePendingBindings",
            "inflate",
            "bind"
        )
        reservedNames += fields.map { it.propertyName }

        return filter { variable ->
            variable.name.isKotlinIdentifier() && reservedNames.add(variable.name)
        }
    }

    private fun String.isKotlinIdentifier(): Boolean {
        if (isEmpty()) return false
        if (all { it == '_' }) return false
        if (this in KOTLIN_KEYWORDS) return false
        if (!first().isLetter() && first() != '_') return false
        return drop(1).all { it.isLetterOrDigit() || it == '_' }
    }

    private fun String.toBrIdPropertyName(index: Int): String {
        val upper = buildString {
            this@toBrIdPropertyName.forEachIndexed { charIndex, char ->
                if (charIndex > 0 && char.isUpperCase()) append('_')
                append(char.uppercaseChar())
            }
        }
        return "${upper}_$index"
    }

    private companion object {
        private val KOTLIN_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
            "in", "interface", "is", "null", "object", "package", "return", "super", "this",
            "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while"
        )
    }

    private fun String.toPascalCase(): String {
        return split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        }
    }
}
