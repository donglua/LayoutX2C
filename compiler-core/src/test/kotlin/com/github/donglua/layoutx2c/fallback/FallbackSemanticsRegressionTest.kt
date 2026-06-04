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
        assertThat(generated).doesNotContain("val density = context.resources.displayMetrics.density")
    }

    @Test
    fun `root fallback with dp attributes does not generate unused density`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="20dp"
                android:theme="@style/AppTheme">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Fallback" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "themed_fallback").root)
        val generated = generator.generate(analyzed, "themed_fallback", "R.layout.themed_fallback").toString()

        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(generated).contains(
            "val root = FallbackInflater.inflate(context, R.layout.themed_fallback, parent)"
        )
        assertThat(generated).doesNotContain("val density = context.resources.displayMetrics.density")
    }

    @Test
    fun `fallback child layout params with dp declares density`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <com.example.UnsupportedView
                    android:id="@+id/shelter_tab_layout"
                    android:layout_width="match_parent"
                    android:layout_height="51dp"
                    app:layout_constraintBottom_toBottomOf="parent" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "fallback_child_dp_params").root)
        val generated = generator.generate(
            analyzed,
            "fallback_child_dp_params",
            "R.layout.fallback_child_dp_params"
        ).toString()

        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(analyzed.children[0].supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(generated).contains("val density = context.resources.displayMetrics.density")
        assertThat(generated).contains("root_child0.layoutParams = ConstraintLayout.LayoutParams")
        assertThat(generated).contains("(51f * density + 0.5f).toInt()")
    }

    @Test
    fun `unsupported relative rule escalates whole layout to FALLBACK`() {
        val xml = """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignWithParentIfMissing="true"
                    android:text="Title" />
            </RelativeLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "unsupported_relative_rule").root)
        val generated = generator.generate(
            analyzed,
            "unsupported_relative_rule",
            "R.layout.unsupported_relative_rule"
        ).toString()

        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(generated).contains(
            "val root = FallbackInflater.inflate(context, R.layout.unsupported_relative_rule, parent)"
        )
        assertThat(generated).doesNotContain("val root = RelativeLayout(context)")
    }

    @Test
    fun `invalid relative rule value escalates whole layout to FALLBACK`() {
        val xml = """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_below="title"
                    android:text="Title" />
            </RelativeLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "invalid_relative_rule").root)
        val generated = generator.generate(
            analyzed,
            "invalid_relative_rule",
            "R.layout.invalid_relative_rule"
        ).toString()

        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(generated).contains(
            "val root = FallbackInflater.inflate(context, R.layout.invalid_relative_rule, parent)"
        )
        assertThat(generated).doesNotContain("addRule(RelativeLayout.BELOW")
    }

    @Test
    fun `constraint layout complex attribute triggers subtree-level fallback not tree-level`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Safe sibling" />
                <androidx.constraintlayout.widget.ConstraintLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        app:layout_constraintCircle="@id/missing"
                        app:layout_constraintStart_toStartOf="parent" />
                </androidx.constraintlayout.widget.ConstraintLayout>
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "constraint_subtree_fallback").root)
        val generated = generator.generate(
            analyzed,
            "constraint_subtree_fallback",
            "R.layout.constraint_subtree_fallback"
        ).toString()

        // Root LinearLayout is still FULL — not escalated
        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(analyzed.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        // ConstraintLayout subtree is FALLBACK
        assertThat(analyzed.children[1].supportLevel).isEqualTo(SupportLevel.FALLBACK)
        // Generated code: sibling is generated, ConstraintLayout subtree uses FallbackInflater
        assertThat(generated).contains("val root = LinearLayout(context).apply {")
        assertThat(generated).contains("val root_child0 = AppCompatTextView(context).apply {")
        assertThat(generated).contains("FallbackInflater.inflateChild(context, R.layout.constraint_subtree_fallback,")
        assertThat(generated).doesNotContain("val root_child1 = ConstraintLayout(context)")
    }
}
