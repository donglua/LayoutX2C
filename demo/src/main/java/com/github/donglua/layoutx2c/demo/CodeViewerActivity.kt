package com.github.donglua.layoutx2c.demo

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Code Viewer：对照展示 XML 源码与 KSP 生成的 Kotlin 代码。
 *
 * XML 从 assets（未来方案）或 raw resource 读取；
 * Kotlin 从 build/generated/ksp/ 目录中查找对应文件。
 */
class CodeViewerActivity : AppCompatActivity() {

    // 每个 demo 对应的 xml res id 和 layout 名称（用于查找生成文件）
    private data class DemoEntry(
        val label: String,
        val layoutName: String,   // e.g. "demo_simple"
        val xmlResId: Int,
        val generatedClassName: String // KotlinPoet 生成的类名
    )

    private val demos = listOf(
        DemoEntry("Simple", "demo_simple", R.layout.demo_simple, "Layout_DemoSimple"),
        DemoEntry("Nested", "demo_nested", R.layout.demo_nested, "Layout_DemoNested"),
        DemoEntry("Form",   "demo_form",   R.layout.demo_form,   "Layout_DemoForm"),
        DemoEntry("Relative", "demo_relative", R.layout.demo_relative, "Layout_DemoRelative"),
    )

    private var currentDemo = demos[0]
    private var showingXml = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_viewer)
        applySystemBarInsetsToContent()

        setupLayoutSelector()
        setupTabSwitch()
        showDemo(demos[0])
    }

    private fun setupLayoutSelector() {
        val btnSimple = findViewById<Button>(R.id.btn_demo_simple)
        val btnNested = findViewById<Button>(R.id.btn_demo_nested)
        val btnForm   = findViewById<Button>(R.id.btn_demo_form)
        val btnRelative = findViewById<Button>(R.id.btn_demo_relative)

        btnSimple.setOnClickListener { showDemo(demos[0]) }
        btnNested.setOnClickListener { showDemo(demos[1]) }
        btnForm.setOnClickListener   { showDemo(demos[2]) }
        btnRelative.setOnClickListener { showDemo(demos[3]) }
    }

    private fun setupTabSwitch() {
        val tabXml    = findViewById<Button>(R.id.tab_xml)
        val tabKotlin = findViewById<Button>(R.id.tab_kotlin)

        tabXml.setOnClickListener {
            showingXml = true
            applyTabVisibility()
        }
        tabKotlin.setOnClickListener {
            showingXml = false
            applyTabVisibility()
        }
    }

    private fun showDemo(demo: DemoEntry) {
        currentDemo = demo
        loadXml(demo)
        loadKotlin(demo)
        applyTabVisibility()
    }

    private fun loadXml(demo: DemoEntry) {
        val xml = readAsset("code/xml/${demo.layoutName}.xml")
            ?: "<!-- XML source not available at runtime.\nFile: res/layout/${demo.layoutName}.xml -->"
        findViewById<TextView>(R.id.code_xml).text = xml
    }

    private fun loadKotlin(demo: DemoEntry) {
        val code = readAsset("code/kotlin/${demo.generatedClassName}.kt")
            ?: getString(R.string.code_viewer_no_source)
        findViewById<TextView>(R.id.code_kotlin).text = code
    }

    private fun readAsset(path: String): String? {
        return runCatching {
            assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun applyTabVisibility() {
        val scrollXml    = findViewById<ScrollView>(R.id.scroll_xml)
        val scrollKotlin = findViewById<ScrollView>(R.id.scroll_kotlin)
        scrollXml.visibility    = if (showingXml) View.VISIBLE else View.GONE
        scrollKotlin.visibility = if (showingXml) View.GONE    else View.VISIBLE
    }
}
