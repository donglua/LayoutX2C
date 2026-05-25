package com.github.donglua.layoutx2c.demo

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.donglua.layoutx2c.runtime.LayoutX2CRegistry

/**
 * Benchmark：对比 LayoutInflater vs LayoutX2C generated factory 的耗时。
 * 分别测试 activity_simple（简单）和 activity_nested（嵌套）两个 layout。
 */
class BenchmarkActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LayoutX2C-Benchmark"
        private const val ITERATIONS = 100
        private const val WARMUP = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
        }

        val title = TextView(this).apply {
            text = getString(R.string.benchmark_title)
            setPadding(0, 0, 0, 16.dp)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title, lp())

        val resultView = TextView(this).apply {
            text = getString(R.string.benchmark_running)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setLineSpacing(4f, 1.2f)
        }
        root.addView(resultView, lp())

        setContentView(root)

        // Run benchmark on next frame to let UI render first
        root.post {
            val results = runBenchmarks()
            resultView.text = results
            Log.i(TAG, results)
        }
    }

    private fun runBenchmarks(): String {
        val container = FrameLayout(this)
        val sb = StringBuilder()

        val layouts = listOf(
            "activity_simple" to R.layout.activity_simple,
            "activity_nested" to R.layout.activity_nested
        )

        for ((name, layoutId) in layouts) {
            val hasGenerated = LayoutX2CRegistry.has(layoutId)

            sb.appendLine("$name")

            val inflaterTime = benchmark {
                LayoutInflater.from(this).inflate(layoutId, container, false)
                container.removeAllViews()
            }
            sb.appendLine("  LayoutInflater: ${inflaterTime}ms total, avg ${String.format("%.2f", inflaterTime.toFloat() / ITERATIONS)}ms")

            if (hasGenerated) {
                val generatedTime = benchmark {
                    LayoutX2CRegistry.inflate(this, layoutId, container)
                    container.removeAllViews()
                }
                sb.appendLine("  LayoutX2C:      ${generatedTime}ms total, avg ${String.format("%.2f", generatedTime.toFloat() / ITERATIONS)}ms")

                val speedup = if (generatedTime > 0) {
                    String.format("%.1fx", inflaterTime.toFloat() / generatedTime)
                } else "∞"
                sb.appendLine("  Speedup: $speedup faster")
            } else {
                sb.appendLine("  LayoutX2C: not registered (run KSP build)")
            }
            sb.appendLine()
        }

        sb.appendLine("Iterations: $ITERATIONS, warmup: $WARMUP")
        return sb.toString().trimEnd()
    }

    private fun benchmark(block: () -> Unit): Long {
        repeat(WARMUP) { block() }

        val start = System.nanoTime()
        repeat(ITERATIONS) { block() }
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}
