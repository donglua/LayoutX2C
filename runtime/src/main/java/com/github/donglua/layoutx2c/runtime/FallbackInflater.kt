package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.github.donglua.layoutx2c.runtime.annotation.PublicApi
import org.xmlpull.v1.XmlPullParser

/**
 * Fallback：对不支持的布局或子树，使用原始 LayoutInflater inflate 原始 layout。
 *
 * 子树 fallback 优先 seek 到原始 XML 中的目标节点做局部 inflate。
 */
@PublicApi
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
        val parser = context.resources.getLayout(layoutId)
        try {
            val targetTag = seekToChildStartTag(parser, childPath, layoutName)
            if (requiresFullTreeExtraction(targetTag)) {
                return inflateChildFromFullTree(context, layoutId, childPath, parent, layoutName)
            }
            return LayoutInflater.from(context).inflate(parser, parent, false)
        } finally {
            parser.close()
        }
    }

    /**
     * Inflate 原始 layout 中的多个 fallback 子树。
     *
     * 委托给 inflateChild 处理每个子树，由其自动选择 partial inflate 或 full-tree extraction。
     */
    fun inflateChildren(
        context: Context,
        @LayoutRes layoutId: Int,
        childPaths: Array<IntArray>,
        parent: ViewGroup?
    ): Array<View> {
        if (childPaths.isEmpty()) return emptyArray()

        return Array(childPaths.size) { index ->
            inflateChild(context, layoutId, childPaths[index], parent)
        }
    }

    private fun seekToChildStartTag(
        parser: XmlPullParser,
        childPath: IntArray,
        layoutName: String
    ): String {
        moveToFirstStartTag(parser, layoutName)
        if (parser.name == "layout") {
            moveToDataBindingViewRoot(parser, layoutName)
        }

        val traversed = mutableListOf<Int>()
        for (index in childPath) {
            moveToChildAt(parser, index, traversed, childPath, layoutName)
            traversed += index
        }

        return parser.name
    }

    private fun moveToFirstStartTag(parser: XmlPullParser, layoutName: String) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> return
                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException(
                    "Fallback child path is invalid for $layoutName: layout XML has no root tag"
                )
            }
        }
    }

    private fun moveToDataBindingViewRoot(parser: XmlPullParser, layoutName: String) {
        val layoutDepth = parser.depth
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.depth == layoutDepth + 1 && parser.name != "data") {
                        return
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == layoutDepth) {
                        throw IllegalArgumentException(
                            "Fallback child path is invalid for $layoutName: data binding layout has no view root"
                        )
                    }
                }
                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException(
                    "Fallback child path is invalid for $layoutName: data binding layout has no view root"
                )
            }
        }
    }

    private fun moveToChildAt(
        parser: XmlPullParser,
        targetIndex: Int,
        traversed: List<Int>,
        childPath: IntArray,
        layoutName: String
    ) {
        if (targetIndex < 0) {
            throw IllegalArgumentException(
                "Fallback child path ${childPath.toPathString()} is invalid for $layoutName: " +
                    "index $targetIndex is out of bounds at ${traversed.toPathString()}"
            )
        }

        val parentDepth = parser.depth
        var childIndex = 0
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.depth == parentDepth + 1) {
                        if (childIndex == targetIndex) {
                            return
                        }
                        childIndex++
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == parentDepth) {
                        throw IllegalArgumentException(
                            "Fallback child path ${childPath.toPathString()} is invalid for $layoutName: " +
                                "index $targetIndex is out of bounds at ${traversed.toPathString()} " +
                                "with childCount=$childIndex"
                        )
                    }
                }
                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException(
                    "Fallback child path ${childPath.toPathString()} is invalid for $layoutName: " +
                        "index $targetIndex is out of bounds with childCount=$childIndex"
                )
            }
        }
    }

    private fun requiresFullTreeExtraction(tagName: String): Boolean {
        return tagName == "merge" || tagName == "include" || tagName == "fragment"
    }

    /**
     * Inflate the full original layout and detach the requested child afterward.
     * Used for inflater semantic tags that cannot be safely inflated as standalone roots.
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

    private fun IntArray.toPathString(): String {
        return toList().toPathString()
    }

    private fun List<Int>.toPathString(): String {
        return if (isEmpty()) "<root>" else joinToString(prefix = "[", postfix = "]")
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
