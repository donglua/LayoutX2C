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
    fun `catalog marks data binding demo as generated only`() {
        val bindingDemo = DemoLayoutCatalog.entries.singleOrNull { it.layoutName == "demo_data_binding" }

        assertEquals("Binding", bindingDemo?.label)
        assertEquals("DemoDataBindingX2CBinding", bindingDemo?.codeViewerClassName)
        assertEquals(false, bindingDemo?.platformInflatable)
    }
}
