package com.github.donglua.layoutx2c.demo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.donglua.layoutx2c.runtime.LayoutX2CRegistry

/**
 * Benchmark：对比 LayoutInflater vs LayoutX2C generated factory 的耗时。
 */
class BenchmarkActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LayoutX2C-Benchmark"
        private const val ITERATIONS = 100
        private const val WARMUP = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)
        applySystemBarInsetsToContent()

        val resultView = findViewById<TextView>(R.id.benchmark_result)

        findViewById<Button>(R.id.btn_run).setOnClickListener {
            resultView.text = getString(R.string.benchmark_running)
            resultView.post {
                val results = runBenchmarks()
                resultView.text = results
                Log.i(TAG, results)
            }
        }
    }

    private fun runBenchmarks(): String {
        val container = FrameLayout(this)
        val sb = StringBuilder()

        val layouts = listOf(
            "demo_simple"  to R.layout.demo_simple,
            "demo_nested"  to R.layout.demo_nested,
            "demo_form"    to R.layout.demo_form,
            "demo_relative" to R.layout.demo_relative,
        )

        for ((name, layoutId) in layouts) {
            val hasGenerated = LayoutX2CRegistry.has(this, layoutId)

            sb.appendLine("▸ $name")

            val inflaterTime = benchmark {
                LayoutInflater.from(this).inflate(layoutId, container, false)
                container.removeAllViews()
            }
            sb.appendLine("  LayoutInflater  ${inflaterTime}ms  avg ${fmtAvg(inflaterTime)}ms")

            if (hasGenerated) {
                val generatedTime = benchmark {
                    LayoutX2CRegistry.inflate(this, layoutId, container)
                    container.removeAllViews()
                }
                sb.appendLine("  LayoutX2C      ${generatedTime}ms  avg ${fmtAvg(generatedTime)}ms")

                val speedup = if (generatedTime > 0) {
                    String.format("%.1fx", inflaterTime.toFloat() / generatedTime)
                } else "∞"
                sb.appendLine("  Speedup: $speedup faster")
            } else {
                sb.appendLine("  LayoutX2C: not registered (run KSP build)")
            }
            sb.appendLine()
        }

        sb.appendLine("Iterations: $ITERATIONS  Warmup: $WARMUP")
        return sb.toString().trimEnd()
    }

    private fun fmtAvg(totalMs: Long) =
        String.format("%.2f", totalMs.toFloat() / ITERATIONS)

    private fun benchmark(block: () -> Unit): Long {
        repeat(WARMUP) { block() }
        val start = System.nanoTime()
        repeat(ITERATIONS) { block() }
        return (System.nanoTime() - start) / 1_000_000
    }
}
