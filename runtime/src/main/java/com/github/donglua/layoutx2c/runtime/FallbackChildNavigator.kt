package com.github.donglua.layoutx2c.runtime

internal interface FallbackChildNode {
    val isContainer: Boolean
    val childCount: Int
    fun childAt(index: Int): FallbackChildNode
}

internal object FallbackChildNavigator {
    fun findChildByPath(root: FallbackChildNode, childPath: IntArray, layoutName: String): FallbackChildNode {
        var current = root
        val traversed = mutableListOf<Int>()

        for (index in childPath) {
            if (!current.isContainer) {
                throw IllegalArgumentException(
                    "Fallback child path ${childPath.toPathString()} is invalid for $layoutName: " +
                        "node at ${traversed.toPathString()} is not a ViewGroup"
                )
            }
            if (index < 0 || index >= current.childCount) {
                throw IllegalArgumentException(
                    "Fallback child path ${childPath.toPathString()} is invalid for $layoutName: " +
                        "index $index is out of bounds at ${traversed.toPathString()} with childCount=${current.childCount}"
                )
            }
            current = current.childAt(index)
            traversed += index
        }

        return current
    }

    private fun IntArray.toPathString(): String {
        return toList().toPathString()
    }

    private fun List<Int>.toPathString(): String {
        return if (isEmpty()) "<root>" else joinToString(prefix = "[", postfix = "]")
    }
}
