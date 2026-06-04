package com.github.donglua.layoutx2c.demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsetsToContent()

        showGeneratedEntryStatus()
        setupButtons()
        renderDemoGallery()
    }

    private fun showGeneratedEntryStatus() {
        val demos = DemoLayoutCatalog.entries
        val total = demos.size
        val generated = demos.count { it.status == DemoLayoutCatalog.Status.Generated }
        val binding = demos.count { it.status == DemoLayoutCatalog.Status.Binding }
        val fallback = demos.count { it.status == DemoLayoutCatalog.Status.Fallback }
        findViewById<TextView>(R.id.registry_status).text =
            "Catalog entries: $total · Generated: $generated · Binding: $binding · Fallback: $fallback"
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

    private fun renderDemoGallery() {
        val gallery = findViewById<LinearLayout>(R.id.demo_gallery)
        gallery.removeAllViews()

        DemoLayoutCatalog.entries.forEachIndexed { index, demo ->
            gallery.addView(createGalleryRow(demo, index > 0))
        }
    }

    private fun createGalleryRow(
        demo: DemoLayoutCatalog.Entry,
        hasTopMargin: Boolean
    ): LinearLayout {
        val openPreview = View.OnClickListener {
            startActivity(DemoPreviewActivity.createIntent(this@MainActivity, demo.layoutName))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.code_viewer_panel)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            contentDescription =
                "${demo.label}, ${statusLabel(demo.status)}, ${previewModeLabel(demo.previewMode)}, ${demo.summary}"
            foreground = selectableItemBackground()
            setOnClickListener(openPreview)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (hasTopMargin) {
                    topMargin = dp(10)
                }
            }
        }

        row.addView(TextView(this).apply {
            text = "${demo.label}  ·  ${statusLabel(demo.status)}  ·  ${previewModeLabel(demo.previewMode)}"
            setTextColor(getColor(R.color.demo_text_primary))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnClickListener(openPreview)
        })

        row.addView(TextView(this).apply {
            text = demo.summary
            setTextColor(getColor(R.color.code_viewer_muted_text))
            textSize = 13f
            setOnClickListener(openPreview)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        })

        row.addView(TextView(this).apply {
            text = "${demo.layoutName} → ${displayClassName(demo)}"
            setTextColor(getColor(R.color.code_viewer_muted_text))
            textSize = 12f
            setOnClickListener(openPreview)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        })

        return row
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

    private fun selectableItemBackground(): android.graphics.drawable.Drawable? {
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val typedArray = obtainStyledAttributes(attrs)
        return try {
            typedArray.getDrawable(0)
        } finally {
            typedArray.recycle()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
