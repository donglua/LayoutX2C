package com.github.donglua.layoutx2c.report

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.LayoutNodeType
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SupportReportGeneratorTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()
    private val analyzerV2 = LayoutAnalyzerV2()
    private val generator = SupportReportGenerator()

    @Test
    fun `data binding wrapper reports the real view root`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable
                        name="title"
                        type="java.lang.String" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "data_binding_layout")
        val analyzed = analyzer.analyze(tree.root)
        val report = generator.generate(analyzed, "data_binding_layout", tree)

        assertThat(report).contains("\"tag\": \"LinearLayout\"")
        assertThat(report).contains("\"support\": \"FULL\"")
        assertThat(report).contains("\"reason\": null")
        assertThat(report).contains("\"bindingFacade\": \"BINDING_FACADE_GENERATED_FAST_PATH\"")
        assertThat(report).doesNotContain("\"tag\": \"layout\"")
        assertThat(report).doesNotContain("\"reason\": \"UNSUPPORTED_VIEW\"")
    }

    @Test
    fun `malformed data binding wrapper is reported as dedicated fallback reason`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable
                        name="title"
                        type="java.lang.String" />
                </data>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "malformed_data_binding_layout")
        val analyzed = analyzer.analyze(tree.root)
        val report = generator.generate(analyzed, "malformed_data_binding_layout", tree)

        assertThat(report).contains("\"tag\": \"layout\"")
        assertThat(report).contains("\"reason\": \"DATA_BINDING_WRAPPER\"")
        assertThat(report).contains("\"bindingFacade\": \"BINDING_FACADE_SKIPPED_MALFORMED_LAYOUT\"")
    }

    @Test
    fun `plain layout reports no binding facade`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent" />
        """.trimIndent()

        val tree = parser.parse(xml, "plain_layout")
        val analyzed = analyzer.analyze(tree.root)
        val report = generator.generate(analyzed, "plain_layout", tree)

        assertThat(report).contains("\"bindingFacade\": \"NOT_DATA_BINDING_LAYOUT\"")
    }

    @Test
    fun `data binding expression reports fallback only binding facade`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@{title}" />
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "expression_layout")
        val analyzed = analyzer.analyze(tree.root)
        val report = generator.generate(analyzed, "expression_layout", tree)

        assertThat(report).contains("\"bindingFacade\": \"BINDING_FACADE_GENERATED_FALLBACK_ONLY\"")
    }

    @Test
    fun `duplicate ids report skipped binding facade`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:id="@+id/title"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                    <TextView
                        android:id="@id/title"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "duplicate_layout")
        val analyzed = analyzer.analyze(tree.root)
        val report = generator.generate(analyzed, "duplicate_layout", tree)

        assertThat(report).contains("\"bindingFacade\": \"BINDING_FACADE_SKIPPED_DUPLICATE_ID\"")
    }

    @Test
    fun `data binding constraint root report keeps root supported`() {
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
                    <androidx.constraintlayout.widget.Guideline
                        android:id="@+id/guide"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintGuide_percent="0.5" />
                </androidx.constraintlayout.widget.ConstraintLayout>
            </layout>
        """.trimIndent()

        val tree = parser.parse(xml, "feature_home_entry")
        val analyzed = analyzer.analyze(tree.root)
        val report = generator.generate(analyzed, "feature_home_entry", tree)
        val rootEntry = report
            .substringAfter("\"tag\": \"androidx.constraintlayout.widget.ConstraintLayout\"")
            .substringBefore("    },")

        assertThat(rootEntry).contains("\"support\": \"FULL\"")
        assertThat(rootEntry).contains("\"reason\": null")
        assertThat(rootEntry).doesNotContain("android:layout_width")
        assertThat(rootEntry).doesNotContain("android:layout_height")
        assertThat(rootEntry).doesNotContain("android:background")
        assertThat(report).contains("\"tag\": \"com.example.widget.HomeTabLayout\"")
        assertThat(report).contains("\"tag\": \"androidx.constraintlayout.widget.Guideline\"")
        assertThat(report).contains("\"support\": \"FALLBACK\"")
        assertThat(report).doesNotContain("\"reason\": \"DATA_BINDING_WRAPPER\"")
    }

    @Test
    fun `include resolution error is reported as fallback reason`() {
        val root = LayoutNode(
            tagName = "LinearLayout",
            attributes = mapOf(
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent"
            ),
            children = listOf(
                LayoutNode(
                    tagName = "include",
                    attributes = mapOf("layout" to "@layout/missing_panel"),
                    children = emptyList(),
                    indexInParent = 0,
                    nodeType = LayoutNodeType.Include("missing_panel", "INCLUDE_NOT_FOUND")
                )
            ),
            indexInParent = 0
        )

        val analyzed = analyzerV2.analyze(root)
        val report = generator.generate(analyzed, "host_layout")

        assertThat(report).contains("\"tag\": \"include\"")
        assertThat(report).contains("\"support\": \"FALLBACK\"")
        assertThat(report).contains("\"reason\": \"INCLUDE_NOT_FOUND\"")
    }
}
