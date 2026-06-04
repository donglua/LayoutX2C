package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuidelineCodegenTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()
    private val generator = LayoutCodeGenerator(
        packageName = "com.example.generated",
        rPackageName = "com.example"
    )

    @Test
    fun `guideline with vertical orientation and percent positioning`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <androidx.constraintlayout.widget.Guideline
                    android:id="@+id/guideline_vertical"
                    android:layout_width="wrap_content"
                    android:layout_height="0dp"
                    android:orientation="vertical"
                    app:layout_constraintGuide_percent="0.3" />

            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test_guideline_percent")
        val analyzed = analyzer.analyze(tree.root)
        val generated = generator.generate(analyzed, "test_guideline_percent", "R.layout.test_guideline_percent").toString()

        // Should generate Guideline instance
        assertThat(generated).contains("import androidx.constraintlayout.widget.Guideline")
        assertThat(generated).contains("val root_child0 = Guideline(context)")

        // Should set orientation to VERTICAL
        assertThat(generated).contains("orientation = ConstraintLayout.LayoutParams.VERTICAL")

        // Should call setGuidelinePercent with 0.3
        assertThat(generated).contains("setGuidelinePercent(0.3")
    }

    @Test
    fun `guideline with horizontal orientation and begin positioning`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <androidx.constraintlayout.widget.Guideline
                    android:id="@+id/guideline_horizontal"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    app:layout_constraintGuide_begin="100dp" />

            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test_guideline_begin")
        val analyzed = analyzer.analyze(tree.root)
        val generated = generator.generate(analyzed, "test_guideline_begin", "R.layout.test_guideline_begin").toString()

        // Should set orientation to HORIZONTAL
        assertThat(generated).contains("orientation = ConstraintLayout.LayoutParams.HORIZONTAL")

        // Should call setGuidelineBegin with dimension value
        assertThat(generated).contains("setGuidelineBegin(")
        assertThat(generated).contains("100")
    }

    @Test
    fun `guideline with end positioning`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <androidx.constraintlayout.widget.Guideline
                    android:id="@+id/guideline_end"
                    android:layout_width="wrap_content"
                    android:layout_height="0dp"
                    android:orientation="vertical"
                    app:layout_constraintGuide_end="50dp" />

            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test_guideline_end")
        val analyzed = analyzer.analyze(tree.root)
        val generated = generator.generate(analyzed, "test_guideline_end", "R.layout.test_guideline_end").toString()

        // Should call setGuidelineEnd
        assertThat(generated).contains("setGuidelineEnd(")
        assertThat(generated).contains("50")
    }

    @Test
    fun `views can reference guideline as constraint anchor`() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <androidx.constraintlayout.widget.Guideline
                    android:id="@+id/guideline"
                    android:layout_width="wrap_content"
                    android:layout_height="0dp"
                    android:orientation="vertical"
                    app:layout_constraintGuide_percent="0.5" />

                <TextView
                    android:id="@+id/text"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:text="Test"
                    app:layout_constraintStart_toStartOf="@id/guideline"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintTop_toTopOf="parent" />

            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val tree = parser.parse(xml, "test_guideline_reference")
        val analyzed = analyzer.analyze(tree.root)
        val generated = generator.generate(analyzed, "test_guideline_reference", "R.layout.test_guideline_reference").toString()

        // TextView should reference guideline via R.id.guideline
        assertThat(generated).contains("startToStart = R.id.guideline")
    }
}
