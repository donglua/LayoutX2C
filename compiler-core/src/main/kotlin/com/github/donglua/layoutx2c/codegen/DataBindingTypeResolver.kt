package com.github.donglua.layoutx2c.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName

/**
 * 将 DataBinding 变量的类型字符串解析为 KotlinPoet TypeName。
 * 支持基本类型、完全限定类名、泛型等。
 */
object DataBindingTypeResolver {

    private val primitiveTypes = mapOf(
        "int" to Int::class.asTypeName(),
        "long" to Long::class.asTypeName(),
        "float" to Float::class.asTypeName(),
        "double" to Double::class.asTypeName(),
        "boolean" to Boolean::class.asTypeName(),
        "byte" to Byte::class.asTypeName(),
        "short" to Short::class.asTypeName(),
        "char" to Char::class.asTypeName(),
        "void" to Unit::class.asTypeName()
    )

    private val commonTypes = mapOf(
        "String" to ClassName("java.lang", "String"),
        "java.lang.String" to ClassName("java.lang", "String"),
        "Integer" to ClassName("java.lang", "Integer"),
        "java.lang.Integer" to ClassName("java.lang", "Integer"),
        "Long" to ClassName("java.lang", "Long"),
        "java.lang.Long" to ClassName("java.lang", "Long"),
        "Float" to ClassName("java.lang", "Float"),
        "java.lang.Float" to ClassName("java.lang", "Float"),
        "Double" to ClassName("java.lang", "Double"),
        "java.lang.Double" to ClassName("java.lang", "Double"),
        "Boolean" to ClassName("java.lang", "Boolean"),
        "java.lang.Boolean" to ClassName("java.lang", "Boolean"),
        "List" to ClassName("java.util", "List"),
        "java.util.List" to ClassName("java.util", "List"),
        "ArrayList" to ClassName("java.util", "ArrayList"),
        "java.util.ArrayList" to ClassName("java.util", "ArrayList"),
        "Map" to ClassName("java.util", "Map"),
        "java.util.Map" to ClassName("java.util", "Map"),
        "HashMap" to ClassName("java.util", "HashMap"),
        "java.util.HashMap" to ClassName("java.util", "HashMap")
    )

    /**
     * 解析类型字符串为 TypeName。
     * 支持：
     * - 基本类型：int, long, boolean 等
     * - 完全限定类名：java.lang.String, com.example.MyClass
     * - 简单类名（假设在 java.lang）：String, Integer
     * - 泛型：List<String>, Map<String, Integer>
     */
    fun resolve(typeString: String): TypeName {
        val trimmed = typeString.trim()

        // 检查基本类型
        primitiveTypes[trimmed]?.let { return it }

        // 检查常见类型
        commonTypes[trimmed]?.let { return it }

        // 处理泛型类型，如 List<String>
        if (trimmed.contains("<")) {
            return parseGenericType(trimmed)
        }

        // 处理完全限定类名
        return if (trimmed.contains(".")) {
            val parts = trimmed.split(".")
            val className = parts.last()
            val packageName = parts.dropLast(1).joinToString(".")
            ClassName(packageName, className)
        } else {
            // 假设是 java.lang 中的类
            ClassName("java.lang", trimmed)
        }
    }

    private fun parseGenericType(typeString: String): TypeName {
        val baseTypeEnd = typeString.indexOf("<")
        val baseType = typeString.substring(0, baseTypeEnd).trim()
        val typeArgs = typeString.substring(baseTypeEnd + 1, typeString.lastIndexOf(">")).trim()

        val baseTypeName = resolve(baseType)
        val argTypes = typeArgs.split(",").map { resolve(it.trim()) }

        return if (baseTypeName is ClassName) {
            baseTypeName.parameterizedBy(*argTypes.toTypedArray())
        } else {
            baseTypeName
        }
    }
}
