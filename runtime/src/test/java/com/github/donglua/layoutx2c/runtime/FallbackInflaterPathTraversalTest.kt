package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertThat
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class FallbackInflaterPathTraversalTest {

    @Test
    fun `child path helpers compare and detect overlap`() {
        assertThat(compare(intArrayOf(0), intArrayOf(1))).isLessThan(0)
        assertThat(compare(intArrayOf(1, 0), intArrayOf(1))).isGreaterThan(0)
        assertThat(compare(intArrayOf(2), intArrayOf(1, 9))).isGreaterThan(0)

        assertThat(hasOverlapping(arrayOf(intArrayOf(0), intArrayOf(1, 0), intArrayOf(2)))).isFalse()
        assertThat(hasOverlapping(arrayOf(intArrayOf(1), intArrayOf(1, 0)))).isTrue()
        assertThat(hasOverlapping(arrayOf(intArrayOf(2), intArrayOf(2)))).isTrue()
    }

    @Test
    fun `seek to child start tag walks ordinary and data binding layouts`() {
        val ordinary = parser(
            event(XmlPullParser.START_TAG, depth = 1, name = "LinearLayout"),
            event(XmlPullParser.START_TAG, depth = 2, name = "TextView"),
            event(XmlPullParser.END_TAG, depth = 2, name = "TextView"),
            event(XmlPullParser.START_TAG, depth = 2, name = "FrameLayout"),
            event(XmlPullParser.START_TAG, depth = 3, name = "ImageView")
        )
        val dataBinding = parser(
            event(XmlPullParser.START_TAG, depth = 1, name = "layout"),
            event(XmlPullParser.START_TAG, depth = 2, name = "data"),
            event(XmlPullParser.END_TAG, depth = 2, name = "data"),
            event(XmlPullParser.START_TAG, depth = 2, name = "LinearLayout"),
            event(XmlPullParser.START_TAG, depth = 3, name = "TextView")
        )

        assertThat(seekToChildStartTag(ordinary, intArrayOf(1, 0), "ordinary_layout")).isEqualTo("ImageView")
        assertThat(seekToChildStartTag(dataBinding, intArrayOf(0), "binding_layout")).isEqualTo("TextView")
    }

    @Test
    fun `seek reports missing roots and missing children with path context`() {
        val noRoot = parser(event(XmlPullParser.END_DOCUMENT, depth = 0))
        val missingChild = parser(
            event(XmlPullParser.START_TAG, depth = 1, name = "FrameLayout"),
            event(XmlPullParser.START_TAG, depth = 2, name = "TextView"),
            event(XmlPullParser.END_TAG, depth = 2, name = "TextView"),
            event(XmlPullParser.END_TAG, depth = 1, name = "FrameLayout")
        )

        assertThat(errorFrom { seekToChildStartTag(noRoot, intArrayOf(0), "empty_layout") })
            .hasMessageThat().contains("layout XML has no root tag")
        assertThat(errorFrom { seekToChildStartTag(missingChild, intArrayOf(2), "missing_child") })
            .hasMessageThat().contains("index 2 is out of bounds at <root> with childCount=1")
        assertThat(errorFrom {
            moveToChildAt(parser(), -1, emptyList(), intArrayOf(-1), "negative_child")
        }).hasMessageThat().contains("index -1 is out of bounds at <root>")
    }

    @Test
    fun `data binding root traversal reports layouts without view root`() {
        val closedLayout = parser(
            event(XmlPullParser.START_TAG, depth = 1, name = "layout"),
            event(XmlPullParser.START_TAG, depth = 2, name = "data"),
            event(XmlPullParser.END_TAG, depth = 2, name = "data"),
            event(XmlPullParser.END_TAG, depth = 1, name = "layout")
        )
        val endDocument = parser(
            event(XmlPullParser.START_TAG, depth = 1, name = "layout"),
            event(XmlPullParser.END_DOCUMENT, depth = 0)
        )
        moveToFirstStartTag(closedLayout, "closed_binding")
        moveToFirstStartTag(endDocument, "ended_binding")

        assertThat(errorFrom { moveToDataBindingViewRoot(closedLayout, "closed_binding") })
            .hasMessageThat().contains("data binding layout has no view root")
        assertThat(errorFrom { moveToDataBindingViewRoot(endDocument, "ended_binding") })
            .hasMessageThat().contains("data binding layout has no view root")
    }

    @Test
    fun `skip current subtree consumes nested tags through matching end tag`() {
        val parser = parser(
            event(XmlPullParser.START_TAG, depth = 2, name = "FrameLayout"),
            event(XmlPullParser.START_TAG, depth = 3, name = "TextView"),
            event(XmlPullParser.END_TAG, depth = 3, name = "TextView"),
            event(XmlPullParser.END_TAG, depth = 2, name = "FrameLayout"),
            event(XmlPullParser.START_TAG, depth = 2, name = "Sibling")
        )
        parser.next()

        skipCurrentSubtree(parser)

        assertThat(parser.next()).isEqualTo(XmlPullParser.START_TAG)
        assertThat(parser.name).isEqualTo("Sibling")
    }

    private fun compare(first: IntArray, second: IntArray): Int {
        return invoke("compareChildPaths", IntArray::class.java, IntArray::class.java, args = arrayOf(first, second))
    }

    private fun hasOverlapping(paths: Array<IntArray>): Boolean {
        return invoke("hasOverlappingChildPaths", Array<IntArray>::class.java, args = arrayOf(paths))
    }

    private fun seekToChildStartTag(parser: XmlPullParser, childPath: IntArray, layoutName: String): String {
        return invoke(
            "seekToChildStartTag",
            XmlPullParser::class.java,
            IntArray::class.java,
            String::class.java,
            args = arrayOf(parser, childPath, layoutName)
        )
    }

    private fun moveToFirstStartTag(parser: XmlPullParser, layoutName: String) {
        invoke<Unit>(
            "moveToFirstStartTag",
            XmlPullParser::class.java,
            String::class.java,
            args = arrayOf(parser, layoutName)
        )
    }

    private fun moveToDataBindingViewRoot(parser: XmlPullParser, layoutName: String) {
        invoke<Unit>(
            "moveToDataBindingViewRoot",
            XmlPullParser::class.java,
            String::class.java,
            args = arrayOf(parser, layoutName)
        )
    }

    private fun moveToChildAt(
        parser: XmlPullParser,
        targetIndex: Int,
        traversed: List<Int>,
        childPath: IntArray,
        layoutName: String
    ) {
        invoke<Unit>(
            "moveToChildAt",
            XmlPullParser::class.java,
            Int::class.javaPrimitiveType!!,
            List::class.java,
            IntArray::class.java,
            String::class.java,
            args = arrayOf(parser, targetIndex, traversed, childPath, layoutName)
        )
    }

    private fun skipCurrentSubtree(parser: XmlPullParser) {
        invoke<Unit>("skipCurrentSubtree", XmlPullParser::class.java, args = arrayOf(parser))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> invoke(name: String, vararg parameterTypes: Class<*>, args: Array<Any?>): T {
        val method: Method = FallbackInflater::class.java
            .getDeclaredMethod(name, *parameterTypes)
            .apply { isAccessible = true }
        return try {
            method.invoke(FallbackInflater, *args) as T
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun errorFrom(block: () -> Unit): Throwable {
        return runCatching(block).exceptionOrNull() ?: error("Expected block to throw")
    }

    private fun parser(vararg events: Event): XmlPullParser {
        var index = -1
        return Proxy.newProxyInstance(
            XmlPullParser::class.java.classLoader,
            arrayOf(XmlPullParser::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "next" -> {
                    index += 1
                    events.getOrNull(index)?.type ?: XmlPullParser.END_DOCUMENT
                }
                "getDepth" -> events.getOrNull(index)?.depth ?: 0
                "getName" -> events.getOrNull(index)?.name
                else -> defaultReturn(method.returnType)
            }
        } as XmlPullParser
    }

    private fun defaultReturn(returnType: Class<*>): Any? {
        return when (returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            else -> null
        }
    }

    private fun event(type: Int, depth: Int, name: String? = null): Event {
        return Event(type, depth, name)
    }

    private data class Event(
        val type: Int,
        val depth: Int,
        val name: String? = null
    )
}
