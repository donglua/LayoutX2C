package com.github.donglua.layoutx2c.codegen

/**
 * 解析和分析 DataBinding 表达式。
 * 支持简单的表达式，如：
 * - @{variable}
 * - @{variable.property}
 * - @{variable != null ? variable : "default"}
 * - @={variable}（双向绑定）
 */
sealed class DataBindingExpression {
    data class VariableReference(val variableName: String) : DataBindingExpression()
    data class PropertyAccess(val variableName: String, val propertyPath: String) : DataBindingExpression()
    data class TernaryExpression(val condition: String, val trueExpr: String, val falseExpr: String) : DataBindingExpression()
    data class TwoWayBinding(val expression: DataBindingExpression) : DataBindingExpression()
    data class ComplexExpression(val expression: String) : DataBindingExpression()
    object NoExpression : DataBindingExpression()
}

object DataBindingExpressionParser {

    /**
     * 从属性值中提取 DataBinding 表达式。
     * 返回表达式内容（不包括 @{} 或 @={} 包装）
     */
    fun extractExpression(attrValue: String): String? {
        return when {
            attrValue.startsWith("@{") && attrValue.endsWith("}") -> {
                attrValue.substring(2, attrValue.length - 1)
            }
            attrValue.startsWith("@={") && attrValue.endsWith("}") -> {
                attrValue.substring(3, attrValue.length - 1)
            }
            else -> null
        }
    }

    /**
     * 判断属性值是否包含 DataBinding 表达式
     */
    fun hasExpression(attrValue: String): Boolean {
        return attrValue.contains("@{") || attrValue.contains("@={")
    }

    /**
     * 判断是否为双向绑定
     */
    fun isTwoWayBinding(attrValue: String): Boolean {
        return attrValue.startsWith("@={") && attrValue.endsWith("}")
    }

    /**
     * 解析表达式为结构化形式
     */
    fun parse(attrValue: String): DataBindingExpression {
        val expression = extractExpression(attrValue) ?: return DataBindingExpression.NoExpression
        val isTwoWay = isTwoWayBinding(attrValue)

        val parsed = parseExpression(expression)
        return if (isTwoWay && parsed != DataBindingExpression.NoExpression) {
            DataBindingExpression.TwoWayBinding(parsed)
        } else {
            parsed
        }
    }

    private fun parseExpression(expr: String): DataBindingExpression {
        val trimmed = expr.trim()

        // 检查三元表达式
        if (trimmed.contains("?") && trimmed.contains(":")) {
            return parseTernaryExpression(trimmed)
        }

        // 检查属性访问
        if (trimmed.contains(".")) {
            val parts = trimmed.split(".", limit = 2)
            if (isValidVariableName(parts[0])) {
                return DataBindingExpression.PropertyAccess(parts[0], parts[1])
            }
        }

        // 检查简单变量引用
        if (isValidVariableName(trimmed)) {
            return DataBindingExpression.VariableReference(trimmed)
        }

        // 复杂表达式
        return DataBindingExpression.ComplexExpression(trimmed)
    }

    private fun parseTernaryExpression(expr: String): DataBindingExpression {
        val questionIdx = expr.indexOf("?")
        val colonIdx = expr.lastIndexOf(":")

        if (questionIdx > 0 && colonIdx > questionIdx) {
            val condition = expr.substring(0, questionIdx).trim()
            val trueExpr = expr.substring(questionIdx + 1, colonIdx).trim()
            val falseExpr = expr.substring(colonIdx + 1).trim()
            return DataBindingExpression.TernaryExpression(condition, trueExpr, falseExpr)
        }

        return DataBindingExpression.ComplexExpression(expr)
    }

    private fun isValidVariableName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (!name.first().isLetter() && name.first() != '_') return false
        return name.drop(1).all { it.isLetterOrDigit() || it == '_' }
    }

    /**
     * 从表达式中提取所有引用的变量名
     */
    fun extractVariableReferences(expr: DataBindingExpression): Set<String> {
        return when (expr) {
            is DataBindingExpression.VariableReference -> setOf(expr.variableName)
            is DataBindingExpression.PropertyAccess -> setOf(expr.variableName)
            is DataBindingExpression.TernaryExpression -> {
                val refs = mutableSetOf<String>()
                extractVariablesFromString(expr.condition, refs)
                extractVariablesFromString(expr.trueExpr, refs)
                extractVariablesFromString(expr.falseExpr, refs)
                refs
            }
            is DataBindingExpression.TwoWayBinding -> extractVariableReferences(expr.expression)
            is DataBindingExpression.ComplexExpression -> {
                val refs = mutableSetOf<String>()
                extractVariablesFromString(expr.expression, refs)
                refs
            }
            DataBindingExpression.NoExpression -> emptySet()
        }
    }

    private fun extractVariablesFromString(str: String, refs: MutableSet<String>) {
        val pattern = Regex("""[a-zA-Z_][a-zA-Z0-9_]*""")
        pattern.findAll(str).forEach { match ->
            refs.add(match.value)
        }
    }
}
