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

        // Should set orientation on the generated ConstraintLayout.LayoutParams, not on Guideline itself.
        assertThat(generated).contains("root_child0LayoutParams.orientation = ConstraintLayout.LayoutParams.VERTICAL")
        assertThat(generated).doesNotContain("root_child0 = Guideline(context).apply {\n      id = R.id.guideline_vertical\n      orientation =")

        // Should set guideline percent on the generated ConstraintLayout.LayoutParams.
        assertThat(generated).contains("root_child0LayoutParams.guidePercent = 0.3")
        assertThat(generated).doesNotContain("setGuidelinePercent")
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

        // Should set orientation on the generated ConstraintLayout.LayoutParams, not on Guideline itself.
        assertThat(generated).contains("root_child0LayoutParams.orientation = ConstraintLayout.LayoutParams.HORIZONTAL")

        // Should set guideline begin on the generated ConstraintLayout.LayoutParams.
        assertThat(generated).contains("root_child0LayoutParams.guideBegin = ")
        assertThat(generated).contains("(100f * density).toInt()")
        assertThat(generated).doesNotContain("setGuidelineBegin")
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

        // Should set guideline end on the generated ConstraintLayout.LayoutParams.
        assertThat(generated).contains("root_child0LayoutParams.guideEnd = ")
        assertThat(generated).contains("(50f * density).toInt()")
        assertThat(generated).doesNotContain("setGuidelineEnd")
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
