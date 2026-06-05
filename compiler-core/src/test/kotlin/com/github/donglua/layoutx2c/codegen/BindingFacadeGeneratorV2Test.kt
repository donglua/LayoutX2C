package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.parser.DataBindingVariable
import com.github.donglua.layoutx2c.parser.IncludeResolver
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * V2 BindingFacadeGenerator 测试：验证类型化变量输出
 */
class BindingFacadeGeneratorV2Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzerV2()
    private val generator = BindingFacadeGeneratorV2(
        packageName = "com.example.generated",
        rPackageName = "com.example"
    )

    @Test
    fun `generates binding fields for data binding includes without collecting child ids`() {
        tempFolder.newFile("main_tab_view.xml").writeText(
            """
                <layout xmlns:android="http://schemas.android.com/apk/res/android">
                    <data>
                        <variable
                            name="text"
                            type="java.lang.String" />
                    </data>
                    <FrameLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent">
                        <TextView
                            android:id="@+id/view_text"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@{text}" />
                    </FrameLayout>
                </layout>
            """.trimIndent()
        )
        val includeParser = XmlLayoutParser(includeResolver = IncludeResolver(tempFolder.root))
        val tree = includeParser.parse(
            """
                <layout xmlns:android="http://schemas.android.com/apk/res/android">
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent">
                        <include
                            android:id="@+id/layout1"
                            layout="@layout/main_tab_view"
                            android:layout_width="0dp"
                            android:layout_height="match_parent" />
                        <include
                            android:id="@+id/layout2"
                            layout="@layout/main_tab_view"
                            android:layout_width="0dp"
                            android:layout_height="match_parent" />
                    </LinearLayout>
                </layout>
            """.trimIndent(),
            "main_tab_layout"
        )
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "main_tab_layout",
            layoutResId = "R.layout.main_tab_layout",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        assertThat(generated).contains("import com.example.databinding.MainTabViewBinding")
        assertThat(generated).contains("public val layout1: MainTabViewBinding")
        assertThat(generated).contains("public val layout2: MainTabViewBinding")
        assertThat(generated).contains("val layout1Root = rootView.findViewById<View>(R.id.layout1)")
        assertThat(generated).contains("val layout1 = DataBindingUtil.bind<MainTabViewBinding>(layout1Root)")
        assertThat(generated).contains("?: error(\"Missing required binding with ID: layout1\")")
        assertThat(generated).contains("val binding = MainTabLayoutX2CBinding(rootView, layout1, layout2)")
        assertThat(generated).doesNotContain("public val viewText: TextView")
    }

    @Test
    fun `generated binding extends ViewDataBinding and implements supported contract`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable
                        name="title"
                        type="java.lang.String" />
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
        val tree = parser.parse(xml, "item_contract")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_contract",
            layoutResId = "R.layout.item_contract",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        assertThat(generated).contains("import androidx.databinding.Bindable")
        assertThat(generated).contains("import androidx.databinding.ViewDataBinding")
        assertThat(generated).doesNotContain("import com.example.BR")
        assertThat(generated).contains("private val TITLE_0: Int = resolveBrId(\"title\", 1)")
        assertThat(generated).contains("Class.forName(\"com.example.BR\").getField(name).getInt(null)")
        assertThat(generated).contains(") : ViewDataBinding(null, rootView, 0)")
        assertThat(generated).contains("@get:Bindable")
        assertThat(generated).contains("public var title: String? = null")
        assertThat(generated).contains("notifyPropertyChanged(TITLE_0)")
        assertThat(generated).contains("requestRebind()")
        assertThat(generated).contains("override fun setVariable(variableId: Int, variable: Any?): Boolean")
        assertThat(generated).contains("TITLE_0 -> {")
        assertThat(generated).contains("override fun invalidateAll()")
        assertThat(generated).contains("override fun hasPendingBindings(): Boolean")
        assertThat(generated).contains("protected override fun executeBindings()")
        assertThat(generated).contains("titleText.setText(title ?: \"\")")
    }

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
        // lifecycleOwner 使用 ViewDataBinding 父类实现，不再生成占位字段
        assertThat(generated).doesNotContain("public var lifecycleOwner")
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

        // bind 方法构造 binding 实例，注册 root tag，调用 setupTwoWayBindings，然后返回
        assertThat(generated).contains("val binding = ItemBindX2CBinding(rootView, titleText)")
        assertThat(generated).contains("binding.setRootTag(rootView)")
        assertThat(generated).contains("binding.setupTwoWayBindings()")
        assertThat(generated).contains("return binding")
        // 构造函数不包含变量参数
        assertThat(generated).contains("public class ItemBindX2CBinding private constructor(")
        assertThat(generated).contains("rootView: View,")
        assertThat(generated).contains("public val titleText: TextView,")
        assertThat(generated).contains(") : ViewDataBinding(null, rootView, 0)")
        assertThat(generated).doesNotContain("private constructor(\n    rootView: View,\n    titleText: TextView,\n    title:")
    }

    @Test
    fun `fast path inflate uses X2C facade refs`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
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
        val tree = parser.parse(xml, "item_fast")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_fast",
            layoutResId = "R.layout.item_fast",
            useFastPath = true,
            dataBindingVariables = emptyList()
        ).toString()

        assertThat(generated).contains("val result = ItemFastX2C.inflateWithRefs(inflater.context, parent, attachToParent)")
        assertThat(generated).contains("return bindFast(result.first, result.second)")
        assertThat(generated).contains("private fun bindFast(")
        assertThat(generated).contains("val titleText = refs.get(R.id.title_text) as? TextView")
        assertThat(generated).doesNotContain("val root = ItemFastX2C.inflate(inflater.context, parent, attachToParent)")
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
