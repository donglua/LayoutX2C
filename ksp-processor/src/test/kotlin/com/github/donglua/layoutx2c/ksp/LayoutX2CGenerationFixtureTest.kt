package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.analyzer.SupportLevel
import com.github.donglua.layoutx2c.codegen.LayoutCodeGenerator
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.github.donglua.layoutx2c.registry.CustomViewAttribute
import com.github.donglua.layoutx2c.registry.CustomViewAttributeKind
import com.github.donglua.layoutx2c.registry.CustomViewDescriptor
import com.github.donglua.layoutx2c.registry.ResourceAwareViewRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutX2CGenerationFixtureTest {

    @Test
    fun `bare ksp config generates data binding constraint root instead of root fallback`() {
        val configSource = """
            package com.fixture.legacy.home

            import com.fixture.feature.home.R
            import com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig

            @FastLayoutConfig
            object LayoutX2CConfig {
                val layouts = intArrayOf(
                    R.layout.feature_home_entry,
                )
            }
        """.trimIndent()
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto">
                <data>
                    <import type="com.fixture.other.R" />
                </data>
                <androidx.constraintlayout.widget.ConstraintLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="@drawable/home_header_bg">
                    <TextView
                        android:id="@+id/title"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toTopOf="parent"
                        android:text="Title" />
                    <com.fixture.widget.HomeTabLayout
                        android:id="@+id/home_tabs"
                        android:layout_width="match_parent"
                        android:layout_height="48dp"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@id/title" />
                    <androidx.constraintlayout.widget.Barrier
                        android:id="@+id/barrier"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </androidx.constraintlayout.widget.ConstraintLayout>
            </layout>
        """.trimIndent()

        val layoutNames = LayoutX2CConfigParser.extractLayoutNames(configSource)
        val rPackageName = LayoutX2CConfigParser.extractRPackageName(configSource)
        val packageName = "$rPackageName.generated"
        val tree = XmlLayoutParser().parse(xml, "feature_home_entry")
        val analyzed = LayoutAnalyzer().analyze(tree.root)
        val generated = LayoutCodeGenerator(
            packageName = packageName,
            rPackageName = rPackageName ?: error("R package missing")
        ).generate(analyzed, "feature_home_entry", "R.layout.feature_home_entry").toString()

        assertThat(layoutNames).containsExactly("feature_home_entry")
        assertThat(rPackageName).isEqualTo("com.fixture.feature.home")
        assertThat(generated).contains("package com.fixture.feature.home.generated")
        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(analyzed.unsupportedAttributes).doesNotContain("android:layout_width")
        assertThat(analyzed.unsupportedAttributes).doesNotContain("android:layout_height")
        assertThat(analyzed.unsupportedAttributes).doesNotContain("android:background")
        assertThat(generated).contains("val root = ConstraintLayout(context).apply {")
        assertThat(generated).contains("setBackgroundResource(R.drawable.home_header_bg)")
        assertThat(generated).contains(
            "FallbackInflater.inflateChildren(context, R.layout.feature_home_entry, " +
                "arrayOf(FallbackChildPlan(intArrayOf(1), \"com.fixture.widget.HomeTabLayout\", false), " +
                "FallbackChildPlan(intArrayOf(2), \"androidx.constraintlayout.widget.Barrier\", false)), root)"
        )
        assertThat(generated).doesNotContain("FallbackInflater.inflateChild(context, R.layout.feature_home_entry,")
        assertThat(generated).doesNotContain("FallbackInflater.inflate(context, R.layout.feature_home_entry, parent)")
    }

    @Test
    fun `custom view whitelist generates typed custom attribute`() {
        val customRegistry = ResourceAwareViewRegistry(
            rPackageName = "com.fixture.feature.home",
            customViews = listOf(
                CustomViewDescriptor(
                    viewClassName = "com.fixture.widget.PriceView",
                    attributes = listOf(
                        CustomViewAttribute(
                            name = "app:priceColor",
                            kind = CustomViewAttributeKind.COLOR
                        )
                    )
                )
            )
        )
        val xml = """
            <com.fixture.widget.PriceView xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:priceColor="@color/red" />
        """.trimIndent()

        val tree = XmlLayoutParser().parse(xml, "price_view")
        val analyzed = LayoutAnalyzer(customRegistry).analyze(tree.root)
        val generated = LayoutCodeGenerator(
            packageName = "com.fixture.feature.home.generated",
            rPackageName = "com.fixture.feature.home",
            viewRegistry = customRegistry
        ).generate(analyzed, "price_view", "R.layout.price_view").toString()

        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(generated).contains("setPriceColor(ContextCompat.getColor(context, R.color.red))")
    }
}
