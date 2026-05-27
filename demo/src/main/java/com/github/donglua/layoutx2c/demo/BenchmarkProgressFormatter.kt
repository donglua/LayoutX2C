package com.github.donglua.layoutx2c.demo

internal class BenchmarkProgressFormatter(
    private val iterations: Int,
    private val warmup: Int,
) {
    private val entries = mutableListOf<EntryProgress>()
    private var complete = false

    fun onLayoutStarted(layoutName: String): BenchmarkProgressFormatter {
        entries += EntryProgress(layoutName)
        return this
    }

    fun onInflaterMeasured(totalMs: Long): BenchmarkProgressFormatter {
        entries.lastOrNull()?.inflaterMs = totalMs
        return this
    }

    fun onGeneratedMeasured(totalMs: Long): BenchmarkProgressFormatter {
        entries.lastOrNull()?.generatedMs = totalMs
        return this
    }

    fun onComplete(): BenchmarkProgressFormatter {
        complete = true
        return this
    }

    override fun toString(): String {
        val sb = StringBuilder()
        entries.forEachIndexed { index, entry ->
            if (index > 0) sb.appendLine()
            sb.appendLine("▸ ${entry.layoutName}")

            val inflaterMs = entry.inflaterMs
            if (inflaterMs == null) {
                sb.appendLine("  LayoutInflater  running…")
                return@forEachIndexed
            }
            sb.appendLine("  LayoutInflater  ${inflaterMs}ms  avg ${fmtAvg(inflaterMs)}ms")

            val generatedMs = entry.generatedMs
            if (generatedMs == null) {
                sb.appendLine("  LayoutX2C      running…")
                return@forEachIndexed
            }
            sb.appendLine("  LayoutX2C      ${generatedMs}ms  avg ${fmtAvg(generatedMs)}ms")

            val speedup = if (generatedMs > 0) {
                String.format("%.1fx", inflaterMs.toFloat() / generatedMs)
            } else {
                "∞"
            }
            sb.appendLine("  Speedup: $speedup faster")
        }

        if (complete) {
            if (sb.isNotEmpty()) sb.appendLine()
            sb.appendLine("Iterations: $iterations  Warmup: $warmup")
        }

        return sb.toString().trimEnd()
    }

    private fun fmtAvg(totalMs: Long) =
        String.format("%.2f", totalMs.toFloat() / iterations)

    private data class EntryProgress(
        val layoutName: String,
        var inflaterMs: Long? = null,
        var generatedMs: Long? = null,
    )
}
