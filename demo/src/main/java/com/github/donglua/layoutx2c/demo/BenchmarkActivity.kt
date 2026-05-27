package com.github.donglua.layoutx2c.demo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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

        for (demo in DemoLayoutCatalog.entries) {
            val layoutId = demo.layoutResId

            sb.appendLine("▸ ${demo.layoutName}")

            val inflaterTime = benchmark {
                LayoutInflater.from(this).inflate(layoutId, container, false)
                container.removeAllViews()
            }
            sb.appendLine("  LayoutInflater  ${inflaterTime}ms  avg ${fmtAvg(inflaterTime)}ms")

            val generatedTime = benchmark {
                demo.generatedInflater(this, container)
                container.removeAllViews()
            }
            sb.appendLine("  LayoutX2C      ${generatedTime}ms  avg ${fmtAvg(generatedTime)}ms")

            val speedup = if (generatedTime > 0) {
                String.format("%.1fx", inflaterTime.toFloat() / generatedTime)
            } else "∞"
            sb.appendLine("  Speedup: $speedup faster")
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
