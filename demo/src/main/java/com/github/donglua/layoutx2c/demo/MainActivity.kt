package com.github.donglua.layoutx2c.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.donglua.layoutx2c.runtime.LayoutX2CRegistry

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsetsToContent()

        showRegistryStatus()
        setupButtons()
    }

    private fun showRegistryStatus() {
        val demos = listOf(
            "demo_simple" to R.layout.demo_simple,
            "demo_nested" to R.layout.demo_nested,
            "demo_form"   to R.layout.demo_form,
            "demo_relative" to R.layout.demo_relative,
            "demo_recycler" to R.layout.demo_recycler,
        )
        val registered = demos.count { (_, id) -> LayoutX2CRegistry.has(this, id) }
        val total = demos.size
        val lines = demos.joinToString("\n") { (name, id) ->
            val ok = if (LayoutX2CRegistry.has(this, id)) "✓" else "–"
            "  $ok $name"
        }
        findViewById<TextView>(R.id.registry_status).text =
            "Registry: $registered/$total\n$lines"
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_benchmark).setOnClickListener {
            startActivity(Intent(this, BenchmarkActivity::class.java))
        }
        findViewById<Button>(R.id.btn_code_viewer).setOnClickListener {
            startActivity(Intent(this, CodeViewerActivity::class.java))
        }
    }
}
