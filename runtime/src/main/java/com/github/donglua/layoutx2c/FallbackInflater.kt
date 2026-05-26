package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes

/**
 * Fallback：对不支持的布局或子树，使用原始 LayoutInflater inflate 原始 layout。
 *
 * 这是 MVP 阶段的简单实现。后续可优化为 XmlPullParser seek 方式。
 */
object FallbackInflater {

    /**
     * Inflate 原始 layout 根节点。
     */
    fun inflate(
        context: Context,
        @LayoutRes layoutId: Int,
        parent: ViewGroup?
    ): View {
        return LayoutInflater.from(context).inflate(layoutId, parent, false)
    }

    /**
     * Inflate 原始 layout 中指定路径的子 View。
     *
     * @param context Context
     * @param layoutId 原始 layout 资源 ID
     * @param childPath 从 root 到 fallback 子树的 child index 路径
     * @param parent 目标父容器（用于生成正确的 LayoutParams）
     */
    fun inflateChild(
        context: Context,
        @LayoutRes layoutId: Int,
        childPath: IntArray,
        parent: ViewGroup?
    ): View {
        val inflater = LayoutInflater.from(context)
        val fullTree = inflater.inflate(layoutId, parent, false)
        val child = findChildByPath(fullTree, childPath, context.resources.getResourceName(layoutId))
        (child.parent as? ViewGroup)?.removeView(child)
        return child
    }

    private fun findChildByPath(root: View, childPath: IntArray, layoutName: String): View {
        return (FallbackChildNavigator.findChildByPath(
            AndroidFallbackChildNode(root),
            childPath,
            layoutName
        ) as AndroidFallbackChildNode).view
    }

    private class AndroidFallbackChildNode(
        val view: View
    ) : FallbackChildNode {
        private val viewGroup: ViewGroup?
            get() = view as? ViewGroup

        override val isContainer: Boolean
            get() = viewGroup != null

        override val childCount: Int
            get() = viewGroup?.childCount ?: 0

        override fun childAt(index: Int): AndroidFallbackChildNode {
            return AndroidFallbackChildNode(viewGroup!!.getChildAt(index))
        }
    }
}
