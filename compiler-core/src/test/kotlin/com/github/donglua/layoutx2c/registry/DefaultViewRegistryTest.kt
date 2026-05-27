package com.github.donglua.layoutx2c.registry

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.analyzer.SupportLevel
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultViewRegistryTest {

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()
    private val registry = DefaultViewRegistry

    @Test
    fun `unknown tag has no view handler`() {
        assertThat(registry.viewHandlerFor("com.example.CustomView")).isNull()
    }

    @Test
    fun `view scoped attributes are supported only by matching view types`() {
        val linearLayout = parser.parse(
            """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical" />
            """.trimIndent(),
            "linear_layout"
        ).root
        val frameLayout = parser.parse(
            """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical" />
            """.trimIndent(),
            "frame_layout"
        ).root

        assertThat(registry.isSupportedAttribute(linearLayout, null, "android:orientation")).isTrue()
        assertThat(registry.isSupportedAttribute(frameLayout, null, "android:orientation")).isFalse()
    }

    @Test
    fun `unsupported attribute values are rejected`() {
        val textView = parser.parse(
            """
                <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textSize="foo" />
            """.trimIndent(),
            "bad_text_size"
        ).root

        assertThat(registry.hasUnsupportedAttributeValue(textView, null)).isTrue()
    }

    @Test
    fun `force fallback attributes still mark node fallback through analyzer`() {
        val textView = parser.parse(
            """
                <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                    style="@style/TextAppearance.Title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            """.trimIndent(),
            "style_fallback"
        ).root

        val analyzed = analyzer.analyze(textView)

        assertThat(registry.forceFallbackAttributes).contains("style")
        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `relative layout rules are invalid outside relative layout parent`() {
        val frameLayout = parser.parse(
            """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_below="@id/title" />
                </FrameLayout>
            """.trimIndent(),
            "bad_relative_rule"
        ).root

        val analyzed = analyzer.analyze(frameLayout)

        assertThat(registry.hasInvalidRelativeLayoutParamForNode(frameLayout.children[0], frameLayout.tagName)).isTrue()
        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FALLBACK)
    }

    @Test
    fun `supported non layout attributes are emitted or explicitly non emitting`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:id="@+id/root"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:visibility="visible"
                android:background="#FF0000"
                android:padding="8dp">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/app_name"
                    android:textColor="#112233"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:gravity="center"
                    android:enabled="true"
                    android:clickable="false"
                    android:focusable="false"
                    android:elevation="2dp"
                    android:minWidth="12dp"
                    android:minHeight="8dp" />
                <EditText
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:hint="Name"
                    android:inputType="textPersonName" />
                <ImageView
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:src="@drawable/ic_demo"
                    android:scaleType="centerCrop"
                    app:tint="@color/demo_tint" />
                <ScrollView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:fillViewport="true" />
                <androidx.recyclerview.widget.RecyclerView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />
            </LinearLayout>
        """.trimIndent()

        val root = analyzer.analyze(parser.parse(xml, "registry_contract").root)
        val nodes = flatten(root)
        val nonEmittingAttrs = setOf("app:layoutManager")

        val supportedNonLayoutAttrs = nodes.flatMap { node ->
            node.supportedAttributes
                .filterNot { it.startsWith("android:layout_") }
                .filterNot { it.startsWith("tools:") }
                .filterNot { it.startsWith("xmlns:") }
                .map { node to it }
        }

        assertThat(supportedNonLayoutAttrs.map { it.second }).containsAtLeast(
            "android:id",
            "android:orientation",
            "android:visibility",
            "android:background",
            "android:padding",
            "android:text",
            "android:textColor",
            "android:textSize",
            "android:textStyle",
            "android:gravity",
            "android:hint",
            "android:inputType",
            "android:src",
            "android:scaleType",
            "app:tint",
            "android:fillViewport",
            "app:layoutManager"
        )
        assertThat(
            supportedNonLayoutAttrs.filterNot { (node, attrName) ->
                registry.canEmitAttribute(node, attrName) || attrName in nonEmittingAttrs
            }
        ).isEmpty()
    }

    private fun flatten(node: AnalyzedNode): List<AnalyzedNode> {
        return listOf(node) + node.children.flatMap(::flatten)
    }
}
