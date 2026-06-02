package com.github.donglua.layoutx2c.resources

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.analyzer.SupportLevel
import com.github.donglua.layoutx2c.codegen.DefaultLayoutParamsEmitter
import com.github.donglua.layoutx2c.codegen.LayoutCodeGenerator
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.github.donglua.layoutx2c.registry.ResourceAwareViewRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResourceReferenceCodegenTest {

    private val parser = XmlLayoutParser()

    @Test
    fun `external color reference falls back instead of generating current module R symbol`() {
        val resolver = StaticResourceReferenceResolver.currentModule(
            currentPackageName = "com.example",
            symbols = ResourceSymbolTable(emptySet())
        )
        val registry = ResourceAwareViewRegistry(
            rPackageName = "com.example",
            resourceResolver = resolver
        )
        val analyzer = LayoutAnalyzer(registry)
        val generator = generator(registry, resolver)
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:background="@color/from_dependency">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Title" />
            </LinearLayout>
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "external_color").root)
        val generated = generator.generate(analyzed, "external_color", "R.layout.external_color").toString()

        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FALLBACK)
        assertThat(generated).contains("FallbackInflater.inflate(context, R.layout.external_color, parent)")
        assertThat(generated).doesNotContain("R.color.from_dependency")
    }

    @Test
    fun `current module color reference keeps current R symbol`() {
        val resolver = StaticResourceReferenceResolver.currentModule(
            currentPackageName = "com.example",
            symbols = ResourceSymbolTable(setOf(ResourceReference("color", "local_divider")))
        )
        val registry = ResourceAwareViewRegistry(
            rPackageName = "com.example",
            resourceResolver = resolver
        )
        val analyzer = LayoutAnalyzer(registry)
        val generator = generator(registry, resolver)
        val xml = """
            <View xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="@color/local_divider" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "local_color").root)
        val generated = generator.generate(analyzed, "local_color", "R.layout.local_color").toString()

        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(generated).contains("ContextCompat.getColor(context, R.color.local_divider)")
        assertThat(generated).doesNotContain("FallbackInflater.inflate(context, R.layout.local_color, parent)")
    }

    @Test
    fun `known owner color reference generates fully qualified owner R symbol`() {
        val resolver = StaticResourceReferenceResolver(
            owners = mapOf(ResourceReference("color", "base_divider") to "com.example.base"),
            currentPackageName = "com.example"
        )
        val registry = ResourceAwareViewRegistry(
            rPackageName = "com.example",
            resourceResolver = resolver
        )
        val analyzer = LayoutAnalyzer(registry)
        val generator = generator(registry, resolver)
        val xml = """
            <View xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="@color/base_divider" />
        """.trimIndent()

        val analyzed = analyzer.analyze(parser.parse(xml, "owned_color").root)
        val generated = generator.generate(analyzed, "owned_color", "R.layout.owned_color").toString()

        assertThat(analyzed.supportLevel).isEqualTo(SupportLevel.FULL)
        assertThat(generated).contains("ContextCompat.getColor(context, com.example.base.R.color.base_divider)")
        assertThat(generated).doesNotContain("ContextCompat.getColor(context, R.color.base_divider)")
    }

    private fun generator(
        registry: ResourceAwareViewRegistry,
        resolver: ResourceReferenceResolver
    ): LayoutCodeGenerator {
        return LayoutCodeGenerator(
            packageName = "com.example.generated",
            rPackageName = "com.example",
            layoutParamsEmitter = DefaultLayoutParamsEmitter(
                rPackageName = "com.example",
                resourceResolver = resolver
            ),
            viewRegistry = registry
        )
    }
}
