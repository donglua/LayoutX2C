package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes

/**
 * Fallback：对不支持的布局或子树，使用原始 LayoutInflater inflate 原始 layout。
 *
 * 子树 fallback 会先 seek 到原始 XML 的目标节点，再只 inflate 目标子树。
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
        val layoutName = context.resources.getResourceName(layoutId)
        context.resources.getLayout(layoutId).use { parser ->
            val subtree = FallbackXmlSubtreeSeeker.seekToChild(parser, childPath, layoutName)
            if (!subtree.requiresLegacyInflate()) {
                return LayoutInflater.from(context).inflate(ReplayStartTagXmlResourceParser(parser), parent, false)
            }
        }
        return inflateChildFromFullTree(context, layoutId, childPath, parent, layoutName)
    }

    private fun FallbackXmlSubtree.requiresLegacyInflate(): Boolean {
        return tagName == "merge" || tagName == "include" || tagName == "fragment"
    }

    /**
     * Legacy path for tags whose semantics depend on normal LayoutInflater handling.
     *
     * Unlike the parser-seek path above, this inflates the full original layout and detaches
     * the requested child afterward. That keeps merge/include/fragment behavior compatible,
     * but can be slower for complex parent layouts. Nested include content that itself expands
     * merge is also still bounded by full-tree inflate semantics, so avoid treating this as a
     * partial-subtree optimization.
     */
    private fun inflateChildFromFullTree(
        context: Context,
        @LayoutRes layoutId: Int,
        childPath: IntArray,
        parent: ViewGroup?,
        layoutName: String
    ): View {
        val inflater = LayoutInflater.from(context)
        val fullTree = inflater.inflate(layoutId, parent, false)
        val child = findChildByPath(fullTree, childPath, layoutName)
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
