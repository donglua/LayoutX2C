package com.github.donglua.layoutx2c.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkProgressFormatterTest {

    @Test
    fun `shows running stage before final result is available`() {
        val formatter = BenchmarkProgressFormatter(iterations = 100, warmup = 10)
            .onLayoutStarted("demo_simple")
            .onInflaterMeasured(12)

        assertEquals(
            """
            ▸ demo_simple
              LayoutInflater  12ms  avg 0.12ms
              LayoutX2C      running…
            """.trimIndent(),
            formatter.toString()
        )
    }

    @Test
    fun `keeps completed entries while appending next layout progress`() {
        val formatter = BenchmarkProgressFormatter(iterations = 100, warmup = 10)
            .onLayoutStarted("demo_simple")
            .onInflaterMeasured(12)
            .onGeneratedMeasured(4)
            .onLayoutStarted("demo_nested")

        assertEquals(
            """
            ▸ demo_simple
              LayoutInflater  12ms  avg 0.12ms
              LayoutX2C      4ms  avg 0.04ms
              Speedup: 3.0x faster

            ▸ demo_nested
              LayoutInflater  running…
            """.trimIndent(),
            formatter.toString()
        )
    }

    @Test
    fun `adds iteration footer after benchmark completes`() {
        val formatter = BenchmarkProgressFormatter(iterations = 100, warmup = 10)
            .onLayoutStarted("demo_simple")
            .onInflaterMeasured(12)
            .onGeneratedMeasured(4)
            .onComplete()

        assertEquals(
            """
            ▸ demo_simple
              LayoutInflater  12ms  avg 0.12ms
              LayoutX2C      4ms  avg 0.04ms
              Speedup: 3.0x faster

            Iterations: 100  Warmup: 10
            """.trimIndent(),
            formatter.toString()
        )
    }
}
