package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BindingFacadeEligibilityTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()

    @Test
    fun `non data binding layout is not eligible`() {
        val tree = parser.parse(
            """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            """.trimIndent(),
            "plain"
        )

        val eligibility = BindingFacadeEligibility.evaluate(tree, analyzer.analyze(tree.root))

        assertThat(eligibility.status).isEqualTo(BindingFacadeStatus.NOT_DATA_BINDING_LAYOUT)
        assertThat(eligibility.shouldGenerate).isFalse()
    }

    @Test
    fun `supported data binding layout is fast path eligible`() {
        val tree = parser.parse(
            """
                <layout xmlns:android="http://schemas.android.com/apk/res/android">
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" />
                </layout>
            """.trimIndent(),
            "fast"
        )

        val eligibility = BindingFacadeEligibility.evaluate(tree, analyzer.analyze(tree.root))

        assertThat(eligibility.status).isEqualTo(BindingFacadeStatus.BINDING_FACADE_GENERATED_FAST_PATH)
        assertThat(eligibility.shouldGenerate).isTrue()
        assertThat(eligibility.useFastPath).isTrue()
    }

    @Test
    fun `data binding expression is fallback only eligible`() {
        val tree = parser.parse(
            """
                <layout xmlns:android="http://schemas.android.com/apk/res/android">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@{title}" />
                </layout>
            """.trimIndent(),
            "fallback"
        )

        val eligibility = BindingFacadeEligibility.evaluate(tree, analyzer.analyze(tree.root))

        assertThat(eligibility.status).isEqualTo(BindingFacadeStatus.BINDING_FACADE_GENERATED_FALLBACK_ONLY)
        assertThat(eligibility.shouldGenerate).isTrue()
        assertThat(eligibility.useFastPath).isFalse()
    }

    @Test
    fun `malformed data binding layout is skipped`() {
        val tree = parser.parse(
            """
                <layout xmlns:android="http://schemas.android.com/apk/res/android">
                    <data />
                </layout>
            """.trimIndent(),
            "malformed"
        )

        val eligibility = BindingFacadeEligibility.evaluate(tree, analyzer.analyze(tree.root))

        assertThat(eligibility.status).isEqualTo(BindingFacadeStatus.BINDING_FACADE_SKIPPED_MALFORMED_LAYOUT)
        assertThat(eligibility.shouldGenerate).isFalse()
    }

    @Test
    fun `duplicate id data binding layout is skipped`() {
        val tree = parser.parse(
            """
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
            """.trimIndent(),
            "duplicate"
        )

        val eligibility = BindingFacadeEligibility.evaluate(tree, analyzer.analyze(tree.root))

        assertThat(eligibility.status).isEqualTo(BindingFacadeStatus.BINDING_FACADE_SKIPPED_DUPLICATE_ID)
        assertThat(eligibility.shouldGenerate).isFalse()
    }
}
