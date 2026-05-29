package com.github.donglua.layoutx2c.codegen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataBindingTypeResolverTest {

    @Test
    fun `resolves primitive types`() {
        assertThat(DataBindingTypeResolver.resolve("int").toString()).isEqualTo("kotlin.Int")
        assertThat(DataBindingTypeResolver.resolve("long").toString()).isEqualTo("kotlin.Long")
        assertThat(DataBindingTypeResolver.resolve("boolean").toString()).isEqualTo("kotlin.Boolean")
        assertThat(DataBindingTypeResolver.resolve("float").toString()).isEqualTo("kotlin.Float")
    }

    @Test
    fun `resolves common types`() {
        assertThat(DataBindingTypeResolver.resolve("String").toString()).isEqualTo("java.lang.String")
        assertThat(DataBindingTypeResolver.resolve("java.lang.String").toString()).isEqualTo("java.lang.String")
        assertThat(DataBindingTypeResolver.resolve("Integer").toString()).isEqualTo("java.lang.Integer")
    }

    @Test
    fun `resolves fully qualified class names`() {
        assertThat(DataBindingTypeResolver.resolve("com.example.MyClass").toString()).isEqualTo("com.example.MyClass")
    }

    @Test
    fun `resolves generic types`() {
        val listStringType = DataBindingTypeResolver.resolve("List<String>").toString()
        assertThat(listStringType).contains("java.util.List")
        assertThat(listStringType).contains("java.lang.String")
    }

    @Test
    fun `resolves nested generic types`() {
        val mapType = DataBindingTypeResolver.resolve("Map<String, Integer>").toString()
        assertThat(mapType).contains("java.util.Map")
        assertThat(mapType).contains("java.lang.String")
        assertThat(mapType).contains("java.lang.Integer")
    }
}

class DataBindingExpressionParserTest {

    @Test
    fun `extracts simple variable reference`() {
        val expr = DataBindingExpressionParser.extractExpression("@{title}")
        assertThat(expr).isEqualTo("title")
    }

    @Test
    fun `extracts property access expression`() {
        val expr = DataBindingExpressionParser.extractExpression("@{user.name}")
        assertThat(expr).isEqualTo("user.name")
    }

    @Test
    fun `extracts two-way binding expression`() {
        val expr = DataBindingExpressionParser.extractExpression("@={text}")
        assertThat(expr).isEqualTo("text")
    }

    @Test
    fun `detects expressions`() {
        assertThat(DataBindingExpressionParser.hasExpression("@{title}")).isTrue()
        assertThat(DataBindingExpressionParser.hasExpression("@={text}")).isTrue()
        assertThat(DataBindingExpressionParser.hasExpression("plain text")).isFalse()
    }

    @Test
    fun `identifies two-way bindings`() {
        assertThat(DataBindingExpressionParser.isTwoWayBinding("@={text}")).isTrue()
        assertThat(DataBindingExpressionParser.isTwoWayBinding("@{text}")).isFalse()
    }

    @Test
    fun `parses variable reference`() {
        val parsed = DataBindingExpressionParser.parse("@{title}")
        assertThat(parsed).isInstanceOf(DataBindingExpression.VariableReference::class.java)
        assertThat((parsed as DataBindingExpression.VariableReference).variableName).isEqualTo("title")
    }

    @Test
    fun `parses property access`() {
        val parsed = DataBindingExpressionParser.parse("@{user.name}")
        assertThat(parsed).isInstanceOf(DataBindingExpression.PropertyAccess::class.java)
        val propAccess = parsed as DataBindingExpression.PropertyAccess
        assertThat(propAccess.variableName).isEqualTo("user")
        assertThat(propAccess.propertyPath).isEqualTo("name")
    }

    @Test
    fun `parses ternary expression`() {
        val parsed = DataBindingExpressionParser.parse("@{user != null ? user.name : \"Unknown\"}")
        assertThat(parsed).isInstanceOf(DataBindingExpression.TernaryExpression::class.java)
    }

    @Test
    fun `parses two-way binding`() {
        val parsed = DataBindingExpressionParser.parse("@={text}")
        assertThat(parsed).isInstanceOf(DataBindingExpression.TwoWayBinding::class.java)
        val twoWay = parsed as DataBindingExpression.TwoWayBinding
        assertThat(twoWay.expression).isInstanceOf(DataBindingExpression.VariableReference::class.java)
    }

    @Test
    fun `extracts variable references from expression`() {
        val expr = DataBindingExpressionParser.parse("@{user.name}")
        val refs = DataBindingExpressionParser.extractVariableReferences(expr)
        assertThat(refs).contains("user")
    }

    @Test
    fun `extracts multiple variable references from ternary`() {
        val expr = DataBindingExpressionParser.parse("@{user != null ? user.name : default}")
        val refs = DataBindingExpressionParser.extractVariableReferences(expr)
        assertThat(refs).containsAtLeast("user", "default")
    }
}
