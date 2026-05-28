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

    private enum class BenchmarkStage {
        START_LAYOUT,
        INFLATE_XML,
        INFLATE_GENERATED,
        NEXT_LAYOUT,
        COMPLETE,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)
        applySystemBarInsetsToContent()

        val resultView = findViewById<TextView>(R.id.benchmark_result)

        findViewById<Button>(R.id.btn_run).setOnClickListener {
            resultView.text = getString(R.string.benchmark_running)
            it.isEnabled = false
            runBenchmarksProgressively(resultView, it as Button)
        }
    }

    private fun runBenchmarksProgressively(resultView: TextView, runButton: Button) {
        val container = FrameLayout(this)
        val formatter = BenchmarkProgressFormatter(ITERATIONS, WARMUP)
        var index = 0
        var stage = BenchmarkStage.START_LAYOUT

        fun render() {
            resultView.text = formatter.toString()
        }

        fun step() {
            when (stage) {
                BenchmarkStage.START_LAYOUT -> {
                    formatter.onLayoutStarted(DemoLayoutCatalog.entries[index].layoutName)
                    render()
                    stage = BenchmarkStage.INFLATE_XML
                    resultView.post(::step)
                }
                BenchmarkStage.INFLATE_XML -> {
                    val demo = DemoLayoutCatalog.entries[index]
                    if (demo.platformInflatable) {
                        val inflaterTime = benchmark {
                            LayoutInflater.from(this).inflate(demo.layoutResId, container, false)
                            container.removeAllViews()
                        }
                        formatter.onInflaterMeasured(inflaterTime)
                    } else {
                        formatter.onInflaterUnavailable()
                    }
                    render()
                    stage = BenchmarkStage.INFLATE_GENERATED
                    resultView.post(::step)
                }
                BenchmarkStage.INFLATE_GENERATED -> {
                    val demo = DemoLayoutCatalog.entries[index]
                    val generatedTime = benchmark {
                        demo.generatedInflater(this, container)
                        container.removeAllViews()
                    }
                    formatter.onGeneratedMeasured(generatedTime)
                    render()
                    stage = BenchmarkStage.NEXT_LAYOUT
                    resultView.post(::step)
                }
                BenchmarkStage.NEXT_LAYOUT -> {
                    index += 1
                    stage = if (index < DemoLayoutCatalog.entries.size) {
                        BenchmarkStage.START_LAYOUT
                    } else {
                        BenchmarkStage.COMPLETE
                    }
                    resultView.post(::step)
                }
                BenchmarkStage.COMPLETE -> {
                    formatter.onComplete()
                    val results = formatter.toString()
                    resultView.text = results
                    runButton.isEnabled = true
                    Log.i(TAG, results)
                }
            }
        }

        resultView.post(::step)
    }

    private fun benchmark(block: () -> Unit): Long {
        repeat(WARMUP) { block() }
        val start = System.nanoTime()
        repeat(ITERATIONS) { block() }
        return (System.nanoTime() - start) / 1_000_000
    }
}
