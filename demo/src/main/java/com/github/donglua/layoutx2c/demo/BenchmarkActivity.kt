package com.github.donglua.layoutx2c.demo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.donglua.layoutx2c.runtime.LayoutX2CRegistry

/**
 * 简单 benchmark：对比 LayoutInflater vs LayoutX2C generated factory 的耗时。
 */
class BenchmarkActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LayoutX2C-Benchmark"
        private const val ITERATIONS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(this)
        setContentView(container)

        val results = StringBuilder()

        // Benchmark: LayoutInflater
        val inflaterTime = benchmark {
            val view = LayoutInflater.from(this).inflate(R.layout.activity_simple, container, false)
            container.removeAllViews()
        }
        results.appendLine("LayoutInflater: ${inflaterTime}ms (avg ${inflaterTime / ITERATIONS}ms)")

        // Benchmark: LayoutX2C generated
        val generatedTime = benchmark {
            val view = LayoutX2CRegistry.inflate(this, R.layout.activity_simple, container)
            container.removeAllViews()
        }
        results.appendLine("LayoutX2C: ${generatedTime}ms (avg ${generatedTime / ITERATIONS}ms)")

        // 显示结果
        val resultView = TextView(this).apply {
            text = results.toString()
            setPadding(32, 32, 32, 32)
            textSize = 14f
        }
        container.addView(resultView)

        Log.i(TAG, results.toString())
    }

    private fun benchmark(block: () -> Unit): Long {
        // Warmup
        repeat(5) { block() }

        val start = System.nanoTime()
        repeat(ITERATIONS) { block() }
        val elapsed = (System.nanoTime() - start) / 1_000_000 // ms
        return elapsed
    }
}
