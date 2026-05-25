package com.github.donglua.layoutx2c.demo

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
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
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
        }

        val titleBar = TextView(this).apply {
            text = "Preview: $layoutName"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(titleBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16.dp, 0, 16.dp)
        }
        val btnGenerated = Button(this).apply {
            text = "LayoutX2C"
        }
        val btnInflater = Button(this).apply {
            text = "LayoutInflater"
        }
        buttonRow.addView(btnGenerated, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = 8.dp
        })
        buttonRow.addView(btnInflater, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 8.dp
        })
        root.addView(buttonRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

        val hasGenerated = LayoutX2CRegistry.has(this, layoutId)
        showView(container, layoutId, useGenerated = hasGenerated)

        btnGenerated.setOnClickListener {
            showView(container, layoutId, useGenerated = true)
        }
        btnInflater.setOnClickListener {
            showView(container, layoutId, useGenerated = false)
        }
    }

    private fun showView(container: FrameLayout, @LayoutRes layoutId: Int, useGenerated: Boolean) {
        container.removeAllViews()
        val view: View = if (useGenerated && LayoutX2CRegistry.has(this, layoutId)) {
            LayoutX2CRegistry.inflate(this, layoutId, container)
        } else {
            LayoutInflater.from(this).inflate(layoutId, container, false)
        }
        container.addView(view)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}
