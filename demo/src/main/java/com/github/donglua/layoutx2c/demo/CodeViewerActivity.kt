package com.github.donglua.layoutx2c.demo

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Code Viewer：对照展示 XML 源码与 KSP 生成的 Kotlin 代码。
 */
class CodeViewerActivity : AppCompatActivity() {

    private enum class CodeTab {
        Xml,
        Kotlin
    }

    private val demos = DemoLayoutCatalog.entries

    private var currentDemo = demos[0]
    private var currentTab = CodeTab.Xml

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_viewer)
        applySystemBarInsetsToContent()

        setupLayoutSelector()
        setupTabSwitch()
        showDemo(demos[0])
    }

    private fun setupLayoutSelector() {
        val buttons = listOf(
            findViewById<Button>(R.id.btn_demo_simple),
            findViewById<Button>(R.id.btn_demo_nested),
            findViewById<Button>(R.id.btn_demo_form),
            findViewById<Button>(R.id.btn_demo_relative),
            findViewById<Button>(R.id.btn_demo_recycler),
            findViewById<Button>(R.id.btn_demo_fallback),
            findViewById<Button>(R.id.btn_demo_binding)
        )
        buttons.zip(demos).forEach { (button, demo) ->
            button.setOnClickListener { showDemo(demo) }
        }
    }

    private fun setupTabSwitch() {
        val tabXml    = findViewById<Button>(R.id.tab_xml)
        val tabKotlin = findViewById<Button>(R.id.tab_kotlin)

        tabXml.setOnClickListener {
            currentTab = CodeTab.Xml
            applyTabVisibility()
        }
        tabKotlin.setOnClickListener {
            currentTab = CodeTab.Kotlin
            applyTabVisibility()
        }
    }

    private fun showDemo(demo: DemoLayoutCatalog.Entry) {
        currentDemo = demo
        loadXml(demo)
        loadKotlin(demo)
        applyTabVisibility()
    }

    private fun loadXml(demo: DemoLayoutCatalog.Entry) {
        val xml = readAsset("code/xml/${demo.layoutName}.xml")
            ?: "<!-- XML source not available at runtime.\nFile: res/layout/${demo.layoutName}.xml -->"
        bindCode(
            title = "res/layout/${demo.layoutName}.xml",
            formatted = CodeFormatter.withLineNumbers(xml),
            titleViewId = R.id.title_xml,
            summaryViewId = R.id.summary_xml,
            lineNumbersViewId = R.id.line_numbers_xml,
            codeViewId = R.id.code_xml
        )
    }

    private fun loadKotlin(demo: DemoLayoutCatalog.Entry) {
        val code = readAsset("code/kotlin/${demo.codeViewerClassName}.kt")
            ?: getString(R.string.code_viewer_no_source)
        bindCode(
            title = "${demo.codeViewerClassName}.kt",
            formatted = CodeFormatter.withLineNumbers(code),
            titleViewId = R.id.title_kotlin,
            summaryViewId = R.id.summary_kotlin,
            lineNumbersViewId = R.id.line_numbers_kotlin,
            codeViewId = R.id.code_kotlin
        )
    }

    private fun bindCode(
        title: String,
        formatted: CodeFormatter.FormattedCode,
        titleViewId: Int,
        summaryViewId: Int,
        lineNumbersViewId: Int,
        codeViewId: Int
    ) {
        findViewById<TextView>(titleViewId).text = title
        findViewById<TextView>(summaryViewId).text = formatted.summary
        findViewById<TextView>(lineNumbersViewId).text = formatted.lineNumbers
        findViewById<TextView>(codeViewId).text = formatted.code
    }

    private fun readAsset(path: String): String? {
        return runCatching {
            assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun applyTabVisibility() {
        val panelXml = findViewById<View>(R.id.panel_xml)
        val panelKotlin = findViewById<View>(R.id.panel_kotlin)
        val tabXml = findViewById<Button>(R.id.tab_xml)
        val tabKotlin = findViewById<Button>(R.id.tab_kotlin)

        panelXml.visibility = if (currentTab == CodeTab.Xml) View.VISIBLE else View.GONE
        panelKotlin.visibility = if (currentTab == CodeTab.Kotlin) View.VISIBLE else View.GONE
        tabXml.isSelected = currentTab == CodeTab.Xml
        tabKotlin.isSelected = currentTab == CodeTab.Kotlin
        updateDemoSelection()
        resetHorizontalScroll()
    }

    private fun updateDemoSelection() {
        val buttonPairs = listOf(
            R.id.btn_demo_simple to demos[0],
            R.id.btn_demo_nested to demos[1],
            R.id.btn_demo_form to demos[2],
            R.id.btn_demo_relative to demos[3],
            R.id.btn_demo_recycler to demos[4],
            R.id.btn_demo_fallback to demos[5],
            R.id.btn_demo_binding to demos[6]
        )
        buttonPairs.forEach { (buttonId, demo) ->
            findViewById<Button>(buttonId).isSelected = demo == currentDemo
        }
    }

    private fun resetHorizontalScroll() {
        findViewById<HorizontalScrollView>(R.id.code_hscroll_xml).scrollTo(0, 0)
        findViewById<HorizontalScrollView>(R.id.code_hscroll_kotlin).scrollTo(0, 0)
    }
}
