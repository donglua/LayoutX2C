package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes

/**
 * 局部 fallback：对不支持的子树，使用原始 LayoutInflater inflate 整个 layout，
 * 然后从中提取指定位置的子 View。
 *
 * 这是 MVP 阶段的简单实现。后续可优化为 XmlPullParser seek 方式。
 */
object FallbackInflater {

    /**
     * Inflate 原始 layout 中指定 index 的子 View。
     *
     * @param context Context
     * @param layoutId 原始 layout 资源 ID
     * @param childIndex 需要 fallback 的 child 在 root 中的 index
     * @param parent 目标父容器（用于生成正确的 LayoutParams）
     */
    fun inflateChild(
        context: Context,
        @LayoutRes layoutId: Int,
        childIndex: Int,
        parent: ViewGroup?
    ): View {
        val inflater = LayoutInflater.from(context)
        val fullTree = inflater.inflate(layoutId, parent, false) as ViewGroup
        val child = fullTree.getChildAt(childIndex)
        fullTree.removeView(child)
        return child
    }
}
