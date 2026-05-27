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
    fun `data binding wrapper is reported as dedicated fallback reason`() {
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

        val analyzed = analyzer.analyze(parser.parse(xml, "data_binding_layout").root)
        val report = generator.generate(analyzed, "data_binding_layout")

        assertThat(report).contains("\"reason\": \"DATA_BINDING_WRAPPER\"")
        assertThat(report).doesNotContain("\"reason\": \"UNSUPPORTED_VIEW\"")
    }
}
