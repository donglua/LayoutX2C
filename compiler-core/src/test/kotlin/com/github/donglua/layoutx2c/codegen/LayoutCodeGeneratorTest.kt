package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutCodeGeneratorTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()
    private val generator = LayoutCodeGenerator(
        packageName = "com.example.generated",
        rPackageName = "com.example"
    )

    @Test
    fun `nested fallback uses full child path instead of root child index`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <View
                    android:layout_width="1dp"
                    android:layout_height="1dp" />
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <androidx.recyclerview.widget.RecyclerView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content" />
                </LinearLayout>
            </FrameLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "nested_fallback").root)
        val generated = generator.generate(analyzed, "nested_fallback", "R.layout.nested_fallback").toString()

        assertThat(generated).contains("FallbackInflater.inflateChild(context, R.layout.nested_fallback,")
        assertThat(generated).contains("intArrayOf(1, 0), root_child1)")
    }

    @Test
    fun `root fallback inflates whole layout instead of extracting a child`() {
        val xml = """
            <androidx.recyclerview.widget.RecyclerView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "root_fallback").root)
        val generated = generator.generate(analyzed, "root_fallback", "R.layout.root_fallback").toString()

        assertThat(generated).contains(
            "val root = FallbackInflater.inflate(context, R.layout.root_fallback, parent)"
        )
    }

    @Test
    fun `fallback child receives generated parent layout params before addView`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <androidx.recyclerview.widget.RecyclerView
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="1"
                    android:layout_marginTop="8dp" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "fallback_layout_params").root)
        val generated = generator.generate(analyzed, "fallback_layout_params", "R.layout.fallback_layout_params").toString()

        assertThat(generated).contains("root_child0.layoutParams =")
        assertThat(generated).contains("android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,")
        assertThat(generated).contains("0, 1.0f)")
        assertThat(generated).contains("(root_child0.layoutParams as ViewGroup.MarginLayoutParams).setMargins(0, (8f *")
        assertThat(generated).contains("context.resources.displayMetrics.density + 0.5f).toInt(), 0, 0)")
    }
}
