package com.github.donglua.layoutx2c.demo

import android.app.Activity
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

private data class InitialPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

fun Activity.applySystemBarInsetsToContent() {
    val content = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return
    val initialPadding = InitialPadding(
        left = content.paddingLeft,
        top = content.paddingTop,
        right = content.paddingRight,
        bottom = content.paddingBottom,
    )

    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            left = initialPadding.left + systemBars.left,
            top = initialPadding.top + systemBars.top,
            right = initialPadding.right + systemBars.right,
            bottom = initialPadding.bottom + systemBars.bottom,
        )
        insets
    }
    ViewCompat.requestApplyInsets(content)
}
