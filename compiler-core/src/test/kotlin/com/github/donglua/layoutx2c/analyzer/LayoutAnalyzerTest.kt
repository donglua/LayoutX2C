package com.github.donglua.layoutx2c.analyzer

import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutAnalyzerTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()

    @Test
    fun `fully supported layout returns FULL`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Hello" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
    }

    @Test
    fun `data binding wrapper analyzes the real view root`() {
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

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.node.tagName).isEqualTo("LinearLayout")
        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
    }

    @Test
    fun `data binding expression falls back the unwrapped root`() {
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
                        android:layout_height="wrap_content"
                        android:text="@{title}" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.node.tagName).isEqualTo("LinearLayout")
        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.children).isEmpty()
    }

    @Test
    fun `two way data binding expression falls back the unwrapped root`() {
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
                    <EditText
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@={title}" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.node.tagName).isEqualTo("LinearLayout")
        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.children).isEmpty()
    }

    @Test
    fun `unsupported view type returns FALLBACK`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <com.example.CustomView
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `scroll containers and recycler view containers are supported`() {
        val xml = """
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:fillViewport="true">
                <HorizontalScrollView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <androidx.recyclerview.widget.RecyclerView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content" />
                </HorizontalScrollView>
            </ScrollView>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[0].children[0].supportLevel).isEqualTo(SupportLevel.FULL)
    }

    @Test
    fun `recycler view design time attributes are ignored as container metadata`() {
        val xml = """
            <androidx.recyclerview.widget.RecyclerView xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                xmlns:tools="http://schemas.android.com/tools"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
                tools:listitem="@layout/item_demo" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.supportedAttributes).contains("app:layoutManager")
        assertThat(result.supportedAttributes).contains("tools:listitem")
        assertThat(result.unsupportedAttributes).isEmpty()
    }

    @Test
    fun `non literal fill viewport returns fallback`() {
        val xml = """
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:fillViewport="@bool/fill_viewport" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.unsupportedAttributes).contains("android:fillViewport")
    }

    @Test
    fun `button and edit text are supported text-like views`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <Button
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Submit"
                    android:enabled="false" />
                <EditText
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textSize="16sp"
                    android:hint="Name"
                    android:inputType="textPersonName" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[1].supportLevel).isEqualTo(SupportLevel.FULL)
    }

    @Test
    fun `input type is edit text only and unsupported values fallback`() {
        val unsupportedOnTextView = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:inputType="text" />
        """.trimIndent()
        val invalidEditText = """
            <EditText xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:inputType="textWeMadeUp" />
        """.trimIndent()

        val textViewResult = analyzer.analyze(parser.parse(unsupportedOnTextView, "test").root)
        val editTextResult = analyzer.analyze(parser.parse(invalidEditText, "test").root)

        assertThat(textViewResult.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(textViewResult.unsupportedAttributes).contains("android:inputType")
        assertThat(editTextResult.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(editTextResult.unsupportedAttributes).contains("android:inputType")
    }

    @Test
    fun `input type rejects mutually exclusive class and variation combinations`() {
        val twoTextVariations = """
            <EditText xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:inputType="textPersonName|textEmailAddress" />
        """.trimIndent()
        val twoClasses = """
            <EditText xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:inputType="text|number" />
        """.trimIndent()
        val noneWithFlag = """
            <EditText xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:inputType="none|textCapWords" />
        """.trimIndent()

        val twoTextVariationsResult = analyzer.analyze(parser.parse(twoTextVariations, "test").root)
        val twoClassesResult = analyzer.analyze(parser.parse(twoClasses, "test").root)
        val noneWithFlagResult = analyzer.analyze(parser.parse(noneWithFlag, "test").root)

        assertThat(twoTextVariationsResult.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(twoClassesResult.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(noneWithFlagResult.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `style attribute forces FALLBACK`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                style="@style/TextAppearance.Title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `unsupported theme reference in value forces FALLBACK`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="?attr/titleText" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `supported color and drawable theme references return FULL`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:textColor="?attr/colorPrimary" />
        """.trimIndent()

        val tree = parser.parse(xml, "theme_refs")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
    }

    @Test
    fun `unsupported attribute returns PARTIAL`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:nextFocusForward="@id/next">
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(result.unsupportedAttributes).contains("android:nextFocusForward")
    }

    @Test
    fun `view-specific attributes are unsupported on other view types`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:src="@drawable/ic_demo">
            </FrameLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.PARTIAL)
        assertThat(result.unsupportedAttributes).containsAtLeast("android:orientation", "android:src")
    }

    @Test
    fun `unknown image scale type returns FALLBACK`() {
        val xml = """
            <ImageView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="32dp"
                android:layout_height="32dp"
                android:scaleType="centercrop" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.unsupportedAttributes).contains("android:scaleType")
    }

    @Test
    fun `supported high frequency attributes return FULL`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@color/title"
                android:textSize="@dimen/title_size"
                android:textStyle="bold|italic"
                android:background="@drawable/title_background" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
    }

    @Test
    fun `supported expanded text attributes return FULL`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textAllCaps="true"
                android:singleLine="true"
                android:ellipsize="middle"
                android:maxLines="3"
                android:minLines="1"
                android:lines="2"
                android:includeFontPadding="false"
                android:lineSpacingExtra="4dp"
                android:lineSpacingMultiplier="1.2"
                android:fontFamily="sans"
                android:textIsSelectable="true"
                android:scrollHorizontally="true" />
        """.trimIndent()

        val tree = parser.parse(xml, "expanded_text_attrs")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.unsupportedAttributes).isEmpty()
    }

    @Test
    fun `supported common view presentation attributes return FULL`() {
        val xml = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:alpha="0.75"
                android:contentDescription="@string/app_name"
                android:tag="panel"
                android:backgroundTint="@color/title"
                android:foreground="@drawable/title_background"
                android:foregroundGravity="center"
                android:importantForAccessibility="yes"
                android:overScrollMode="never"
                android:scrollbars="vertical" />
        """.trimIndent()

        val tree = parser.parse(xml, "common_view_presentation_attrs")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.unsupportedAttributes).isEmpty()
    }

    @Test
    fun `supported widget controls return FULL`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">
                <ProgressBar
                    android:id="@+id/loading"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:indeterminate="false"
                    android:max="100"
                    android:progress="40"
                    android:secondaryProgress="60"
                    android:progressTint="@color/title"
                    android:indeterminateTint="@color/title" />
                <SeekBar
                    android:id="@+id/slider"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:max="10"
                    android:progress="4"
                    android:thumb="@drawable/title_background"
                    android:splitTrack="false" />
                <RatingBar
                    android:id="@+id/rating"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:numStars="5"
                    android:rating="3.5"
                    android:stepSize="0.5"
                    android:isIndicator="true" />
                <Spinner
                    android:id="@+id/filter"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" />
                <Space
                    android:id="@+id/gap"
                    android:layout_width="1dp"
                    android:layout_height="8dp" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "supported_widget_controls")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.unsupportedAttributes).isEmpty()
        assertThat(result.children.map { it.supportLevel }).containsExactly(
            SupportLevel.FULL,
            SupportLevel.FULL,
            SupportLevel.FULL,
            SupportLevel.FULL,
            SupportLevel.FULL
        )
    }

    @Test
    fun `unsupported high frequency attribute values return FALLBACK`() {
        val xml = """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textStyle="blod" />
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.unsupportedAttributes).contains("android:textStyle")
    }

    @Test
    fun `relative layout with common rules returns FULL`() {
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
                    android:id="@+id/subtitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_below="@id/title"
                    android:layout_toEndOf="@id/title"
                    android:layout_alignParentEnd="true"
                    android:layout_centerVertical="true"
                    android:text="Subtitle" />
            </RelativeLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(2)
        assertThat(result.children.map { it.supportLevel }).containsExactly(SupportLevel.FULL, SupportLevel.FULL)
        assertThat(result.children[1].unsupportedAttributes).isEmpty()
    }

    @Test
    fun `constraint layout with safe anchors and bias returns FULL`() {
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
                    app:layout_constraintHorizontal_bias="0.5"
                    android:text="Title" />
                <TextView
                    android:id="@+id/subtitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintTop_toBottomOf="@id/title"
                    app:layout_constraintStart_toStartOf="@id/title"
                    app:layout_constraintVertical_bias="0.0"
                    android:text="Subtitle" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(2)
        assertThat(result.children.map { it.supportLevel }).containsExactly(SupportLevel.FULL, SupportLevel.FULL)
        assertThat(result.children[0].unsupportedAttributes).isEmpty()
        assertThat(result.children[1].unsupportedAttributes).isEmpty()
    }

    @Test
    fun `constraint layout with safe left and right anchors returns FULL`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    app:layout_constraintLeft_toLeftOf="parent"
                    app:layout_constraintRight_toRightOf="parent"
                    app:layout_constraintTop_toTopOf="parent"
                    android:text="Title" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[0].unsupportedAttributes).isEmpty()
    }

    @Test
    fun `constraint layout helper tag children fall back the subtree`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Outer" />
                <androidx.constraintlayout.widget.ConstraintLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <androidx.constraintlayout.widget.Barrier
                        android:id="@+id/barrier"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </androidx.constraintlayout.widget.ConstraintLayout>
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(2)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[1].supportLevel).isEqualTo(SupportLevel.FALLBACK)
        // subtree-level fallback: ConstraintLayout's children are not analyzed
        assertThat(result.children[1].children).isEmpty()
    }

    @Test
    fun `data binding constraint root with helper child keeps root generated`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto">
                <data>
                    <import type="com.example.other.R" />
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
                    <com.example.widget.HomeTabLayout
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

        val result = analyzer.analyze(parser.parse(xml, "feature_home_entry").root)

        assertThat(result.node.tagName).isEqualTo("androidx.constraintlayout.widget.ConstraintLayout")
        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.unsupportedAttributes).doesNotContain("android:layout_width")
        assertThat(result.unsupportedAttributes).doesNotContain("android:layout_height")
        assertThat(result.unsupportedAttributes).doesNotContain("android:background")
        assertThat(result.children).hasSize(3)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[1].supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.children[2].supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `constraint layout complex circle attribute on child falls back the child subtree`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/a"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintCircle="@id/a"
                    android:text="A" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.children[0].children).isEmpty()
    }

    @Test
    fun `constraint layout unsupported circle radius on child falls back the child subtree`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <ImageView
                    android:id="@+id/img"
                    android:layout_width="0dp"
                    android:layout_height="0dp"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintCircleRadius="12dp" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `constraint layout invalid anchor value falls back the child subtree`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintStart_toStartOf="@string/some_string" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `constraint layout invalid bias value falls back the child subtree`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintHorizontal_bias="@dimen/bias" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `constraint layout subtree fallback does not affect siblings`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Sibling above" />
                <androidx.constraintlayout.widget.ConstraintLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintCircle="@id/anchor"
                        app:layout_constraintStart_toStartOf="parent" />
                </androidx.constraintlayout.widget.ConstraintLayout>
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Sibling below" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(3)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[1].supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(result.children[2].supportLevel).isEqualTo(SupportLevel.FULL)
    }

    @Test
    fun `constraint layout extended layout params return FULL`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="0dp"
                    android:layout_height="0dp"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintDimensionRatio="16:9"
                    app:layout_constraintWidth_percent="0.5"
                    app:layout_constraintHeight_percent="0.25"
                    app:layout_constraintHorizontal_chainStyle="packed"
                    app:layout_constraintVertical_chainStyle="spread_inside"
                    app:layout_constraintHorizontal_weight="1.5"
                    app:layout_constraintVertical_weight="2"
                    app:layout_goneMarginStart="8dp"
                    app:layout_goneMarginTop="4dp" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "constraint_extended_params")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children).hasSize(1)
        assertThat(result.children[0].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[0].unsupportedAttributes).isEmpty()
    }

    @Test
    fun `supported rich container and app bar controls return FULL`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <androidx.cardview.widget.CardView
                    android:id="@+id/card"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:cardCornerRadius="8dp"
                    app:cardElevation="2dp"
                    app:cardUseCompatPadding="true" />
                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/material_card"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:cardCornerRadius="12dp"
                    app:strokeColor="@color/title"
                    app:strokeWidth="1dp" />
                <androidx.appcompat.widget.Toolbar
                    android:id="@+id/toolbar"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:title="@string/app_name"
                    app:subtitle="Details"
                    app:navigationIcon="@drawable/title_background" />
                <com.google.android.material.appbar.MaterialToolbar
                    android:id="@+id/material_toolbar"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:title="Material"
                    app:subtitle="@string/app_name" />
                <androidx.viewpager2.widget.ViewPager2
                    android:id="@+id/pager"
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="1" />
            </LinearLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "rich_container_app_bar_controls")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.unsupportedAttributes).isEmpty()
        assertThat(result.children.map { it.supportLevel }).containsExactly(
            SupportLevel.FULL,
            SupportLevel.FULL,
            SupportLevel.FULL,
            SupportLevel.FULL,
            SupportLevel.FULL
        )
    }

    @Test
    fun `relative layout supports every declared rule and false boolean rules`() {
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

        val tree = parser.parse(xml, "test")
        val result = analyzer.analyze(tree.root)

        assertThat(result.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[1].supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(result.children[1].unsupportedAttributes).isEmpty()
    }
}
