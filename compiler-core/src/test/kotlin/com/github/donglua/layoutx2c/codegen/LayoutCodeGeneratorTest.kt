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

    @Test
    fun `supported attributes emit current view property code`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:id="@+id/title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:visibility="invisible"
                android:text="@string/app_name"
                android:paddingStart="4dp"
                android:paddingTop="8dp"
                android:paddingEnd="12dp"
                android:paddingBottom="16dp"
                android:gravity="center_vertical|end" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "supported_attrs").root)
        val generated = generator.generate(analyzed, "supported_attrs", "R.layout.supported_attrs").toString()

        assertThat(generated).contains("id = R.id.title")
        assertThat(generated).contains("visibility = View.INVISIBLE")
        assertThat(generated).contains("text = context.getString(R.string.app_name)")
        assertThat(generated).contains("setPadding((4f * context.resources.displayMetrics.density + 0.5f).toInt(),")
        assertThat(generated).contains("(8f *")
        assertThat(generated).contains("context.resources.displayMetrics.density + 0.5f).toInt(), (12f *")
        assertThat(generated).contains("context.resources.displayMetrics.density + 0.5f).toInt(), (16f *")
        assertThat(generated).contains("context.resources.displayMetrics.density + 0.5f).toInt())")
        assertThat(generated).contains("gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END")
    }

    @Test
    fun `orientation emits only for linear layout nodes`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="horizontal" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "frame_orientation").root)
        val generated = generator.generate(analyzed, "frame_orientation", "R.layout.frame_orientation").toString()

        assertThat(generated).doesNotContain("orientation = LinearLayout.HORIZONTAL")
    }

    @Test
    fun `image view emits src scaleType and tint`() {
        val xml = """
            <ImageView xmlns:android="http://schemas.android.com/apk/res/android"
                android:id="@+id/avatar"
                android:layout_width="32dp"
                android:layout_height="32dp"
                android:src="@drawable/ic_demo"
                android:scaleType="centerCrop"
                android:tint="@color/demo_tint" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "image_attrs").root)
        val generated = generator.generate(analyzed, "image_attrs", "R.layout.image_attrs").toString()

        assertThat(generated).contains("val root = AppCompatImageView(context).apply {")
        assertThat(generated).contains("setImageResource(R.drawable.ic_demo)")
        assertThat(generated).contains("scaleType = ImageView.ScaleType.CENTER_CROP")
        assertThat(generated).contains("imageTintList = ContextCompat.getColorStateList(context, R.color.demo_tint)")
    }

    @Test
    fun `unknown image scale type falls back instead of emitting default scale type`() {
        val xml = """
            <ImageView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="32dp"
                android:layout_height="32dp"
                android:scaleType="centercrop" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "image_unknown_scale_type").root)
        val generated = generator.generate(analyzed, "image_unknown_scale_type", "R.layout.image_unknown_scale_type").toString()

        assertThat(generated).contains("FallbackInflater.inflate(context, R.layout.image_unknown_scale_type, parent)")
        assertThat(generated).doesNotContain("ScaleType.FIT_CENTER")
    }
}
