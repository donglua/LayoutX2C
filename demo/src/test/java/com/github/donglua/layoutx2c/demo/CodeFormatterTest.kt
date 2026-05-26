package com.github.donglua.layoutx2c.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFormatterTest {

    @Test
    fun `adds aligned line numbers`() {
        val formatted = CodeFormatter.withLineNumbers("first\nsecond\nthird")

        assertEquals("1\n2\n3", formatted.lineNumbers)
        assertEquals("first\nsecond\nthird", formatted.code)
        assertEquals("3 lines", formatted.summary)
    }

    @Test
    fun `keeps trailing empty line visible`() {
        val formatted = CodeFormatter.withLineNumbers("first\n")

        assertEquals("1\n2", formatted.lineNumbers)
        assertEquals("first\n", formatted.code)
        assertEquals("2 lines", formatted.summary)
    }
}
