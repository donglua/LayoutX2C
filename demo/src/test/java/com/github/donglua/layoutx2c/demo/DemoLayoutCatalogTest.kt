package com.github.donglua.layoutx2c.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLayoutCatalogTest {

    @Test
    fun `catalog includes fallback demo`() {
        val fallbackDemo = DemoLayoutCatalog.entries.singleOrNull { it.layoutName == "demo_fallback" }

        assertEquals("Fallback", fallbackDemo?.label)
        assertEquals("Layout_DemoFallback", fallbackDemo?.generatedClassName)
        assertTrue(DemoLayoutCatalog.entries.map { it.layoutName }.contains("demo_fallback"))
    }

    @Test
    fun `catalog includes data binding demo in platform comparisons`() {
        val bindingDemo = DemoLayoutCatalog.entries.singleOrNull { it.layoutName == "demo_data_binding" }

        assertEquals("Binding", bindingDemo?.label)
        assertEquals("DemoDataBindingX2CBinding", bindingDemo?.codeViewerClassName)
        assertEquals(true, bindingDemo?.platformInflatable)
    }

    @Test
    fun `catalog includes constraint demo for safe subset showcase`() {
        val constraintDemo = DemoLayoutCatalog.entries.singleOrNull { it.layoutName == "demo_constraint" }

        assertEquals("Constraint", constraintDemo?.label)
        assertEquals("Layout_DemoConstraint", constraintDemo?.generatedClassName)
        assertEquals(true, constraintDemo?.platformInflatable)
        assertTrue(DemoLayoutCatalog.entries.map { it.layoutName }.contains("demo_constraint"))
    }
}
