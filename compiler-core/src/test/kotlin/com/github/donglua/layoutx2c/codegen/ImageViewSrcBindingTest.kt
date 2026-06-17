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

        // 验证 android:src 被识别为 dataBindingAttribute
        val imageViewNode = analyzed.children[0].children[0] // FrameLayout -> ImageView
        assertThat(imageViewNode.dataBindingAttributes).contains("android:src")
        assertThat(imageViewNode.unsupportedAttributes).doesNotContain("android:src")

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "test_icon_binding",
            layoutResId = "R.layout.test_icon_binding",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        println("=== Generated code ===")
        println(generated)

        // Should generate both text and icon bindings in executeBindings()
        assertThat(generated).contains("viewText.setText(text ?: \"\")")
        assertThat(generated).contains("viewIcon.setImageResource(icon ?: 0)")
    }
}
