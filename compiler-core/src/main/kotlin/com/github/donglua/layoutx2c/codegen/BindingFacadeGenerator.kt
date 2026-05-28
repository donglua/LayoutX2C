package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.parser.DataBindingVariable
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

class BindingFacadeGenerator(
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

        val constructor = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
            .addParameter("root", ClassName("android.view", "View"))
            .apply {
                fields.forEach { field ->
                    addParameter(field.propertyName, field.viewClass)
                }
            }
            .build()

        val typeSpec = TypeSpec.classBuilder(bindingClassName)
            .primaryConstructor(constructor)
            .addProperty(
                PropertySpec.builder("root", ClassName("android.view", "View"))
                    .initializer("root")
                    .build()
            )
            .apply {
                fields.forEach { field ->
                    addProperty(
                        PropertySpec.builder(field.propertyName, field.viewClass)
                            .initializer(field.propertyName)
                            .build()
                    )
                }
                dataBindingProperties.forEach { variable ->
                    addProperty(
                        PropertySpec.builder(variable.name, ANY.copy(nullable = true))
                            .mutable(true)
                            .initializer("null")
                            .build()
                    )
                }
            }
            .addProperty(
                PropertySpec.builder("lifecycleOwner", ANY.copy(nullable = true))
                    .mutable(true)
                    .initializer("null")
                    .build()
            )
            .addFunction(FunSpec.builder("executePendingBindings").build())
            .addType(companionObject(layoutName, layoutResId, bindingClassName, fields, useFastPath))
            .build()

        return FileSpec.builder(packageName, bindingClassName)
            .addImport(rPackageName, "R")
            .addType(typeSpec)
            .build()
    }

    private fun companionObject(
        layoutName: String,
        layoutResId: String,
        bindingClassName: String,
        fields: List<BindingField>,
        useFastPath: Boolean
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
            .addFunction(inflateFun)
            .addFunction(bindFun)
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
            "return %N(%L)",
            bindingClassName,
            (listOf("rootView") + fields.map { it.propertyName }).joinToString(", ")
        )
        return builder.build()
    }

    private fun layoutNameToFacadeName(layoutName: String): String {
        return layoutName.toPascalCase() + "X2C"
    }

    private fun layoutNameToBindingClassName(layoutName: String): String {
        return layoutName.toPascalCase() + "X2CBinding"
    }

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

    private companion object {
        private val KOTLIN_KEYWORDS = setOf(
            "as",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "interface",
            "is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            "while"
        )
    }

    private fun String.toPascalCase(): String {
        return split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        }
    }
}
