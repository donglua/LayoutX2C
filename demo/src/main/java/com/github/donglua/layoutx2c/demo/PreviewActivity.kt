package com.github.donglua.layoutx2c.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import com.github.donglua.layoutx2c.runtime.LayoutX2CRegistry

/**
 * 预览 Activity：展示 LayoutX2C 生成的 View 和 LayoutInflater 的对比。
 */
class PreviewActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_LAYOUT_ID = "layout_id"
        private const val EXTRA_LAYOUT_NAME = "layout_name"

        fun intent(context: Context, @LayoutRes layoutId: Int, layoutName: String): Intent {
            return Intent(context, PreviewActivity::class.java).apply {
                putExtra(EXTRA_LAYOUT_ID, layoutId)
                putExtra(EXTRA_LAYOUT_NAME, layoutName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutId = intent.getIntExtra(EXTRA_LAYOUT_ID, 0)
        val layoutName = intent.getStringExtra(EXTRA_LAYOUT_NAME) ?: "unknown"

        if (layoutId == 0) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        // Title bar
        val titleBar = TextView(this).apply {
            text = "Preview: $layoutName"
            setPadding(24.dp, 16.dp, 24.dp, 16.dp)
            textSize = 16f
        }
        root.addView(titleBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Tab labels
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24.dp, 0, 24.dp, 8.dp)
        }
        val tabGenerated = TextView(this).apply {
            text = "LayoutX2C"
            setPadding(0, 8.dp, 16.dp, 8.dp)
            textSize = 13f
        }
        val tabInflater = TextView(this).apply {
            text = "LayoutInflater"
            setPadding(16.dp, 8.dp, 0, 8.dp)
            textSize = 13f
        }
        tabRow.addView(tabGenerated)
        tabRow.addView(tabInflater)
        root.addView(tabRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Content container
        val container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

        // Show generated view by default
        val hasGenerated = LayoutX2CRegistry.has(layoutId)
        showView(container, layoutId, useGenerated = hasGenerated)

        tabGenerated.setOnClickListener {
            showView(container, layoutId, useGenerated = true)
        }
        tabInflater.setOnClickListener {
            showView(container, layoutId, useGenerated = false)
        }
    }

    private fun showView(container: FrameLayout, @LayoutRes layoutId: Int, useGenerated: Boolean) {
        container.removeAllViews()
        val view: View = if (useGenerated && LayoutX2CRegistry.has(layoutId)) {
            LayoutX2CRegistry.inflate(this, layoutId, container)
        } else {
            LayoutInflater.from(this).inflate(layoutId, container, false)
        }
        container.addView(view)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}
