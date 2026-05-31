package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.parser.DataBindingVariable
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 集成测试：验证 V2 generator 端到端行为
 */
class DataBindingEnhancementsIntegrationTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzerV2()
    private val generator = BindingFacadeGeneratorV2(
        packageName = "com.example.generated",
        rPackageName = "com.example"
    )

    @Test
    fun `generates typed binding facade with multiple variable types`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable name="title" type="java.lang.String" />
                    <variable name="count" type="int" />
                    <variable name="viewModel" type="com.example.ItemViewModel" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <TextView
                        android:id="@+id/title_text"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "item_typed")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_typed",
            layoutResId = "R.layout.item_typed",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        // 类型化 nullable 变量
        assertThat(generated).contains("public var title: String? = null")
        assertThat(generated).contains("public var count: Int? = null")
        assertThat(generated).contains("public var viewModel: ItemViewModel? = null")

        // lifecycleOwner 类型化
        assertThat(generated).contains("public var lifecycleOwner: LifecycleOwner? = null")

        // View 字段非空
        assertThat(generated).contains("public val titleText: TextView")

        // 不包含 Any?
        assertThat(generated).doesNotContain("Any?")
    }

    @Test
    fun `type resolver handles all common patterns`() {
        assertThat(DataBindingTypeResolver.resolve("int").toString()).isEqualTo("kotlin.Int")
        assertThat(DataBindingTypeResolver.resolve("boolean").toString()).isEqualTo("kotlin.Boolean")
        assertThat(DataBindingTypeResolver.resolve("java.lang.String").toString()).isEqualTo("kotlin.String")
        assertThat(DataBindingTypeResolver.resolve("com.example.Foo").toString()).isEqualTo("com.example.Foo")

        val listType = DataBindingTypeResolver.resolve("List<String>").toString()
        assertThat(listType).contains("kotlin.collections.List")
        assertThat(listType).contains("kotlin.String")
    }

    @Test
    fun `expression parser identifies expression types`() {
        // 简单变量引用
        val varRef = DataBindingExpressionParser.parse("@{title}")
        assertThat(varRef).isInstanceOf(DataBindingExpression.VariableReference::class.java)

        // 属性访问
        val propAccess = DataBindingExpressionParser.parse("@{user.name}")
        assertThat(propAccess).isInstanceOf(DataBindingExpression.PropertyAccess::class.java)

        // 双向绑定
        val twoWay = DataBindingExpressionParser.parse("@={text}")
        assertThat(twoWay).isInstanceOf(DataBindingExpression.TwoWayBinding::class.java)

        // 非表达式
        val noExpr = DataBindingExpressionParser.parse("plain text")
        assertThat(noExpr).isEqualTo(DataBindingExpression.NoExpression)
    }

    @Test
    fun `full pipeline with generic type variables`() {
        val variables = listOf(
            DataBindingVariable("items", "java.util.List<java.lang.String>"),
            DataBindingVariable("map", "java.util.Map<java.lang.String, java.lang.Integer>")
        )

        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "item_generics")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_generics",
            layoutResId = "R.layout.item_generics",
            useFastPath = true,
            dataBindingVariables = variables
        ).toString()

        assertThat(generated).contains("public var items: List<String>? = null")
        assertThat(generated).contains("public var map: Map<String, Int>? = null")
    }
}
