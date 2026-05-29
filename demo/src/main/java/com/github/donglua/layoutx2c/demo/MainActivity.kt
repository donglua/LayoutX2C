package com.github.donglua.layoutx2c.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsetsToContent()

        showGeneratedEntryStatus()
        setupButtons()
    }

    private fun showGeneratedEntryStatus() {
        val demos = DemoLayoutCatalog.entries
        val total = demos.size
        val lines = demos.joinToString("\n") { demo ->
            "  ✓ ${demo.layoutName} → ${demo.generatedClassName.replace("Layout_", "")}X2C"
        }
        findViewById<TextView>(R.id.registry_status).text =
            "Direct generated entries: $total/$total\n$lines"
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_benchmark).setOnClickListener {
            startActivity(Intent(this, BenchmarkActivity::class.java))
        }
        findViewById<Button>(R.id.btn_code_viewer).setOnClickListener {
            startActivity(Intent(this, CodeViewerActivity::class.java))
        }
        findViewById<Button>(R.id.btn_databinding_enhanced).setOnClickListener {
            startActivity(Intent(this, DataBindingEnhancedActivity::class.java))
        }
    }
}
