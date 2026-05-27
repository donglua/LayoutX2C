package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatViewInflater
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView

/**
 * AppCompat 兼容的 View 创建工具。
 * 确保生成的代码创建的 View 类型与 LayoutInflater + AppCompat Factory2 一致。
 */
object ViewCompat {

    fun createTextView(context: Context): AppCompatTextView {
        return AppCompatTextView(context)
    }

    fun createImageView(context: Context): AppCompatImageView {
        return AppCompatImageView(context)
    }

    fun createView(context: Context): View {
        return View(context)
    }
}
