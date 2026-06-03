package com.github.donglua.layoutx2c.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLayoutCatalogTest {

    @Test
    fun `catalog stays aligned with generated config layout order`() {
        assertEquals(LayoutX2CConfig.layouts.toList(), DemoLayoutCatalog.entries.map { it.layoutResId })
    }

    @Test
    fun `catalog includes fallback demo`() {
        val fallbackDemo = DemoLayoutCatalog.entries.singleOrNull { it.layoutName == "demo_fallback" }

        assertEquals("Fallback", fallbackDemo?.label)
        assertEquals("Layout_DemoFallback", fallbackDemo?.generatedClassName)
        assertEquals(DemoLayoutCatalog.Status.Fallback, fallbackDemo?.status)
        assertTrue(DemoLayoutCatalog.entries.map { it.layoutName }.contains("demo_fallback"))
    }

    @Test
    fun `catalog includes data binding demo in platform comparisons`() {
        val bindingDemo = DemoLayoutCatalog.entries.singleOrNull { it.layoutName == "demo_data_binding" }

        assertEquals("Binding", bindingDemo?.label)
        assertEquals("DemoDataBindingX2CBinding", bindingDemo?.codeViewerClassName)
        assertEquals(DemoLayoutCatalog.Status.Binding, bindingDemo?.status)
        assertEquals(true, bindingDemo?.platformInflatable)
    }

    @Test
    fun `catalog includes constraint demo for safe subset showcase`() {
        val constraintDemo = DemoLayoutCatalog.entries.singleOrNull { it.layoutName == "demo_constraint" }

        assertEquals("Constraint", constraintDemo?.label)
        assertEquals("Layout_DemoConstraint", constraintDemo?.generatedClassName)
        assertEquals(DemoLayoutCatalog.Status.Generated, constraintDemo?.status)
        assertEquals(true, constraintDemo?.platformInflatable)
        assertTrue(DemoLayoutCatalog.entries.map { it.layoutName }.contains("demo_constraint"))
    }

    @Test
    fun `catalog includes include merge and viewstub demo`() {
        val includeDemo = DemoLayoutCatalog.entries.singleOrNull { it.layoutName == "demo_include" }

        assertEquals("Include", includeDemo?.label)
        assertEquals("Layout_DemoInclude", includeDemo?.generatedClassName)
        assertEquals(true, includeDemo?.platformInflatable)
    }

    @Test
    fun `catalog includes enhanced data binding demo for code viewer`() {
        val bindingDemo = DemoLayoutCatalog.entries.singleOrNull {
            it.layoutName == "demo_data_binding_enhanced"
        }

        assertEquals("Binding Enhanced", bindingDemo?.label)
        assertEquals("DemoDataBindingEnhancedX2CBinding", bindingDemo?.codeViewerClassName)
        assertEquals(DemoLayoutCatalog.Status.Binding, bindingDemo?.status)
        assertEquals(true, bindingDemo?.platformInflatable)
    }

    @Test
    fun `catalog lookup resolves entries by layout name`() {
        val simpleDemo = DemoLayoutCatalog.requireByLayoutName("demo_simple")

        assertEquals("Simple", simpleDemo.label)
        assertEquals("demo_simple", simpleDemo.layoutName)
    }

    @Test
    fun `catalog includes scroll image demo for supported scroll and image attributes`() {
        val scrollImageDemo = DemoLayoutCatalog.entries.singleOrNull {
            it.layoutName == "demo_scroll_image"
        }

        assertEquals("Scroll + Image", scrollImageDemo?.label)
        assertEquals("Layout_DemoScrollImage", scrollImageDemo?.generatedClassName)
        assertEquals(DemoLayoutCatalog.Status.Generated, scrollImageDemo?.status)
        assertTrue(scrollImageDemo?.summary.orEmpty().contains("ScrollView"))
    }

    @Test
    fun `code viewer selector covers every catalog entry`() {
        assertEquals(
            DemoLayoutCatalog.entries.map { it.layoutName },
            CodeViewerSelector.entries.map { it.layoutName },
        )
    }
}
