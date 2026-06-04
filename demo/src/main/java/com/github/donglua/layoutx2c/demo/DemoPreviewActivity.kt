package com.github.donglua.layoutx2c.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DemoPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo_preview)
        applySystemBarInsetsToContent()

        val layoutName = intent.getStringExtra(EXTRA_LAYOUT_NAME)
        val demo = layoutName?.let { name ->
            runCatching { DemoLayoutCatalog.requireByLayoutName(name) }.getOrNull()
        }

        if (demo == null) {
            showMissingDemo()
        } else {
            showDemo(demo)
        }
    }

    private fun showDemo(demo: DemoLayoutCatalog.Entry) {
        findViewById<TextView>(R.id.preview_title).text = demo.label
        findViewById<TextView>(R.id.preview_summary).text = demo.summary
        findViewById<TextView>(R.id.preview_metadata).text =
            "${demo.layoutName} · ${displayClassName(demo)} · ${statusLabel(demo.status)} · ${previewModeLabel(demo.previewMode)}"

        val host = findViewById<FrameLayout>(R.id.preview_host)
        host.removeAllViews()

        runCatching {
            demo.generatedInflater(this, host)
        }.onSuccess { preview ->
            if (preview.parent == null) {
                host.addView(preview)
            }
            demo.configurePreview(this, preview)
        }.onFailure { throwable ->
            host.addView(errorView("Preview failed: ${throwable.message.orEmpty()}"))
        }
    }

    private fun showMissingDemo() {
        findViewById<TextView>(R.id.preview_title).text = getString(R.string.demo_preview_missing)
        findViewById<TextView>(R.id.preview_summary).text = ""
        findViewById<TextView>(R.id.preview_metadata).text = ""
        findViewById<FrameLayout>(R.id.preview_host).addView(
            errorView(getString(R.string.demo_preview_missing))
        )
    }

    private fun errorView(message: String): View {
        return TextView(this).apply {
            text = message
            setTextColor(getColor(R.color.demo_text_primary))
            textSize = 14f
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
    }

    private fun displayClassName(demo: DemoLayoutCatalog.Entry): String {
        return if (demo.codeViewerClassName != demo.generatedClassName) {
            demo.codeViewerClassName
        } else {
            "${demo.generatedClassName.removePrefix("Layout_")}X2C"
        }
    }

    private fun statusLabel(status: DemoLayoutCatalog.Status): String {
        return when (status) {
            DemoLayoutCatalog.Status.Generated -> "Generated"
            DemoLayoutCatalog.Status.Binding -> "Binding"
            DemoLayoutCatalog.Status.Fallback -> "Fallback"
        }
    }

    private fun previewModeLabel(mode: DemoLayoutCatalog.PreviewMode): String {
        return when (mode) {
            DemoLayoutCatalog.PreviewMode.DisplayOnly -> "Display-only"
            DemoLayoutCatalog.PreviewMode.Interactive -> "Interactive"
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val EXTRA_LAYOUT_NAME = "layoutName"

        fun createIntent(context: Context, layoutName: String): Intent {
            return Intent(context, DemoPreviewActivity::class.java)
                .putExtra(EXTRA_LAYOUT_NAME, layoutName)
        }
    }
}
