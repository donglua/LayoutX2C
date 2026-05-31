package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.LayoutNodeType
import org.junit.Test

class DebugIncludeTest {
    @Test
    fun debug() {
        val child = LayoutNode(
            tagName = "LinearLayout",
            attributes = emptyMap(),
            children = emptyList(),
            indexInParent = 0,
            nodeType = LayoutNodeType.Include("title_bar")
        )
        val rootNode = LayoutNode(
            tagName = "FrameLayout",
            attributes = mapOf("android:layout_width" to "match_parent", "android:layout_height" to "match_parent"),
            children = listOf(child),
            indexInParent = 0
        )
        val analyzer = LayoutAnalyzer()
        val analyzed = analyzer.analyze(rootNode)
        println("Include child includedLayoutRef: " + analyzed.children[0].includedLayoutRef)
    }
}
