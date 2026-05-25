package com.github.donglua.layoutx2c.demo

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.donglua.layoutx2c.runtime.LayoutX2CRegistry

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootView = LayoutX2CRegistry.inflate(
            context = this,
            layoutId = R.layout.activity_main,
            parent = null
        )
        setContentView(rootView)

        styleHome()
        showRegistryStatus()
        setupButtons()
    }

    private fun styleHome() {
        findViewById<TextView>(R.id.title).apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        findViewById<TextView>(R.id.subtitle).textSize = 14f
        findViewById<TextView>(R.id.result).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
    }

    private fun showRegistryStatus() {
        val resultView = findViewById<TextView>(R.id.result)
        val layouts = listOf("activity_main", "activity_simple", "activity_nested")
        val registered = layouts.filter { name ->
            val resId = resources.getIdentifier(name, "layout", packageName)
            resId != 0 && LayoutX2CRegistry.has(this, resId)
        }

        val text = buildString {
            append("Registered: ${registered.size}/${layouts.size}\n")
            for (name in layouts) {
                val resId = resources.getIdentifier(name, "layout", packageName)
                val status = if (resId != 0 && LayoutX2CRegistry.has(this@MainActivity, resId)) "OK" else "--"
                append("  $status $name\n")
            }
        }
        resultView.text = text.trimEnd()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_benchmark).setOnClickListener {
            startActivity(Intent(this, BenchmarkActivity::class.java))
        }

        findViewById<Button>(R.id.btn_simple_layout).setOnClickListener {
            startActivity(
                PreviewActivity.intent(this, R.layout.activity_simple, "activity_simple")
            )
        }

        findViewById<Button>(R.id.btn_nested_layout).setOnClickListener {
            startActivity(
                PreviewActivity.intent(this, R.layout.activity_nested, "activity_nested")
            )
        }
    }
}
