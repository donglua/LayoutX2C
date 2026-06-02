package com.github.donglua.layoutx2c.runtime

import org.xmlpull.v1.XmlPullParser

internal data class FallbackXmlSubtree(
    val tagName: String,
    val dataBindingWrapped: Boolean = false,
    val dataBindingDataTagPresent: Boolean = false
)

internal object FallbackXmlSubtreeSeeker {
    fun seekToChild(parser: XmlPullParser, childPath: IntArray, layoutName: String): FallbackXmlSubtree {
        seekToRoot(parser, layoutName)
        var dataBindingWrapped = false
        var dataBindingDataTagPresent = false
        if (parser.name == "layout") {
            dataBindingWrapped = true
            dataBindingDataTagPresent = seekToDataBindingViewRoot(parser, childPath, layoutName)
        }

        val traversed = mutableListOf<Int>()
        for (index in childPath) {
            seekToDirectChild(parser, index, childPath, traversed, layoutName)
            traversed += index
        }

        return FallbackXmlSubtree(
            tagName = parser.name,
            dataBindingWrapped = dataBindingWrapped,
            dataBindingDataTagPresent = dataBindingDataTagPresent
        )
    }

    fun seekBeforeChild(
        parser: XmlPullParser,
        childPath: IntArray,
        layoutName: String,
        dataBindingWrapped: Boolean,
        dataBindingDataTagPresent: Boolean
    ) {
        if (!dataBindingWrapped) {
            if (childPath.isEmpty()) {
                return
            }
            seekToRoot(parser, layoutName)
        } else {
            seekToRoot(parser, layoutName)
            if (childPath.isEmpty()) {
                if (dataBindingDataTagPresent) {
                    skipDataBindingDataTag(parser, childPath, layoutName)
                }
                return
            }
            seekToDataBindingViewRoot(parser, childPath, layoutName)
        }

        val traversed = mutableListOf<Int>()
        for (index in childPath.dropLast(1)) {
            seekToDirectChild(parser, index, childPath, traversed, layoutName)
            traversed += index
        }
        seekBeforeDirectChild(parser, childPath.last(), childPath, traversed, layoutName)
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

    private fun seekToDataBindingViewRoot(
        parser: XmlPullParser,
        childPath: IntArray,
        layoutName: String
    ): Boolean {
        val parentDepth = parser.depth
        var viewRootSeen = false
        var dataTagPresent = false
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.depth == parentDepth + 1) {
                        if (parser.name == "data") {
                            dataTagPresent = true
                            skipCurrentTag(parser)
                        } else {
                            if (viewRootSeen) {
                                throw invalidPath(
                                    childPath,
                                    layoutName,
                                    "data binding wrapper has multiple view roots"
                                )
                            }
                            viewRootSeen = true
                            return dataTagPresent
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == parentDepth) {
                        throw invalidPath(
                            childPath,
                            layoutName,
                            "data binding wrapper has no view root"
                        )
                    }
                }
                XmlPullParser.END_DOCUMENT -> throw invalidPath(
                    childPath,
                    layoutName,
                    "data binding wrapper has no view root"
                )
            }
        }
    }

    private fun skipDataBindingDataTag(parser: XmlPullParser, childPath: IntArray, layoutName: String) {
        val parentDepth = parser.depth
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.depth == parentDepth + 1) {
                        if (parser.name == "data") {
                            skipCurrentTag(parser)
                            return
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == parentDepth) {
                        throw invalidPath(
                            childPath,
                            layoutName,
                            "data binding wrapper has no data tag before view root"
                        )
                    }
                }
                XmlPullParser.END_DOCUMENT -> throw invalidPath(
                    childPath,
                    layoutName,
                    "data binding wrapper has no data tag before view root"
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

    private fun seekBeforeDirectChild(
        parser: XmlPullParser,
        targetIndex: Int,
        childPath: IntArray,
        traversed: List<Int>,
        layoutName: String
    ) {
        if (targetIndex < 0) {
            throw invalidPath(childPath, layoutName, "index $targetIndex is negative at ${traversed.toPathString()}")
        }

        if (targetIndex == 0) {
            return
        }

        val parentDepth = parser.depth
        var skippedChildCount = 0
        while (skippedChildCount < targetIndex) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.depth == parentDepth + 1) {
                        skippedChildCount++
                        skipCurrentTag(parser)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == parentDepth) {
                        throw invalidPath(
                            childPath,
                            layoutName,
                            "index $targetIndex is out of bounds at ${traversed.toPathString()} " +
                                "with childCount=$skippedChildCount"
                        )
                    }
                }
                XmlPullParser.END_DOCUMENT -> throw invalidPath(
                    childPath,
                    layoutName,
                    "index $targetIndex is out of bounds at ${traversed.toPathString()} " +
                        "with childCount=$skippedChildCount"
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
