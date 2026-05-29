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
    fun `facade exposes databinding style inflate entry without registry`() {
        val generated = generator.generateFacade("demo_simple").toString()

        assertThat(generated).contains("public object DemoSimpleX2C")
        assertThat(generated).contains("public fun inflate(")
        assertThat(generated).contains("context: Context")
        assertThat(generated).contains("parent: ViewGroup? = null")
        assertThat(generated).contains("attachToParent: Boolean = false")
        assertThat(generated).contains("val view = Layout_DemoSimple().create(context, parent)")
        assertThat(generated).contains("if (attachToParent && parent != null) {")
        assertThat(generated).contains("parent.addView(view)")
        assertThat(generated).contains("return view")
        assertThat(generated).doesNotContain("LayoutX2CRegistry")
    }

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
                    <com.example.CustomView
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
            <com.example.CustomView xmlns:android="http://schemas.android.com/apk/res/android"
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
    fun `data binding wrapper generates the real view root`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable
                        name="title"
                        type="java.lang.String" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hello" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "data_binding_layout").root)
        val generated = generator.generate(analyzed, "data_binding_layout", "R.layout.data_binding_layout").toString()

        assertThat(generated).contains("val root = LinearLayout(context).apply {")
        assertThat(generated).contains("orientation = LinearLayout.VERTICAL")
        assertThat(generated).contains("val root_child0 = AppCompatTextView(context).apply {")
        assertThat(generated).doesNotContain("FallbackInflater.inflate(context, R.layout.data_binding_layout, parent)")
        assertThat(generated).doesNotContain("val root = layout")
    }

    @Test
    fun `data binding wrapper uses unwrapped root path for nested fallback`() {
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
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                    <com.example.CustomView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "data_binding_nested_fallback").root)
        val generated = generator.generate(
            analyzed,
            "data_binding_nested_fallback",
            "R.layout.data_binding_nested_fallback"
        ).toString()

        assertThat(generated).contains("FallbackInflater.inflateChild(context, R.layout.data_binding_nested_fallback,")
        assertThat(generated).contains("intArrayOf(1), root)")
    }

    @Test
    fun `scroll view emits fill viewport and frame layout params for its child`() {
        val xml = """
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:fillViewport="true">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Title" />
                </LinearLayout>
            </ScrollView>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "scroll_container").root)
        val generated = generator.generate(analyzed, "scroll_container", "R.layout.scroll_container").toString()

        assertThat(generated).contains("val root = ScrollView(context).apply {")
        assertThat(generated).contains("isFillViewport = true")
        assertThat(generated).contains("root_child0.layoutParams =")
        assertThat(generated).contains("FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,")
        assertThat(generated).contains("ViewGroup.LayoutParams.WRAP_CONTENT)")
    }

    @Test
    fun `horizontal scroll view emits horizontal scroll constructor and child layout params`() {
        val xml = """
            <HorizontalScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:fillViewport="false">
                <FrameLayout
                    android:layout_width="320dp"
                    android:layout_height="48dp" />
            </HorizontalScrollView>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "horizontal_scroll_container").root)
        val generated = generator.generate(
            analyzed,
            "horizontal_scroll_container",
            "R.layout.horizontal_scroll_container"
        ).toString()

        assertThat(generated).contains("val root = HorizontalScrollView(context).apply {")
        assertThat(generated).doesNotContain("isFillViewport = false")
        assertThat(generated).contains("root_child0.layoutParams = FrameLayout.LayoutParams((320f *")
    }

    @Test
    fun `root layout params are generated from non null parent`() {
        val xml = """
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:layout_marginBottom="12dp" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "root_layout_params").root)
        val generated = generator.generate(analyzed, "root_layout_params", "R.layout.root_layout_params").toString()

        assertThat(generated).contains("parent?.let { parentView ->")
        assertThat(generated).contains("root.layoutParams =")
        assertThat(generated).contains("is LinearLayout -> LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,")
        assertThat(generated).contains("is FrameLayout -> FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,")
        assertThat(generated).contains("else -> ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,")
        assertThat(generated).contains("ViewGroup.LayoutParams.MATCH_PARENT)")
        assertThat(generated).contains("(root.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = (12f * density +")
    }

    @Test
    fun `layout params use imports instead of fully qualified names`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <View
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "layout_param_imports").root)
        val generated = generator.generate(analyzed, "layout_param_imports", "R.layout.layout_param_imports").toString()

        assertThat(generated).contains("import android.view.ViewGroup")
        assertThat(generated).contains("import android.widget.LinearLayout")
        assertThat(generated).contains("root_child0.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,")
        assertThat(generated).contains("ViewGroup.LayoutParams.WRAP_CONTENT)")
        assertThat(generated).doesNotContain("android.widget.LinearLayout.LayoutParams")
        assertThat(generated).doesNotContain("android.view.ViewGroup.LayoutParams")
    }

    @Test
    fun `nodes without emitted attributes do not use empty apply block`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <View
                    android:layout_width="1dp"
                    android:layout_height="1dp" />
            </FrameLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "empty_apply").root)
        val generated = generator.generate(analyzed, "empty_apply", "R.layout.empty_apply").toString()

        assertThat(generated).contains("val root_child0 = View(context)")
        assertThat(generated).doesNotContain("val root_child0 = View(context).apply {\n  }")
    }

    @Test
    fun `dp conversions reuse local density`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:padding="16dp"
                android:minWidth="120dp" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "density_reuse").root)
        val generated = generator.generate(analyzed, "density_reuse", "R.layout.density_reuse").toString()

        assertThat(generated).contains("val density = context.resources.displayMetrics.density")
        assertThat(generated).contains("setPadding((16f * density + 0.5f).toInt(),")
        assertThat(generated).contains("minimumWidth = (120f * density + 0.5f).toInt()")
        assertThat(generated).doesNotContain("16f * context.resources.displayMetrics.density")
    }

    @Test
    fun `layout gravity emits for frame backed parent layout params`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="center" />
            </FrameLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "frame_layout_gravity").root)
        val generated = generator.generate(analyzed, "frame_layout_gravity", "R.layout.frame_layout_gravity").toString()

        assertThat(generated).contains("root_child0.layoutParams =")
        assertThat(generated).contains("FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,")
        assertThat(generated).contains("(root_child0.layoutParams as FrameLayout.LayoutParams).gravity = android.view.Gravity.CENTER")
    }

    @Test
    fun `layout gravity emits for scroll view child layout params`() {
        val xml = """
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="center_horizontal" />
            </ScrollView>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "scroll_layout_gravity").root)
        val generated = generator.generate(analyzed, "scroll_layout_gravity", "R.layout.scroll_layout_gravity").toString()

        assertThat(generated).contains("root_child0.layoutParams =")
        assertThat(generated).contains("FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,")
        assertThat(generated).contains("(root_child0.layoutParams as FrameLayout.LayoutParams).gravity =")
        assertThat(generated).contains("android.view.Gravity.CENTER_HORIZONTAL")
    }

    @Test
    fun `layout gravity emits for linear layout child layout params`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="end|bottom" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "linear_layout_gravity").root)
        val generated = generator.generate(analyzed, "linear_layout_gravity", "R.layout.linear_layout_gravity").toString()

        assertThat(generated).contains("root_child0.layoutParams =")
        assertThat(generated).contains("LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,")
        assertThat(generated).contains("(root_child0.layoutParams as LinearLayout.LayoutParams).gravity = android.view.Gravity.END or")
        assertThat(generated).contains("android.view.Gravity.BOTTOM")
    }

    @Test
    fun `fallback child receives generated parent layout params before addView`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <com.example.CustomView
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="1"
                    android:layout_marginTop="8dp" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "fallback_layout_params").root)
        val generated = generator.generate(analyzed, "fallback_layout_params", "R.layout.fallback_layout_params").toString()

        assertThat(generated).contains("root_child0.layoutParams =")
        assertThat(generated).contains("LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,")
        assertThat(generated).contains("1.0f)")
        assertThat(generated).contains("(root_child0.layoutParams as ViewGroup.MarginLayoutParams).topMargin = (8f * density +")
    }

    @Test
    fun `recycler view emits container constructor and parent layout params`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                xmlns:tools="http://schemas.android.com/tools"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/list"
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="1"
                    android:layout_marginTop="8dp"
                    app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
                    tools:listitem="@layout/item_demo" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "recycler_container").root)
        val generated = generator.generate(analyzed, "recycler_container", "R.layout.recycler_container").toString()

        assertThat(generated).contains("import androidx.recyclerview.widget.RecyclerView")
        assertThat(generated).contains("val root_child0 = RecyclerView(context).apply {")
        assertThat(generated).contains("id = R.id.list")
        assertThat(generated).contains("root_child0.layoutParams = LinearLayout.LayoutParams(")
        assertThat(generated).contains("ViewGroup.LayoutParams.MATCH_PARENT")
        assertThat(generated).contains("0")
        assertThat(generated).contains("1.0f)")
        assertThat(generated).contains("(root_child0.layoutParams as ViewGroup.MarginLayoutParams).topMargin = (8f * density +")
        assertThat(generated).doesNotContain("layoutManager")
        assertThat(generated).doesNotContain("listitem")
        assertThat(generated).doesNotContain("FallbackInflater.inflateChild")
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
                android:textColor="@color/title"
                android:textSize="16sp"
                android:textStyle="bold|italic"
                android:background="#FF0000"
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
        assertThat(generated).contains("setTextColor(ContextCompat.getColor(context, R.color.title))")
        assertThat(generated).contains("setTextSize(TypedValue.COMPLEX_UNIT_PX,")
        assertThat(generated).contains("android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f,")
        assertThat(generated).contains("context.resources.displayMetrics)")
        assertThat(generated).contains("setTypeface(typeface, android.graphics.Typeface.BOLD_ITALIC)")
        assertThat(generated).contains("setBackgroundColor(Color.parseColor(\"#FF0000\"))")
        assertThat(generated).contains("setPadding((4f * density + 0.5f).toInt(),")
        assertThat(generated).contains("(8f *")
        assertThat(generated).contains("density + 0.5f).toInt(), (12f *")
        assertThat(generated).contains("density +")
        assertThat(generated).contains("0.5f).toInt(), (16f * density + 0.5f).toInt())")
        assertThat(generated).contains("gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END")
    }

    @Test
    fun `button emits app compat button and text-like attrs`() {
        val xml = """
            <Button xmlns:android="http://schemas.android.com/apk/res/android"
                android:id="@+id/submit"
                android:layout_width="match_parent"
                android:layout_height="48dp"
                android:text="@string/app_name"
                android:textColor="#112233"
                android:textStyle="bold"
                android:enabled="false"
                android:clickable="true"
                android:focusable="false"
                android:elevation="2dp"
                android:minWidth="120dp"
                android:minHeight="40dp" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "button_attrs").root)
        val generated = generator.generate(analyzed, "button_attrs", "R.layout.button_attrs").toString()

        assertThat(generated).contains("val root = AppCompatButton(context).apply {")
        assertThat(generated).contains("id = R.id.submit")
        assertThat(generated).contains("text = context.getString(R.string.app_name)")
        assertThat(generated).contains("setTextColor(Color.parseColor(\"#112233\"))")
        assertThat(generated).contains("setTypeface(typeface, android.graphics.Typeface.BOLD)")
        assertThat(generated).contains("isEnabled = false")
        assertThat(generated).contains("isClickable = true")
        assertThat(generated).contains("isFocusable = false")
        assertThat(generated).contains("elevation = (2f * density)")
        assertThat(generated).contains("minimumWidth = (120f * density + 0.5f).toInt()")
        assertThat(generated).contains("minimumHeight = (40f * density + 0.5f).toInt()")
    }

    @Test
    fun `edit text emits hint and input type`() {
        val xml = """
            <EditText xmlns:android="http://schemas.android.com/apk/res/android"
                android:id="@+id/name"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/app_name"
                android:inputType="textPersonName|textCapWords"
                android:textSize="16sp" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "edit_text_attrs").root)
        val generated = generator.generate(analyzed, "edit_text_attrs", "R.layout.edit_text_attrs").toString()

        assertThat(generated).contains("val root = AppCompatEditText(context).apply {")
        assertThat(generated).contains("id = R.id.name")
        assertThat(generated).contains("hint = context.getString(R.string.app_name)")
        assertThat(generated).contains("inputType = android.text.InputType.TYPE_CLASS_TEXT or")
        assertThat(generated).contains("android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME or")
        assertThat(generated).contains("android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS")
        assertThat(generated).contains("setTextSize(TypedValue.COMPLEX_UNIT_PX,")
    }

    @Test
    fun `number input type flag includes number class`() {
        val xml = """
            <EditText xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="numberDecimal" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "edit_text_number_decimal").root)
        val generated = generator.generate(analyzed, "edit_text_number_decimal", "R.layout.edit_text_number_decimal").toString()

        assertThat(generated).contains("inputType = android.text.InputType.TYPE_CLASS_NUMBER or")
        assertThat(generated).contains("android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL")
    }

    @Test
    fun `unsupported input type combination falls back instead of generating invalid code`() {
        val xml = """
            <EditText xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="textPersonName|textEmailAddress" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "edit_text_invalid_input_type").root)
        val generated = generator.generate(
            analyzed,
            "edit_text_invalid_input_type",
            "R.layout.edit_text_invalid_input_type"
        ).toString()

        assertThat(generated).contains("FallbackInflater.inflate(context, R.layout.edit_text_invalid_input_type, parent)")
        assertThat(generated).doesNotContain("TYPE_TEXT_VARIATION_PERSON_NAME")
        assertThat(generated).doesNotContain("TYPE_TEXT_VARIATION_EMAIL_ADDRESS")
    }

    @Test
    fun `gravity is unsupported on plain view and not emitted`() {
        val xml = """
            <View xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "view_gravity").root)
        val generated = generator.generate(analyzed, "view_gravity", "R.layout.view_gravity").toString()

        assertThat(analyzed.unsupportedAttributes).contains("android:gravity")
        assertThat(generated).doesNotContain("gravity = android.view.Gravity.CENTER")
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

    @Test
    fun `high frequency attributes emit resource references`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#112233"
                android:textSize="@dimen/title_size"
                android:background="@drawable/title_background" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "high_frequency_attrs").root)
        val generated = generator.generate(analyzed, "high_frequency_attrs", "R.layout.high_frequency_attrs").toString()

        assertThat(generated).contains("setTextColor(Color.parseColor(\"#112233\"))")
        assertThat(generated).contains("setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.title_size))")
        assertThat(generated).contains("setBackgroundResource(R.drawable.title_background)")
    }

    @Test
    fun `relative layout emits relative layout params and addRule calls`() {
        val xml = """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Title" />
                <TextView
                    android:id="@+id/badge"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_below="@id/title"
                    android:layout_alignParentEnd="true"
                    android:text="Badge" />
                <Button
                    android:id="@+id/action"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_toEndOf="@id/title"
                    android:layout_centerVertical="true"
                    android:text="Action" />
            </RelativeLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "relative_rules").root)
        val generated = generator.generate(analyzed, "relative_rules", "R.layout.relative_rules").toString()

        assertThat(generated).contains("import android.widget.RelativeLayout")
        assertThat(generated).contains("val root = RelativeLayout(context)")
        assertThat(generated).contains("root_child1.layoutParams = RelativeLayout.LayoutParams(")
        assertThat(generated).contains("root_child2.layoutParams = RelativeLayout.LayoutParams(")
        assertThat(generated).contains("root_child1.layoutParams as RelativeLayout.LayoutParams")
        assertThat(generated).contains("RelativeLayout.BELOW")
        assertThat(generated).contains("RelativeLayout.ALIGN_PARENT_END")
        assertThat(generated).contains("root_child2.layoutParams as RelativeLayout.LayoutParams")
        assertThat(generated).contains("RelativeLayout.END_OF")
        assertThat(generated).contains("RelativeLayout.CENTER_VERTICAL")
        assertThat(generated).contains("R.id.title")
        assertThat(generated).doesNotContain("root_child1.layoutParams = ViewGroup.MarginLayoutParams(")
    }

    @Test
    fun `relative layout emits every declared rule and skips false boolean rules`() {
        val xml = """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content">
                <TextView
                    android:id="@+id/anchor"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
                <TextView
                    android:id="@+id/target"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_above="@+id/anchor"
                    android:layout_below="@id/anchor"
                    android:layout_toStartOf="@id/anchor"
                    android:layout_toEndOf="@id/anchor"
                    android:layout_toLeftOf="@id/anchor"
                    android:layout_toRightOf="@id/anchor"
                    android:layout_alignStart="@id/anchor"
                    android:layout_alignEnd="@id/anchor"
                    android:layout_alignLeft="@id/anchor"
                    android:layout_alignRight="@id/anchor"
                    android:layout_alignTop="@id/anchor"
                    android:layout_alignBottom="@id/anchor"
                    android:layout_alignParentStart="true"
                    android:layout_alignParentEnd="true"
                    android:layout_alignParentLeft="true"
                    android:layout_alignParentRight="true"
                    android:layout_alignParentTop="true"
                    android:layout_alignParentBottom="true"
                    android:layout_centerInParent="true"
                    android:layout_centerHorizontal="false"
                    android:layout_centerVertical="false" />
            </RelativeLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "relative_all_rules").root)
        val generated = generator.generate(analyzed, "relative_all_rules", "R.layout.relative_all_rules").toString()

        assertThat(generated).contains("RelativeLayout.ABOVE")
        assertThat(generated).contains("RelativeLayout.BELOW")
        assertThat(generated).contains("RelativeLayout.START_OF")
        assertThat(generated).contains("RelativeLayout.END_OF")
        assertThat(generated).contains("RelativeLayout.LEFT_OF")
        assertThat(generated).contains("RelativeLayout.RIGHT_OF")
        assertThat(generated).contains("RelativeLayout.ALIGN_START")
        assertThat(generated).contains("RelativeLayout.ALIGN_END")
        assertThat(generated).contains("RelativeLayout.ALIGN_LEFT")
        assertThat(generated).contains("RelativeLayout.ALIGN_RIGHT")
        assertThat(generated).contains("RelativeLayout.ALIGN_TOP")
        assertThat(generated).contains("RelativeLayout.ALIGN_BOTTOM")
        assertThat(generated).contains("R.id.anchor")
        assertThat(generated).contains("RelativeLayout.ALIGN_PARENT_START")
        assertThat(generated).contains("RelativeLayout.ALIGN_PARENT_END")
        assertThat(generated).contains("RelativeLayout.ALIGN_PARENT_LEFT")
        assertThat(generated).contains("RelativeLayout.ALIGN_PARENT_RIGHT")
        assertThat(generated).contains("RelativeLayout.ALIGN_PARENT_TOP")
        assertThat(generated).contains("RelativeLayout.ALIGN_PARENT_BOTTOM")
        assertThat(generated).contains("RelativeLayout.CENTER_IN_PARENT")
        assertThat(generated).doesNotContain("RelativeLayout.CENTER_HORIZONTAL")
        assertThat(generated).doesNotContain("RelativeLayout.CENTER_VERTICAL")
    }

    @Test
    fun `root layout params use relative layout when parent is relative layout`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Title" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "relative_root_params").root)
        val generated = generator.generate(analyzed, "relative_root_params", "R.layout.relative_root_params").toString()

        assertThat(generated).contains("parent?.let { parentView ->")
        assertThat(generated).contains("is RelativeLayout -> RelativeLayout.LayoutParams(")
    }

    @Test
    fun `constraint layout emits constraint layout params and anchor field assignments`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintHorizontal_bias="0.25"
                    android:text="Title" />
                <TextView
                    android:id="@+id/subtitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintTop_toBottomOf="@id/title"
                    app:layout_constraintStart_toStartOf="@id/title"
                    android:text="Subtitle" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "constraint_basic").root)
        val generated = generator.generate(analyzed, "constraint_basic", "R.layout.constraint_basic").toString()

        assertThat(generated).contains("import androidx.constraintlayout.widget.ConstraintLayout")
        assertThat(generated).contains("val root = ConstraintLayout(context)")
        assertThat(generated).contains("root_child0.layoutParams = ConstraintLayout.LayoutParams(")
        // 0dp under ConstraintLayout maps to MATCH_CONSTRAINT
        assertThat(generated).contains("ConstraintLayout.LayoutParams.MATCH_CONSTRAINT")
        // anchors to parent become PARENT_ID
        assertThat(generated).contains("(root_child0.layoutParams as ConstraintLayout.LayoutParams).startToStart =")
        assertThat(generated).contains("ConstraintLayout.LayoutParams.PARENT_ID")
        assertThat(generated).contains("(root_child0.layoutParams as ConstraintLayout.LayoutParams).endToEnd =")
        assertThat(generated).contains("(root_child0.layoutParams as ConstraintLayout.LayoutParams).topToTop =")
        // bias becomes a float assignment
        assertThat(generated).contains("(root_child0.layoutParams as ConstraintLayout.LayoutParams).horizontalBias = 0.25f")
        // anchors to id reference R.id
        assertThat(generated).contains("(root_child1.layoutParams as ConstraintLayout.LayoutParams).topToBottom = R.id.title")
        assertThat(generated).contains("(root_child1.layoutParams as ConstraintLayout.LayoutParams).startToStart = R.id.title")
    }

    @Test
    fun `constraint layout helper child triggers subtree fallback in codegen`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <androidx.constraintlayout.widget.ConstraintLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <androidx.constraintlayout.widget.Guideline
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintGuide_percent="0.5" />
                </androidx.constraintlayout.widget.ConstraintLayout>
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "constraint_helper").root)
        val generated = generator.generate(analyzed, "constraint_helper", "R.layout.constraint_helper").toString()

        // Helper-tagged subtree falls back via FallbackInflater
        assertThat(generated).contains("FallbackInflater.inflateChild(context, R.layout.constraint_helper,")
        assertThat(generated).doesNotContain("ConstraintLayout.LayoutParams")
    }

    @Test
    fun `root layout params use constraint layout when parent is constraint layout`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Title" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "constraint_root_params").root)
        val generated = generator.generate(
            analyzed,
            "constraint_root_params",
            "R.layout.constraint_root_params"
        ).toString()

        assertThat(generated).contains("parent?.let { parentView ->")
        assertThat(generated).contains("is ConstraintLayout -> ConstraintLayout.LayoutParams(")
    }

    @Test
    fun `constraint layout 0dp dimension under non-constraint parent uses dp conversion`() {
        // Outside ConstraintLayout, 0dp should NOT become MATCH_CONSTRAINT
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <View
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="1" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "linear_zero_dp").root)
        val generated = generator.generate(analyzed, "linear_zero_dp", "R.layout.linear_zero_dp").toString()

        assertThat(generated).doesNotContain("MATCH_CONSTRAINT")
        assertThat(generated).contains("LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,")
        assertThat(generated).contains("1.0f)")
    }
}
