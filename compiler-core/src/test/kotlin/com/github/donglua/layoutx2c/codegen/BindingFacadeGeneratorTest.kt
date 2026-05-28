package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BindingFacadeGeneratorTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()
    private val generator = BindingFacadeGenerator(
        packageName = "com.example.generated",
        rPackageName = "com.example"
    )

    @Test
    fun `generates binding facade with fast path and non null fields`() {
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
        val tree = parser.parse(xml, "item_demo")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_demo",
            layoutResId = "R.layout.item_demo",
            useFastPath = true
        ).toString()

        assertThat(generated).contains("public class ItemDemoX2CBinding private constructor(")
        assertThat(generated).contains("public val root: View")
        assertThat(generated).contains("public val titleText: TextView")
        assertThat(generated).contains("public companion object")
        assertThat(generated).contains("public fun inflate(")
        assertThat(generated).contains("inflater: LayoutInflater")
        assertThat(generated).contains("val root = ItemDemoX2C.inflate(inflater.context, parent, attachToParent)")
        assertThat(generated).contains("return bind(root)")
        assertThat(generated).contains("public fun bind(rootView: View): ItemDemoX2CBinding")
        assertThat(generated).contains("val titleText = rootView.findViewById<TextView>(R.id.title_text)")
        assertThat(generated).contains("?: error(\"Missing required view with ID: title_text\")")
        assertThat(generated).doesNotContain("ItemDemoBinding")
    }

    @Test
    fun `bind root parameter is not shadowed by root id field`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:id="@+id/root"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
            </layout>
        """.trimIndent()
        val tree = parser.parse(xml, "item_root")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_root",
            layoutResId = "R.layout.item_root",
            useFastPath = true
        ).toString()

        assertThat(generated).contains("public fun bind(rootView: View): ItemRootX2CBinding")
        assertThat(generated).contains("val root = rootView.findViewById<TextView>(R.id.root)")
        assertThat(generated).contains("return ItemRootX2CBinding(rootView, root)")
    }

    @Test
    fun `generates fallback only inflate path`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:id="@+id/title"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@{title}" />
                </LinearLayout>
            </layout>
        """.trimIndent()
        val tree = parser.parse(xml, "item_fallback")
        val analyzed = analyzer.analyze(tree.root)

        val generated = generator.generate(
            analyzedRoot = analyzed,
            layoutName = "item_fallback",
            layoutResId = "R.layout.item_fallback",
            useFastPath = false
        ).toString()

        assertThat(generated).contains("public class ItemFallbackX2CBinding private constructor(")
        assertThat(generated).contains("val root = inflater.inflate(R.layout.item_fallback, parent, attachToParent)")
        assertThat(generated).doesNotContain("ItemFallbackX2C.inflate")
        assertThat(generated).doesNotContain("ItemFallbackBinding")
    }
}
