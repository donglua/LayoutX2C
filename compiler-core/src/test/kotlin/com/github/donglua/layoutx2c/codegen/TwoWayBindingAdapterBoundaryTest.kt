package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TwoWayBindingAdapterBoundaryTest {

    @Test
    fun `two-way binding adapter is unsupported without an inverse adapter contract`() {
        val bindingAdapters = listOf(
            BindingAdapterDescriptor(
                attrs = listOf("app:stateColorRes"),
                methodClassName = "com.example.databinding.SampleBindingAdapters",
                methodName = "setStateColor"
            )
        )
        val tree = XmlLayoutParser().parse(
            """
                <layout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto">
                    <data>
                        <variable name="vm" type="com.example.StateViewModel" />
                    </data>
                    <View
                        android:id="@+id/state_indicator"
                        android:layout_width="6dp"
                        android:layout_height="6dp"
                        app:stateColorRes="@={vm.colorRes}" />
                </layout>
            """.trimIndent(),
            "item_two_way_binding_adapter"
        )
        val analyzed = LayoutAnalyzerV2(bindingAdapters = bindingAdapters).analyze(tree.root)

        val generated = BindingFacadeGeneratorV2(
            packageName = "com.example.generated",
            rPackageName = "com.example",
            bindingAdapters = bindingAdapters
        ).generate(
            analyzedRoot = analyzed,
            layoutName = "item_two_way_binding_adapter",
            layoutResId = "R.layout.item_two_way_binding_adapter",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables
        ).toString()

        assertThat(analyzed.unsupportedAttributes).contains("app:stateColorRes")
        assertThat(analyzed.twoWayBindingAttributes).doesNotContain("app:stateColorRes")
        assertThat(generated).doesNotContain("SampleBindingAdapters.setStateColor")
    }
}
