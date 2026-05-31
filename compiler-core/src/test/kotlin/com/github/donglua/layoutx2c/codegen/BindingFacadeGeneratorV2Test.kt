package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.parser.DataBindingVariable
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * V2 BindingFacadeGenerator 测试：验证类型化变量输出
 */
class BindingFacadeGeneratorV2Test {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzerV2()
    private val generator = BindingFacadeGeneratorV2(
        packageName = "com.example.generated",
        rPackageName = "com.example"
    )

    @Test
    fun `generates typed nullable variables instead of Any`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable
                        name="title"
                        type="java.lang.String" />
                    <variable
                        name="vm"
                        type="com.example.ItemViewModel" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
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

        // 变量是 nullable 实际类型
        assertThat(generated).contains("public var title: String? = null")
        assertThat(generated).contains("public var vm: ItemViewModel? = null")
        // 不再是 Any?
        assertThat(generated).doesNotContain("public var title: Any?")
        assertThat(generated).doesNotContain("public var vm: Any?")
        // lifecycleOwner 是 LifecycleOwner?
        assertThat(generated).contains("public var lifecycleOwner: LifecycleOwner? = null")
        assertThat(generated).doesNotContain("public var lifecycleOwner: Any?")
    }

    @Test
    fun `generates primitive typed variables`() {
        val variables = listOf(
            DataBindingVariable("count", "int"),
            DataBindingVariable("isVisible", "boolean"),
            DataBindingVariable("ratio", "float")
        )

        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </layout>
        """.trimIndent()
        val tree = parser.parse(xml, "item_primitives")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_primitives",
            layoutResId = "R.layout.item_primitives",
            useFastPath = true,
            dataBindingVariables = variables
        ).toString()

        // 基本类型也是 nullable（因为初始值为 null）
        assertThat(generated).contains("public var count: Int? = null")
        assertThat(generated).contains("public var isVisible: Boolean? = null")
        assertThat(generated).contains("public var ratio: Float? = null")
    }

    @Test
    fun `bind method does not include variable params`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable
                        name="title"
                        type="java.lang.String" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:id="@+id/title_text"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
            </layout>
        """.trimIndent()
        val tree = parser.parse(xml, "item_bind")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_bind",
            layoutResId = "R.layout.item_bind",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        // bind 方法构造 binding 实例，调用 setupTwoWayBindings，然后返回
        assertThat(generated).contains("val binding = ItemBindX2CBinding(rootView, titleText)")
        assertThat(generated).contains("binding.setupTwoWayBindings()")
        assertThat(generated).contains("return binding")
        // 构造函数不包含变量参数
        assertThat(generated).contains("public class ItemBindX2CBinding private constructor(")
        assertThat(generated).contains("public val root: View,")
        assertThat(generated).contains("public val titleText: TextView,")
        assertThat(generated).doesNotContain("private constructor(\n    root: View,\n    titleText: TextView,\n    title:")
    }

    @Test
    fun `fast path inflate uses X2C facade`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </layout>
        """.trimIndent()
        val tree = parser.parse(xml, "item_fast")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_fast",
            layoutResId = "R.layout.item_fast",
            useFastPath = true,
            dataBindingVariables = emptyList()
        ).toString()

        assertThat(generated).contains("val root = ItemFastX2C.inflate(inflater.context, parent, attachToParent)")
    }

    @Test
    fun `fallback inflate uses LayoutInflater`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </layout>
        """.trimIndent()
        val tree = parser.parse(xml, "item_fb")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_fb",
            layoutResId = "R.layout.item_fb",
            useFastPath = false,
            dataBindingVariables = emptyList()
        ).toString()

        assertThat(generated).contains("val root = inflater.inflate(R.layout.item_fb, parent, attachToParent)")
    }

    @Test
    fun `skips conflicting variable names`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:id="@+id/title"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
            </layout>
        """.trimIndent()
        val tree = parser.parse(xml, "item_conflict")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_conflict",
            layoutResId = "R.layout.item_conflict",
            useFastPath = true,
            dataBindingVariables = listOf(
                DataBindingVariable("title", "java.lang.String"),
                DataBindingVariable("root", "java.lang.String"),
                DataBindingVariable("validVar", "int")
            )
        ).toString()

        // title 和 root 冲突，被跳过
        assertThat(generated).contains("public val title: TextView")
        assertThat(generated).doesNotContain("public var title: String?")
        assertThat(generated).doesNotContain("public var root: String?")
        // validVar 不冲突，正常生成
        assertThat(generated).contains("public var validVar: Int? = null")
    }
}
