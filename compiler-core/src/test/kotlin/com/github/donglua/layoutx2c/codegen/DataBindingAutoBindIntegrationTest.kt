package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 集成测试：验证带 @{} 表达式的 layout 端到端生成正确的 executePendingBindings() 代码
 */
class DataBindingAutoBindIntegrationTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzerV2()
    private val generator = BindingFacadeGeneratorV2(
        packageName = "com.example.generated",
        rPackageName = "com.example"
    )

    @Test
    fun `simple variable reference generates text assignment`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable name="title" type="java.lang.String" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <TextView
                        android:id="@+id/title_text"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@{title}" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "item_auto_bind")
        val analyzed = analyzer.analyze(tree.root)
        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_auto_bind",
            layoutResId = "R.layout.item_auto_bind",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        // executePendingBindings 应该包含绑定代码
        assertThat(generated).contains("titleText.text = title ?: \"\"")
        // 变量仍然是类型化的
        assertThat(generated).contains("public var title: String? = null")
    }

    @Test
    fun `property access generates nested binding`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable name="user" type="com.example.User" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <TextView
                        android:id="@+id/name_text"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@{user.name}" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "item_prop_bind")
        val analyzed = analyzer.analyze(tree.root)
        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_prop_bind",
            layoutResId = "R.layout.item_prop_bind",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        assertThat(generated).contains("nameText.text = user?.name ?: \"\"")
    }

    @Test
    fun `visibility binding generates boolean check`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable name="isVisible" type="boolean" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <TextView
                        android:id="@+id/content_view"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:visibility="@{isVisible}" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "item_visibility")
        val analyzed = analyzer.analyze(tree.root)
        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_visibility",
            layoutResId = "R.layout.item_visibility",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        assertThat(generated).contains("contentView.visibility = if (isVisible == true) View.VISIBLE else View.GONE")
    }

    @Test
    fun `complex expression does NOT generate binding - triggers fallback`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable name="count" type="int" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <TextView
                        android:id="@+id/count_text"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@{count + 1}" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "item_complex")
        val analyzed = analyzer.analyze(tree.root)
        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_complex",
            layoutResId = "R.layout.item_complex",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        // 复杂表达式不应该出现在 executePendingBindings 中
        assertThat(generated).doesNotContain("countText.text")
    }

    @Test
    fun `static attributes still emitted normally alongside binding attributes`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable name="title" type="java.lang.String" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <TextView
                        android:id="@+id/title_text"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@{title}" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "item_mixed")
        val analyzed = analyzer.analyze(tree.root)

        // @{title} 不应该在 supportedAttributes 中（不会被静态发射）
        fun findTextView(node: com.github.donglua.layoutx2c.analyzer.AnalyzedNode): com.github.donglua.layoutx2c.analyzer.AnalyzedNode? {
            if (node.node.attributes["android:id"] == "@+id/title_text") return node
            return node.children.firstNotNullOfOrNull { findTextView(it) }
        }

        val textViewNode = findTextView(analyzed)!!
        assertThat(textViewNode.supportedAttributes).doesNotContain("android:text")
        assertThat(textViewNode.dataBindingAttributes).contains("android:text")
    }
}
