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
    )

    private var currentDemo = demos[0]
    private var showingXml = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_viewer)

        setupLayoutSelector()
        setupTabSwitch()
        showDemo(demos[0])
    }

    private fun setupLayoutSelector() {
        val btnSimple = findViewById<Button>(R.id.btn_demo_simple)
        val btnNested = findViewById<Button>(R.id.btn_demo_nested)
        val btnForm   = findViewById<Button>(R.id.btn_demo_form)

        btnSimple.setOnClickListener { showDemo(demos[0]) }
        btnNested.setOnClickListener { showDemo(demos[1]) }
        btnForm.setOnClickListener   { showDemo(demos[2]) }
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
        val xml = runCatching {
            resources.openRawResource(demo.xmlResId).bufferedReader().readText()
        }.getOrElse {
            // Fallback: read from res/layout via asset path (won't work at runtime for layout res IDs,
            // so we surface a helpful message)
            readXmlFromDisk(demo.layoutName)
        }
        findViewById<TextView>(R.id.code_xml).text = xml
    }

    private fun readXmlFromDisk(layoutName: String): String {
        // Try to find the XML source from the project source tree (only works on emulator/device
        // with a debug build where sources are on-disk at a predictable location).
        val candidates = listOf(
            File(filesDir, "../../../../../../demo/src/main/res/layout/${layoutName}.xml"),
        )
        for (f in candidates) {
            if (f.exists()) return f.readText()
        }
        return "<!-- XML source not available at runtime.\nFile: res/layout/${layoutName}.xml -->"
    }

    private fun loadKotlin(demo: DemoEntry) {
        val code = findGeneratedKotlin(demo.generatedClassName)
        findViewById<TextView>(R.id.code_kotlin).text = code
    }

    /**
     * Search common KSP output directories for the generated .kt file.
     * Works when running on a device/emulator with a debug build where the
     * build directory is accessible via the external storage or a known path.
     *
     * Falls back to a helpful hint if the file can't be found.
     */
    private fun findGeneratedKotlin(className: String): String {
        val fileName = "$className.kt"

        // On a real device the build/ dir isn't accessible, but on an emulator
        // with a connected project we can try the external files dir as a staging area.
        // The primary approach: look relative to the app's data dir for any pre-staged file.
        val staged = File(filesDir, "generated/$fileName")
        if (staged.exists()) return staged.readText()

        // Secondary: search common KSP output paths relative to the project root
        // (only works on host-side JVM tests or when running from Android Studio with
        //  a shared filesystem — not typical for a production APK).
        val kspCandidates = listOf(
            "demo/build/generated/ksp/debug/kotlin/com/github/donglua/layoutx2c/demo/generated/$fileName",
            "demo/build/generated/ksp/release/kotlin/com/github/donglua/layoutx2c/demo/generated/$fileName",
        )
        for (rel in kspCandidates) {
            // Try from possible project roots accessible at runtime
            val f = File("/data/local/tmp/layoutx2c/$rel")
            if (f.exists()) return f.readText()
        }

        return getString(R.string.code_viewer_no_source)
    }

    private fun applyTabVisibility() {
        val scrollXml    = findViewById<ScrollView>(R.id.scroll_xml)
        val scrollKotlin = findViewById<ScrollView>(R.id.scroll_kotlin)
        scrollXml.visibility    = if (showingXml) View.VISIBLE else View.GONE
        scrollKotlin.visibility = if (showingXml) View.GONE    else View.VISIBLE
    }
}
