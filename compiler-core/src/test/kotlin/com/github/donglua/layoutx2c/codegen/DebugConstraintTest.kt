package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import org.junit.Test

class DebugConstraintTest {
    @Test
    fun debug() {
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
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()
        val parser = XmlLayoutParser()
        val analyzer = LayoutAnalyzer()
        val analyzed = analyzer.analyze(parser.parse(xml, "constraint_basic").root)
        println("Children size: " + analyzed.children.size)
    }
}
