package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.parser.DataBindingImport
import com.github.donglua.layoutx2c.parser.DataBindingVariable
import com.github.donglua.layoutx2c.parser.LayoutNodeType
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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

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
    fieldCollector: BindingFieldCollector? = null,
    private val bindingAdapters: List<BindingAdapterDescriptor> = emptyList()
) {
    private val fieldCollector: BindingFieldCollector = fieldCollector ?: BindingFieldCollector("$rPackageName.databinding")

    fun generate(
        analyzedRoot: AnalyzedNode,
        layoutName: String,
        layoutResId: String,
        useFastPath: Boolean,
        dataBindingVariables: List<DataBindingVariable> = emptyList(),
        dataBindingImports: List<DataBindingImport> = emptyList()
    ): FileSpec {
        val bindingClassName = layoutNameToBindingClassName(layoutName)
        val nativeBindingClassName = layoutNameToNativeBindingClassName(layoutName)
        val fields = when (val result = fieldCollector.collect(analyzedRoot.node)) {
            is BindingFieldResult.Success -> result.fields.sortedBy { it.propertyName }
            is BindingFieldResult.DuplicateIds -> emptyList()
        }
        val dataBindingProperties = dataBindingVariables.toCompatibilityProperties(fields)
        val nestedBindingFields = fields.filter { it.isNestedBinding }

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
            .superclass(nativeBindingClassName)
            .addSuperclassConstructorParameter(
                (listOf("null", "rootView", "0") + fields.map { it.propertyName }).joinToString(", ")
            )
            .apply {
                dataBindingProperties.forEach { variable ->
                    addFunction(
                        buildVariableSetter(
                            variable,
                            dirtyFlagBits.getValue(variable.name),
                            brIdProperties.getValue(variable.name).propertyName
                        )
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
            .addFunction(buildInvalidateAll(invalidateDirtyFlag, nestedBindingFields))
            .addFunction(buildHasPendingBindings(nestedBindingFields))
            .addFunction(buildOnFieldChange())
            .addFunction(buildExecuteBindings(analyzedRoot, fields, nestedBindingFields, dataBindingImports))
            .addFunction(buildSetupTwoWayBindings(analyzedRoot, fields))
            .apply {
                if (nestedBindingFields.isNotEmpty()) {
                    addFunction(buildSetupContainedBindings(nestedBindingFields))
                    addFunction(buildSetLifecycleOwner(nestedBindingFields))
                }
            }
            .addType(companionObject(layoutName, layoutResId, bindingClassName, fields, useFastPath, brIdProperties))
            .build()

        return FileSpec.builder(packageName, bindingClassName)
            .addImport(rPackageName, "R")
            .addType(typeSpec)
            .build()
    }

    private fun buildVariableSetter(
        variable: DataBindingVariable,
        dirtyFlag: Long,
        brIdPropertyName: String
    ): FunSpec {
        return FunSpec.builder(variable.name.toNativeSetterName())
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(variable.name, variable.toNativeSetterTypeName())
            .addCode(variableSetterBody(variable.name, dirtyFlag, brIdPropertyName))
            .build()
    }

    private fun variableSetterBody(variableName: String, dirtyFlag: Long, brIdPropertyName: String): CodeBlock {
        val backingFieldName = variableName.toNativeBackingFieldName()
        return CodeBlock.builder()
            .beginControlFlow("if (%L != %L)", backingFieldName, variableName)
            .addStatement("%L = %L", backingFieldName, variableName)
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
            val resolvedType = variable.toNativeSetterTypeName()
            builder.beginControlFlow("%L ->", brIdProperties.getValue(variable.name).propertyName)
            builder.addStatement("%L = variable as %T", variable.name, resolvedType)
            builder.addStatement("true")
            builder.endControlFlow()
        }
        builder.addStatement("else -> false")
        builder.endControlFlow()
        return builder.build()
    }

    private fun buildInvalidateAll(
        invalidateDirtyFlag: Long,
        nestedBindingFields: List<BindingField>
    ): FunSpec {
        val builder = FunSpec.builder("invalidateAll")
            .addModifiers(KModifier.OVERRIDE)
            .beginControlFlow("synchronized(this)")
            .addStatement("mDirtyFlags = mDirtyFlags or %LL", invalidateDirtyFlag)
            .endControlFlow()

        nestedBindingFields.forEach { field ->
            builder.addStatement("%L.invalidateAll()", field.propertyName)
        }

        return builder
            .addStatement("requestRebind()")
            .build()
    }

    private fun buildHasPendingBindings(nestedBindingFields: List<BindingField>): FunSpec {
        val builder = FunSpec.builder("hasPendingBindings")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Boolean::class)
            .beginControlFlow("synchronized(this)")
            .beginControlFlow("if (mDirtyFlags != 0L)")
            .addStatement("return true")
            .endControlFlow()
            .endControlFlow()

        nestedBindingFields.forEach { field ->
            builder.beginControlFlow("if (%L.hasPendingBindings())", field.propertyName)
            builder.addStatement("return true")
            builder.endControlFlow()
        }

        return builder.addStatement("return false").build()
    }

    private fun buildSetupContainedBindings(nestedBindingFields: List<BindingField>): FunSpec {
        val builder = FunSpec.builder("setupContainedBindings")
            .addModifiers(KModifier.PRIVATE)

        nestedBindingFields.forEach { field ->
            builder.addStatement("setContainedBinding(%L)", field.propertyName)
        }

        return builder.build()
    }

    private fun buildSetLifecycleOwner(nestedBindingFields: List<BindingField>): FunSpec {
        val lifecycleOwnerClass = ClassName("androidx.lifecycle", "LifecycleOwner")
        val builder = FunSpec.builder("setLifecycleOwner")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("lifecycleOwner", lifecycleOwnerClass.copy(nullable = true))
            .addStatement("super.setLifecycleOwner(lifecycleOwner)")

        nestedBindingFields.forEach { field ->
            builder.addStatement("%L.lifecycleOwner = lifecycleOwner", field.propertyName)
        }

        return builder.build()
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
        val sparseArrayClass = ClassName("android.util", "SparseArray").parameterizedBy(viewClass)

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
                        "val result = %N.inflateWithRefs(inflater.context, parent, attachToParent)",
                        layoutNameToFacadeName(layoutName)
                    )
                    addStatement("return bindFast(result.first, result.second)")
                } else {
                    addStatement("val root = inflater.inflate(%L, parent, attachToParent)", layoutResId)
                    addStatement("return bind(root)")
                }
            }
            .build()

        val bindFun = FunSpec.builder("bind")
            .addParameter("rootView", viewClass)
            .returns(ClassName(packageName, bindingClassName))
            .addCode(bindBody(bindingClassName, fields, refsVarName = null))
            .build()

        val bindFastFun = FunSpec.builder("bindFast")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("rootView", viewClass)
            .addParameter("refs", sparseArrayClass)
            .returns(ClassName(packageName, bindingClassName))
            .addCode(bindBody(bindingClassName, fields, refsVarName = "refs"))
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
            .apply {
                if (useFastPath) {
                    addFunction(bindFastFun)
                }
            }
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

    private fun bindBody(
        bindingClassName: String,
        fields: List<BindingField>,
        refsVarName: String?
    ): CodeBlock {
        val builder = CodeBlock.builder()
        fields.forEach { field ->
            if (field.isNestedBinding) {
                val rootVarName = "${field.propertyName}Root"
                if (refsVarName != null) {
                    builder.addStatement(
                        "val %L = %L.get(R.id.%L)\n⇥?: rootView.findViewById<View>(R.id.%L)\n⇥?: error(%S)⇤",
                        rootVarName,
                        refsVarName,
                        field.idName,
                        field.idName,
                        "Missing required view with ID: ${field.idName}"
                    )
                } else {
                    builder.addStatement(
                        "val %L = rootView.findViewById<View>(R.id.%L)\n⇥?: error(%S)⇤",
                        rootVarName,
                        field.idName,
                        "Missing required view with ID: ${field.idName}"
                    )
                }
                builder.addStatement(
                    "val %L: %T = %T.bind(%L)",
                    field.propertyName,
                    field.viewClass,
                    field.nestedBindingX2CClassName(),
                    rootVarName
                )
            } else if (refsVarName != null) {
                builder.addStatement(
                    "val %L = %L.get(R.id.%L) as? %T\n⇥?: rootView.findViewById<%T>(R.id.%L)\n⇥?: error(%S)⇤",
                    field.propertyName,
                    refsVarName,
                    field.idName,
                    field.viewClass,
                    field.viewClass,
                    field.idName,
                    "Missing required view with ID: ${field.idName}"
                )
            } else {
                builder.addStatement(
                    "val %L = rootView.findViewById<%T>(R.id.%L)\n⇥?: error(%S)⇤",
                    field.propertyName,
                    field.viewClass,
                    field.idName,
                    "Missing required view with ID: ${field.idName}"
                )
            }
        }
        builder.addStatement(
            "val binding = %N(%L)",
            bindingClassName,
            (listOf("rootView") + fields.map { it.propertyName }).joinToString(", ")
        )
        if (fields.any { it.isNestedBinding }) {
            builder.addStatement("binding.setupContainedBindings()")
        }
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

    private fun layoutNameToNativeBindingClassName(layoutName: String): ClassName {
        return ClassName("$rPackageName.databinding", layoutName.toPascalCase() + "Binding")
    }

    private fun BindingField.nestedBindingX2CClassName(): ClassName {
        val nestedLayoutName = nestedBindingLayoutName
            ?: error("Nested binding field $idName is missing layout name")
        return ClassName(packageName, nestedLayoutName.toPascalCase() + "X2CBinding")
    }

    /**
     * 构建 executeBindings() 方法，生成 @{} 表达式的绑定代码。
     */
    private fun buildExecuteBindings(
        analyzedRoot: AnalyzedNode,
        fields: List<BindingField>,
        nestedBindingFields: List<BindingField>,
        dataBindingImports: List<DataBindingImport>
    ): FunSpec {
        val builder = FunSpec.builder("executeBindings")
            .addModifiers(KModifier.PROTECTED, KModifier.OVERRIDE)
            .addStatement("val dirtyFlags: Long")
            .beginControlFlow("synchronized(this)")
            .addStatement("dirtyFlags = mDirtyFlags")
            .addStatement("mDirtyFlags = 0L")
            .endControlFlow()

        // 收集所有有 @{} 表达式的属性绑定
        val bindings = collectDataBindingExpressions(analyzedRoot, fields)
        val adapterBindings = collectBindingAdapterBindings(analyzedRoot, fields, dataBindingImports)
        val includeBindings = collectIncludeVariableBindings(analyzedRoot, fields, dataBindingImports)

        // 为每个绑定生成代码
        if (bindings.isNotEmpty() || adapterBindings.isNotEmpty() || includeBindings.isNotEmpty()) {
            builder.beginControlFlow("if (dirtyFlags != 0L)")
        } else if (nestedBindingFields.isEmpty()) {
            builder.beginControlFlow("if (dirtyFlags == 0L)")
            builder.addStatement("return")
            builder.endControlFlow()
        }

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

        adapterBindings.forEach { binding ->
            val methodClassName = binding.descriptor.methodClassName.toClassName()
            builder.addStatement(
                "%T.%L(%L%L)",
                methodClassName,
                binding.descriptor.methodName,
                binding.viewFieldName,
                binding.argumentCodes.joinToString(prefix = if (binding.argumentCodes.isEmpty()) "" else ", ")
            )
        }

        includeBindings.forEach { binding ->
            builder.addStatement(
                "%L.%L = %L",
                binding.bindingFieldName,
                binding.variableName,
                binding.expressionCode
            )
        }

        if (bindings.isNotEmpty() || adapterBindings.isNotEmpty() || includeBindings.isNotEmpty()) {
            builder.endControlFlow()
        }

        nestedBindingFields.forEach { field ->
            builder.addStatement("executeBindingsOn(%L)", field.propertyName)
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

    private fun collectBindingAdapterBindings(
        node: AnalyzedNode,
        fields: List<BindingField>,
        dataBindingImports: List<DataBindingImport>
    ): List<BindingAdapterBinding> {
        if (bindingAdapters.isEmpty()) return emptyList()
        val bindings = mutableListOf<BindingAdapterBinding>()

        fun traverse(analyzed: AnalyzedNode) {
            val rawId = analyzed.node.attributes["android:id"]
            val idName = rawId?.removePrefix("@+id/")?.removePrefix("@id/")
            val viewField = idName?.let { id -> fields.find { it.idName == id } }
            if (viewField != null) {
                bindingAdapters.forEach { descriptor ->
                    val presentAttrs = descriptor.attrs.filter { attrName ->
                        attrName in analyzed.dataBindingAttributes && analyzed.node.attributes.containsKey(attrName)
                    }
                    val shouldEmit = if (descriptor.requireAll) {
                        presentAttrs.size == descriptor.attrs.size
                    } else {
                        presentAttrs.isNotEmpty()
                    }
                    if (shouldEmit) {
                        val argumentCodes = descriptor.attrs.mapNotNull { attrName ->
                            val attrValue = analyzed.node.attributes[attrName] ?: return@mapNotNull null
                            dataBindingExpressionToCode(attrValue, dataBindingImports)?.toString()
                        }
                        if ((!descriptor.requireAll || argumentCodes.size == descriptor.attrs.size) && argumentCodes.isNotEmpty()) {
                            bindings += BindingAdapterBinding(
                                descriptor = descriptor,
                                viewFieldName = viewField.propertyName,
                                argumentCodes = argumentCodes
                            )
                        }
                    }
                }
            }

            analyzed.children.forEach { traverse(it) }
        }

        traverse(node)
        return bindings
    }

    private fun collectIncludeVariableBindings(
        node: AnalyzedNode,
        fields: List<BindingField>,
        dataBindingImports: List<DataBindingImport>
    ): List<IncludeVariableBinding> {
        val bindings = mutableListOf<IncludeVariableBinding>()

        fun traverse(analyzed: AnalyzedNode) {
            val includeNode = analyzed.node.nodeType as? LayoutNodeType.Include
            if (includeNode?.isDataBindingLayout == true) {
                val rawId = analyzed.node.attributes["android:id"]
                val idName = rawId?.removePrefix("@+id/")?.removePrefix("@id/")
                val field = fields.find { it.idName == idName }
                if (field != null) {
                    includeNode.includeAttributes.forEach { (attrName, attrValue) ->
                        val variableName = attrName.toIncludeVariableName() ?: return@forEach
                        val expressionCode = dataBindingExpressionToCode(attrValue, dataBindingImports) ?: return@forEach
                        bindings += IncludeVariableBinding(
                            bindingFieldName = field.propertyName,
                            variableName = variableName,
                            expressionCode = expressionCode
                        )
                    }
                }
            }

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

    private data class IncludeVariableBinding(
        val bindingFieldName: String,
        val variableName: String,
        val expressionCode: CodeBlock
    )

    private data class BindingAdapterBinding(
        val descriptor: BindingAdapterDescriptor,
        val viewFieldName: String,
        val argumentCodes: List<String>
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

    private fun String.toNativeSetterName(): String {
        return "set" + replaceFirstChar { it.uppercaseChar() }
    }

    private fun String.toNativeBackingFieldName(): String {
        return "m" + replaceFirstChar { it.uppercaseChar() }
    }

    private fun String.toIncludeVariableName(): String? {
        if (startsWith("android:") || startsWith("xmlns:") || startsWith("tools:")) return null
        val localName = substringAfter(':', this)
        if (localName == this || localName == "layout") return null
        return localName.takeIf { it.isKotlinIdentifier() }
    }

    private fun dataBindingExpressionToCode(
        attrValue: String,
        dataBindingImports: List<DataBindingImport>
    ): CodeBlock? {
        val expression = when {
            attrValue.startsWith("@{") && attrValue.endsWith("}") ->
                attrValue.substring(2, attrValue.length - 1).trim()
            attrValue.startsWith("@={") && attrValue.endsWith("}") ->
                attrValue.substring(3, attrValue.length - 1).trim()
            else -> return null
        }

        val importedExpression = qualifyImportedExpression(expression, dataBindingImports)
        return DataBindingExpressionParser.expressionToCode(importedExpression)?.let { CodeBlock.of("%L", it) }
    }

    private fun String.toClassName(): ClassName {
        val packageName = substringBeforeLast('.', missingDelimiterValue = "")
        val simpleName = substringAfterLast('.')
        return ClassName(packageName, simpleName)
    }

    private fun qualifyImportedExpression(expression: String, dataBindingImports: List<DataBindingImport>): String {
        val trimmed = expression.trim()
        val firstSegment = trimmed.substringBefore('.', missingDelimiterValue = "")
        if (firstSegment.isEmpty()) return trimmed
        val importedType = dataBindingImports.firstOrNull { dataBindingImport ->
            val importedName = dataBindingImport.alias ?: dataBindingImport.type.substringAfterLast('.')
            importedName == firstSegment
        } ?: return trimmed
        val suffix = trimmed.removePrefix(firstSegment)
        return importedType.type + suffix
    }

    private fun DataBindingVariable.toNativeSetterTypeName() =
        DataBindingTypeResolver.resolve(type).copy(nullable = !type.isPrimitiveDataBindingType())

    private fun String.isPrimitiveDataBindingType(): Boolean {
        return trim() in primitiveDataBindingTypes
    }

    private companion object {
        private val primitiveDataBindingTypes = setOf(
            "int",
            "long",
            "float",
            "double",
            "boolean",
            "byte",
            "short",
            "char"
        )

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
