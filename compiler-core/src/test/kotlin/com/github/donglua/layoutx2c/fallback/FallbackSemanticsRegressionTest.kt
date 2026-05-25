package com.github.donglua.layoutx2c.fallback

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.analyzer.SupportLevel
import com.github.donglua.layoutx2c.codegen.LayoutCodeGenerator
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FallbackSemanticsRegressionTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()
    private val generator = LayoutCodeGenerator(
        packageName = "com.example.generated",
        rPackageName = "com.example"
    )

    @Test
    fun `unsupported child layout params semantics escalate the whole layout to FALLBACK`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginHorizontal="8dp"
                    android:text="Title" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "unsafe_partial_layout_params").root)

        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `unsafe partial layout params semantics generate layout-level fallback instead of mixed tree code`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginHorizontal="8dp"
                    android:text="Title" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "unsafe_partial_layout_params").root)
        val generated = generator.generate(
            analyzed,
            "unsafe_partial_layout_params",
            "R.layout.unsafe_partial_layout_params"
        ).toString()

        assertThat(generated).contains(
            "val root = FallbackInflater.inflate(context, R.layout.unsafe_partial_layout_params, parent)"
        )
        assertThat(generated).doesNotContain("val root = LinearLayout(context).apply {")
    }
}
