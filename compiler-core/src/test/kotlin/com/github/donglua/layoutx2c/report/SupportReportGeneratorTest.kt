package com.github.donglua.layoutx2c.report

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SupportReportGeneratorTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()
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
}
