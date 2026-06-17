package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageViewSrcBindingTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzerV2()
    private val generator = BindingFacadeGeneratorV2(
        packageName = "com.example.generated",
        rPackageName = "com.example"
    )

    @Test
    fun `generates ImageView src binding with Integer variable`() {
        val tree = parser.parse(
            """
                <layout xmlns:android="http://schemas.android.com/apk/res/android">
                    <data>
                        <variable name="icon" type="Integer" />
                        <variable name="text" type="String" />
                    </data>
                    <FrameLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent">
                        <ImageView
                            android:id="@+id/view_icon"
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@{icon}" />
                        <TextView
                            android:id="@+id/view_text"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@{text}" />
                    </FrameLayout>
                </layout>
            """.trimIndent(),
            "test_icon_binding"
        )

        val analyzed = analyzer.analyze(tree.root)

        // 调试输出
        println("=== Analyzed structure ===")
        println("Root node: ${analyzed.node.tagName}")
        println("Children count: ${analyzed.children.size}")
        if (analyzed.children.isNotEmpty()) {
            val frameLayout = analyzed.children[0]
            println("First child: ${frameLayout.node.tagName}")
            println("FrameLayout children: ${frameLayout.children.size}")
            frameLayout.children.forEach { child ->
                println("  - ${child.node.tagName} (id: ${child.node.attributes["android:id"]})")
                println("    dataBindingAttributes: ${child.dataBindingAttributes}")
                println("    unsupportedAttributes: ${child.unsupportedAttributes}")
            }
        }

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "test_icon_binding",
            layoutResId = "R.layout.test_icon_binding",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        println("=== Generated code ===")
        println(generated)

        // 验证生成的代码包含两个绑定
        assertThat(generated).contains("viewText.setText(text ?: \"\")")
        assertThat(generated).contains("viewIcon.setImageResource(icon ?: 0)")
    }
}
