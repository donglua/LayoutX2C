package com.github.donglua.layoutx2c.runtime

import org.xmlpull.v1.XmlPullParser

internal data class FallbackXmlSubtree(
    val tagName: String
)

internal object FallbackXmlSubtreeSeeker {
    fun seekToChild(parser: XmlPullParser, childPath: IntArray, layoutName: String): FallbackXmlSubtree {
        seekToRoot(parser, layoutName)

        val traversed = mutableListOf<Int>()
        for (index in childPath) {
            seekToDirectChild(parser, index, childPath, traversed, layoutName)
            traversed += index
        }

        return FallbackXmlSubtree(tagName = parser.name)
    }

    private fun seekToRoot(parser: XmlPullParser, layoutName: String) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> return
                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException(
                    "Fallback child path <root> is invalid for $layoutName: layout has no root tag"
                )
            }
        }
    }

    private fun seekToDirectChild(
        parser: XmlPullParser,
        targetIndex: Int,
        childPath: IntArray,
        traversed: List<Int>,
        layoutName: String
    ) {
        if (targetIndex < 0) {
            throw invalidPath(childPath, layoutName, "index $targetIndex is negative at ${traversed.toPathString()}")
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
                        skipCurrentTag(parser)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == parentDepth) {
                        throw invalidPath(
                            childPath,
                            layoutName,
                            "index $targetIndex is out of bounds at ${traversed.toPathString()} " +
                                "with childCount=$childIndex"
                        )
                    }
                }
                XmlPullParser.END_DOCUMENT -> throw invalidPath(
                    childPath,
                    layoutName,
                    "index $targetIndex is out of bounds at ${traversed.toPathString()} with childCount=$childIndex"
                )
            }
        }
    }

    private fun skipCurrentTag(parser: XmlPullParser) {
        val startDepth = parser.depth
        while (true) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> if (parser.depth == startDepth) return
                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException(
                    "Unexpected end of document while skipping fallback sibling subtree"
                )
            }
        }
    }

    private fun invalidPath(childPath: IntArray, layoutName: String, reason: String): IllegalArgumentException {
        return IllegalArgumentException(
            "Fallback child path ${childPath.toPathString()} is invalid for $layoutName: $reason"
        )
    }

    private fun IntArray.toPathString(): String {
        return toList().toPathString()
    }

    private fun List<Int>.toPathString(): String {
        return if (isEmpty()) "<root>" else joinToString(prefix = "[", postfix = "]")
    }
}
