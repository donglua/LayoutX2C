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
 * 子树 fallback 通过完整 inflate 原始 XML 后摘取目标节点。
 */
@PublicApi
class FallbackChildPlan(
    val childPath: IntArray,
    val targetTag: String
)

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
        return inflateChildWithPath(context, layoutId, childPath, parent)
    }

    fun inflateChild(
        context: Context,
        @LayoutRes layoutId: Int,
        childPlan: FallbackChildPlan,
        parent: ViewGroup?
    ): View {
        return inflateChildWithPath(context, layoutId, childPlan.childPath, parent)
    }

    private fun inflateChildWithPath(
        context: Context,
        @LayoutRes layoutId: Int,
        childPath: IntArray,
        parent: ViewGroup?
    ): View {
        val layoutName = context.resources.getResourceName(layoutId)
        val parser = context.resources.getLayout(layoutId)
        try {
            seekToChildStartTag(parser, childPath, layoutName)
            return inflateChildFromFullTree(context, layoutId, childPath, parent, layoutName)
        } finally {
            parser.close()
        }
    }

    /**
     * Inflate 原始 layout 中的多个 fallback 子树。
     *
     * 对非重叠路径共享一次 XML 遍历；不适合局部 inflate 的目标共享一次整树提取。
     */
    fun inflateChildren(
        context: Context,
        @LayoutRes layoutId: Int,
        childPaths: Array<IntArray>,
        parent: ViewGroup?
    ): Array<View> {
        if (childPaths.isEmpty()) return emptyArray()

        if (hasOverlappingChildPaths(childPaths)) {
            return inflateChildrenIndividually(context, layoutId, childPaths, parent)
        }

        val layoutName = context.resources.getResourceName(layoutId)
        val targets = childPaths.mapIndexed { index, childPath ->
            ChildInflateTarget(index, childPath)
        }.sortedWith { first, second ->
            compareChildPaths(first.childPath, second.childPath)
        }
        return inflateChildrenWithSingleParser(context, layoutId, targets, parent, layoutName)
    }

    fun inflateChildren(
        context: Context,
        @LayoutRes layoutId: Int,
        childPlans: Array<FallbackChildPlan>,
        parent: ViewGroup?
    ): Array<View> {
        if (childPlans.isEmpty()) return emptyArray()

        val childPaths = Array(childPlans.size) { index -> childPlans[index].childPath }
        if (hasOverlappingChildPaths(childPaths)) {
            return Array(childPlans.size) { index ->
                val childPlan = childPlans[index]
                inflateChild(context, layoutId, childPlan, parent)
            }
        }

        val layoutName = context.resources.getResourceName(layoutId)
        val targets = childPlans.mapIndexed { index, childPlan ->
            ChildInflateTarget(index, childPlan.childPath)
        }.sortedWith { first, second ->
            compareChildPaths(first.childPath, second.childPath)
        }
        return inflateChildrenWithSingleParser(context, layoutId, targets, parent, layoutName)
    }

    private fun inflateChildrenIndividually(
        context: Context,
        @LayoutRes layoutId: Int,
        childPaths: Array<IntArray>,
        parent: ViewGroup?
    ): Array<View> {
        return Array(childPaths.size) { index ->
            val childPath = childPaths[index]
            inflateChild(context, layoutId, childPath, parent)
        }
    }

    private fun inflateChildrenWithSingleParser(
        context: Context,
        @LayoutRes layoutId: Int,
        targets: List<ChildInflateTarget>,
        parent: ViewGroup?,
        layoutName: String
    ): Array<View> {
        val targetsByPath = targets.associateBy { it.childPath.toList() }
        val inflatedChildren = arrayOfNulls<View>(targets.size)
        val fullTreeTargets = mutableListOf<ChildInflateTarget>()
        val parser = context.resources.getLayout(layoutId)

        try {
            moveToFirstStartTag(parser, layoutName)
            if (parser.name == "layout") {
                moveToDataBindingViewRoot(parser, layoutName)
            }
            inflateTargetSubtree(
                parser = parser,
                currentPath = emptyList(),
                targetsByPath = targetsByPath,
                fullTreeTargets = fullTreeTargets
            )
        } finally {
            parser.close()
        }

        if (fullTreeTargets.isNotEmpty()) {
            val fullTreeChildren = inflateChildrenFromFullTree(
                context,
                layoutId,
                fullTreeTargets.map { it.childPath }.toTypedArray(),
                parent,
                layoutName
            )
            for (index in fullTreeTargets.indices) {
                inflatedChildren[fullTreeTargets[index].outputIndex] = fullTreeChildren[index]
            }
        }

        return Array(targets.size) { index ->
            inflatedChildren[index] ?: throw IllegalArgumentException(
                "Fallback child path ${targets[index].childPath.toPathString()} is invalid for $layoutName"
            )
        }
    }

    private fun inflateTargetSubtree(
        parser: XmlPullParser,
        currentPath: List<Int>,
        targetsByPath: Map<List<Int>, ChildInflateTarget>,
        fullTreeTargets: MutableList<ChildInflateTarget>
    ) {
        val target = targetsByPath[currentPath]
        if (target != null) {
            fullTreeTargets += target
            skipCurrentSubtree(parser)
            return
        }

        val parentDepth = parser.depth
        var childIndex = 0
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.depth == parentDepth + 1) {
                        inflateTargetSubtree(
                            parser = parser,
                            currentPath = currentPath + childIndex,
                            targetsByPath = targetsByPath,
                            fullTreeTargets = fullTreeTargets
                        )
                        childIndex++
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == parentDepth) {
                        return
                    }
                }
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun skipCurrentSubtree(parser: XmlPullParser) {
        val startDepth = parser.depth
        while (true) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> {
                    if (parser.depth == startDepth) {
                        return
                    }
                }
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun hasOverlappingChildPaths(childPaths: Array<IntArray>): Boolean {
        val sortedPaths = childPaths.sortedWith(::compareChildPaths)
        for (index in 1 until sortedPaths.size) {
            val previous = sortedPaths[index - 1]
            val current = sortedPaths[index]
            if (previous.isPrefixOf(current)) {
                return true
            }
        }
        return false
    }

    private fun compareChildPaths(first: IntArray, second: IntArray): Int {
        val sharedSize = minOf(first.size, second.size)
        for (index in 0 until sharedSize) {
            val difference = first[index] - second[index]
            if (difference != 0) {
                return difference
            }
        }
        return first.size - second.size
    }

    private fun IntArray.isPrefixOf(other: IntArray): Boolean {
        if (size > other.size) return false
        for (index in indices) {
            if (this[index] != other[index]) {
                return false
            }
        }
        return true
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

    private fun inflateChildrenFromFullTree(
        context: Context,
        @LayoutRes layoutId: Int,
        childPaths: Array<IntArray>,
        parent: ViewGroup?,
        layoutName: String
    ): Array<View> {
        val inflater = LayoutInflater.from(context)
        val fullTree = inflater.inflate(layoutId, parent, false)
        val children = Array(childPaths.size) { index ->
            findChildByPath(fullTree, childPaths[index], layoutName)
        }
        for (child in children) {
            (child.parent as? ViewGroup)?.removeView(child)
        }
        return children
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

    private class ChildInflateTarget(
        val outputIndex: Int,
        val childPath: IntArray
    )

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
