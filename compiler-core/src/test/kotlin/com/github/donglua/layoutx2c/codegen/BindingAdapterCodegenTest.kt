package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BindingAdapterCodegenTest {

    private val bindingAdapters = listOf(
        BindingAdapterDescriptor(
            attrs = listOf("app:stateColorRes", "app:stateSizeDp"),
            methodClassName = "com.example.databinding.SampleBindingAdapters",
            methodName = "setViewState",
            requireAll = true
        )
    )

    @Test
    fun `declared binding adapter expression emits adapter call`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto">
                <data>
                    <import type="com.example.shared.R" />
                </data>
                <FrameLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <View
                        android:id="@+id/state_indicator"
                        android:layout_width="6dp"
                        android:layout_height="6dp"
                        app:stateColorRes="@{R.color.sample_state}"
                        app:stateSizeDp="@{3F}" />
                </FrameLayout>
            </layout>
        """.trimIndent()

        val tree = XmlLayoutParser().parse(xml, "item_sample_binding_adapter")
        val analyzed = LayoutAnalyzerV2(bindingAdapters = bindingAdapters).analyze(tree.root)
        val generated = BindingFacadeGeneratorV2(
            packageName = "com.example.generated",
            rPackageName = "com.example",
            bindingAdapters = bindingAdapters
        ).generate(
            analyzedRoot = analyzed,
            layoutName = "item_sample_binding_adapter",
            layoutResId = "R.layout.item_sample_binding_adapter",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables,
            dataBindingImports = tree.rootMetadata.dataBindingImports
        ).toString()

        assertThat(analyzed.children.single().unsupportedAttributes).isEmpty()
        assertThat(generated).contains(
            "SampleBindingAdapters.setViewState(stateIndicator, com.example.shared.R.color.sample_state, 3f)"
        )
    }

    @Test
    fun `binding adapter descriptor without namespace matches app attribute`() {
        val bindingAdaptersWithoutNamespace = listOf(
            BindingAdapterDescriptor(
                attrs = listOf("stateColorRes", "stateSizeDp"),
                methodClassName = "com.example.databinding.SampleBindingAdapters",
                methodName = "setViewState",
                requireAll = true
            )
        )
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto">
                <data>
                    <import type="com.example.shared.R" />
                </data>
                <FrameLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <View
                        android:id="@+id/state_indicator"
                        android:layout_width="6dp"
                        android:layout_height="6dp"
                        app:stateColorRes="@{R.color.sample_state}"
                        app:stateSizeDp="@{3F}" />
                </FrameLayout>
            </layout>
        """.trimIndent()

        val tree = XmlLayoutParser().parse(xml, "item_sample_binding_adapter")
        val analyzed = LayoutAnalyzerV2(bindingAdapters = bindingAdaptersWithoutNamespace).analyze(tree.root)
        val generated = BindingFacadeGeneratorV2(
            packageName = "com.example.generated",
            rPackageName = "com.example",
            bindingAdapters = bindingAdaptersWithoutNamespace
        ).generate(
            analyzedRoot = analyzed,
            layoutName = "item_sample_binding_adapter",
            layoutResId = "R.layout.item_sample_binding_adapter",
            useFastPath = true,
            dataBindingVariables = tree.rootMetadata.dataBindingVariables,
            dataBindingImports = tree.rootMetadata.dataBindingImports
        ).toString()

        assertThat(analyzed.children.single().unsupportedAttributes).isEmpty()
        assertThat(generated).contains(
            "SampleBindingAdapters.setViewState(stateIndicator, com.example.shared.R.color.sample_state, 3f)"
        )
    }

    @Test
    fun `undeclared binding adapter expression remains unsupported`() {
        val xml = """
            <layout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto">
                <FrameLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <View
                        android:id="@+id/state_indicator"
                        android:layout_width="6dp"
                        android:layout_height="6dp"
                        app:stateColorRes="@{R.color.sample_state}" />
                </FrameLayout>
            </layout>
        """.trimIndent()

        val tree = XmlLayoutParser().parse(xml, "item_unsupported_binding_adapter")
        val analyzed = LayoutAnalyzerV2().analyze(tree.root)

        assertThat(analyzed.children.single().unsupportedAttributes).contains("app:stateColorRes")
    }
}
