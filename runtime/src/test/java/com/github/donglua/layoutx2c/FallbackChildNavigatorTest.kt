package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FallbackChildNavigatorTest {

    @Test
    fun `find child by path returns nested child`() {
        val target = TestNode()
        val root = TestNode(TestNode(), TestNode(target))

        val result = FallbackChildNavigator.findChildByPath(root, intArrayOf(1, 0), "demo_nested")

        assertThat(result).isSameInstanceAs(target)
    }

    @Test
    fun `find child by path reports out of bounds index with layout and path`() {
        val root = TestNode(TestNode())

        val error = runCatching {
            FallbackChildNavigator.findChildByPath(root, intArrayOf(1), "demo_nested")
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("demo_nested")
        assertThat(error).hasMessageThat().contains("[1]")
        assertThat(error).hasMessageThat().contains("out of bounds")
        assertThat(error).hasMessageThat().contains("childCount=1")
    }

    @Test
    fun `find child by path reports non container intermediate node`() {
        val root = TestNode(LeafNode())

        val error = runCatching {
            FallbackChildNavigator.findChildByPath(root, intArrayOf(0, 0), "demo_nested")
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("demo_nested")
        assertThat(error).hasMessageThat().contains("[0, 0]")
        assertThat(error).hasMessageThat().contains("[0]")
        assertThat(error).hasMessageThat().contains("not a ViewGroup")
    }

    private class TestNode(
        private vararg val children: FallbackChildNode
    ) : FallbackChildNode {
        override val isContainer: Boolean = true

        override val childCount: Int
            get() = children.size

        override fun childAt(index: Int): FallbackChildNode {
            return children[index]
        }
    }

    private class LeafNode : FallbackChildNode {
        override val isContainer: Boolean = false

        override val childCount: Int
            get() = 0

        override fun childAt(index: Int): FallbackChildNode {
            throw AssertionError("Leaf nodes should not be traversed")
        }
    }
}
