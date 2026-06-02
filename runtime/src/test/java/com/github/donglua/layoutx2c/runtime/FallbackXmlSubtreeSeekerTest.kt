package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.io.Reader

class FallbackXmlSubtreeSeekerTest {

    @Test
    fun `seek to nested child uses direct element child indexes`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "FrameLayout", 1),
            Event(XmlPullParser.START_TAG, "View", 2),
            Event(XmlPullParser.END_TAG, "View", 2),
            Event(XmlPullParser.START_TAG, "LinearLayout", 2),
            Event(XmlPullParser.START_TAG, "TextView", 3),
            Event(XmlPullParser.END_TAG, "TextView", 3),
            Event(XmlPullParser.START_TAG, "com.example.CustomView", 3),
            Event(XmlPullParser.END_TAG, "com.example.CustomView", 3),
            Event(XmlPullParser.END_TAG, "LinearLayout", 2),
            Event(XmlPullParser.END_TAG, "FrameLayout", 1),
        )

        val subtree = FallbackXmlSubtreeSeeker.seekToChild(parser, intArrayOf(1, 1), "demo_nested")

        assertThat(subtree.tagName).isEqualTo("com.example.CustomView")
        assertThat(parser.eventType).isEqualTo(XmlPullParser.START_TAG)
        assertThat(parser.depth).isEqualTo(3)
    }

    @Test
    fun `seek before root leaves parser before first start tag`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "FrameLayout", 1),
            Event(XmlPullParser.START_TAG, "TextView", 2),
            Event(XmlPullParser.END_TAG, "TextView", 2),
            Event(XmlPullParser.END_TAG, "FrameLayout", 1),
        )

        FallbackXmlSubtreeSeeker.seekBeforeChild(
            parser,
            intArrayOf(),
            "demo_root",
            dataBindingWrapped = false,
            dataBindingDataTagPresent = false
        )

        assertThat(parser.eventType).isEqualTo(XmlPullParser.START_DOCUMENT)
        assertThat(parser.depth).isEqualTo(0)
    }

    @Test
    fun `seek before first child leaves parser on parent start tag`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "FrameLayout", 1),
            Event(XmlPullParser.START_TAG, "TextView", 2),
            Event(XmlPullParser.END_TAG, "TextView", 2),
            Event(XmlPullParser.START_TAG, "Button", 2),
            Event(XmlPullParser.END_TAG, "Button", 2),
            Event(XmlPullParser.END_TAG, "FrameLayout", 1),
        )

        FallbackXmlSubtreeSeeker.seekBeforeChild(
            parser,
            intArrayOf(0),
            "demo_first_child",
            dataBindingWrapped = false,
            dataBindingDataTagPresent = false
        )

        assertThat(parser.eventType).isEqualTo(XmlPullParser.START_TAG)
        assertThat(parser.name).isEqualTo("FrameLayout")
        assertThat(parser.depth).isEqualTo(1)
    }

    @Test
    fun `seek before later child leaves parser after skipped sibling`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "FrameLayout", 1),
            Event(XmlPullParser.START_TAG, "TextView", 2),
            Event(XmlPullParser.END_TAG, "TextView", 2),
            Event(XmlPullParser.START_TAG, "Button", 2),
            Event(XmlPullParser.END_TAG, "Button", 2),
            Event(XmlPullParser.END_TAG, "FrameLayout", 1),
        )

        FallbackXmlSubtreeSeeker.seekBeforeChild(
            parser,
            intArrayOf(1),
            "demo_later_child",
            dataBindingWrapped = false,
            dataBindingDataTagPresent = false
        )

        assertThat(parser.eventType).isEqualTo(XmlPullParser.END_TAG)
        assertThat(parser.name).isEqualTo("TextView")
        assertThat(parser.depth).isEqualTo(2)
    }

    @Test
    fun `seek before nested child leaves parser on nested parent start tag`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "FrameLayout", 1),
            Event(XmlPullParser.START_TAG, "LinearLayout", 2),
            Event(XmlPullParser.START_TAG, "TextView", 3),
            Event(XmlPullParser.END_TAG, "TextView", 3),
            Event(XmlPullParser.END_TAG, "LinearLayout", 2),
            Event(XmlPullParser.END_TAG, "FrameLayout", 1),
        )

        FallbackXmlSubtreeSeeker.seekBeforeChild(
            parser,
            intArrayOf(0, 0),
            "demo_nested_first_child",
            dataBindingWrapped = false,
            dataBindingDataTagPresent = false
        )

        assertThat(parser.eventType).isEqualTo(XmlPullParser.START_TAG)
        assertThat(parser.name).isEqualTo("LinearLayout")
        assertThat(parser.depth).isEqualTo(2)
    }

    @Test
    fun `seek ignores nested descendants of skipped siblings`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "FrameLayout", 1),
            Event(XmlPullParser.START_TAG, "LinearLayout", 2),
            Event(XmlPullParser.START_TAG, "TextView", 3),
            Event(XmlPullParser.END_TAG, "TextView", 3),
            Event(XmlPullParser.END_TAG, "LinearLayout", 2),
            Event(XmlPullParser.START_TAG, "Button", 2),
            Event(XmlPullParser.END_TAG, "Button", 2),
            Event(XmlPullParser.END_TAG, "FrameLayout", 1),
        )

        val subtree = FallbackXmlSubtreeSeeker.seekToChild(parser, intArrayOf(1), "demo_siblings")

        assertThat(subtree.tagName).isEqualTo("Button")
        assertThat(parser.depth).isEqualTo(2)
    }

    @Test
    fun `empty path leaves parser on root tag`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "FrameLayout", 1),
            Event(XmlPullParser.START_TAG, "TextView", 2),
            Event(XmlPullParser.END_TAG, "TextView", 2),
            Event(XmlPullParser.END_TAG, "FrameLayout", 1),
        )

        val subtree = FallbackXmlSubtreeSeeker.seekToChild(parser, intArrayOf(), "demo_root")

        assertThat(subtree.tagName).isEqualTo("FrameLayout")
        assertThat(parser.eventType).isEqualTo(XmlPullParser.START_TAG)
        assertThat(parser.depth).isEqualTo(1)
    }

    @Test
    fun `data binding wrapper paths start at the unwrapped view root`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "layout", 1),
            Event(XmlPullParser.START_TAG, "data", 2),
            Event(XmlPullParser.START_TAG, "variable", 3),
            Event(XmlPullParser.END_TAG, "variable", 3),
            Event(XmlPullParser.END_TAG, "data", 2),
            Event(XmlPullParser.START_TAG, "LinearLayout", 2),
            Event(XmlPullParser.START_TAG, "TextView", 3),
            Event(XmlPullParser.END_TAG, "TextView", 3),
            Event(XmlPullParser.START_TAG, "com.example.CustomView", 3),
            Event(XmlPullParser.END_TAG, "com.example.CustomView", 3),
            Event(XmlPullParser.END_TAG, "LinearLayout", 2),
            Event(XmlPullParser.END_TAG, "layout", 1),
        )

        val subtree = FallbackXmlSubtreeSeeker.seekToChild(parser, intArrayOf(1), "demo_data_binding")

        assertThat(subtree.tagName).isEqualTo("com.example.CustomView")
        assertThat(subtree.dataBindingWrapped).isTrue()
        assertThat(subtree.dataBindingDataTagPresent).isTrue()
        assertThat(parser.eventType).isEqualTo(XmlPullParser.START_TAG)
        assertThat(parser.depth).isEqualTo(3)
    }

    @Test
    fun `seek before data binding root with data tag leaves parser after data tag`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "layout", 1),
            Event(XmlPullParser.START_TAG, "data", 2),
            Event(XmlPullParser.START_TAG, "variable", 3),
            Event(XmlPullParser.END_TAG, "variable", 3),
            Event(XmlPullParser.END_TAG, "data", 2),
            Event(XmlPullParser.START_TAG, "LinearLayout", 2),
            Event(XmlPullParser.START_TAG, "TextView", 3),
            Event(XmlPullParser.END_TAG, "TextView", 3),
            Event(XmlPullParser.END_TAG, "LinearLayout", 2),
            Event(XmlPullParser.END_TAG, "layout", 1),
        )

        FallbackXmlSubtreeSeeker.seekBeforeChild(
            parser,
            intArrayOf(),
            "demo_data_binding_root",
            dataBindingWrapped = true,
            dataBindingDataTagPresent = true
        )

        assertThat(parser.eventType).isEqualTo(XmlPullParser.END_TAG)
        assertThat(parser.name).isEqualTo("data")
        assertThat(parser.depth).isEqualTo(2)
    }

    @Test
    fun `seek before data binding child leaves parser on view root start tag`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "layout", 1),
            Event(XmlPullParser.START_TAG, "data", 2),
            Event(XmlPullParser.START_TAG, "variable", 3),
            Event(XmlPullParser.END_TAG, "variable", 3),
            Event(XmlPullParser.END_TAG, "data", 2),
            Event(XmlPullParser.START_TAG, "LinearLayout", 2),
            Event(XmlPullParser.START_TAG, "TextView", 3),
            Event(XmlPullParser.END_TAG, "TextView", 3),
            Event(XmlPullParser.START_TAG, "Button", 3),
            Event(XmlPullParser.END_TAG, "Button", 3),
            Event(XmlPullParser.END_TAG, "LinearLayout", 2),
            Event(XmlPullParser.END_TAG, "layout", 1),
        )

        FallbackXmlSubtreeSeeker.seekBeforeChild(
            parser,
            intArrayOf(0),
            "demo_data_binding_child",
            dataBindingWrapped = true,
            dataBindingDataTagPresent = true
        )

        assertThat(parser.eventType).isEqualTo(XmlPullParser.START_TAG)
        assertThat(parser.name).isEqualTo("LinearLayout")
        assertThat(parser.depth).isEqualTo(2)
    }

    @Test
    fun `out of bounds path reports layout path and child count`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "FrameLayout", 1),
            Event(XmlPullParser.START_TAG, "View", 2),
            Event(XmlPullParser.END_TAG, "View", 2),
            Event(XmlPullParser.END_TAG, "FrameLayout", 1),
        )

        val error = runCatching {
            FallbackXmlSubtreeSeeker.seekToChild(parser, intArrayOf(1), "demo_invalid")
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("demo_invalid")
        assertThat(error).hasMessageThat().contains("[1]")
        assertThat(error).hasMessageThat().contains("out of bounds")
        assertThat(error).hasMessageThat().contains("childCount=1")
    }

    @Test
    fun `negative index reports layout path and traversed parent`() {
        val parser = parserFor(
            Event(XmlPullParser.START_TAG, "FrameLayout", 1),
            Event(XmlPullParser.START_TAG, "View", 2),
            Event(XmlPullParser.END_TAG, "View", 2),
            Event(XmlPullParser.END_TAG, "FrameLayout", 1),
        )

        val error = runCatching {
            FallbackXmlSubtreeSeeker.seekToChild(parser, intArrayOf(-1), "demo_invalid")
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("demo_invalid")
        assertThat(error).hasMessageThat().contains("[-1]")
        assertThat(error).hasMessageThat().contains("index -1 is negative at <root>")
    }

    private fun parserFor(vararg events: Event): XmlPullParser {
        return TestXmlPullParser(events.toList())
    }

    private data class Event(
        val type: Int,
        val name: String?,
        val depth: Int
    )

    private class TestXmlPullParser(
        private val events: List<Event>
    ) : XmlPullParser {
        private var index = -1
        private val current: Event?
            get() = events.getOrNull(index)

        override fun next(): Int {
            index++
            return current?.type ?: XmlPullParser.END_DOCUMENT
        }

        override fun getEventType(): Int {
            return current?.type ?: XmlPullParser.START_DOCUMENT
        }

        override fun getName(): String? = current?.name

        override fun getDepth(): Int = current?.depth ?: 0

        override fun defineEntityReplacementText(entityName: String?, replacementText: String?) = unsupported()
        override fun getAttributeCount(): Int = unsupported()
        override fun getAttributeName(index: Int): String = unsupported()
        override fun getAttributeNamespace(index: Int): String = unsupported()
        override fun getAttributePrefix(index: Int): String = unsupported()
        override fun getAttributeType(index: Int): String = unsupported()
        override fun getAttributeValue(index: Int): String = unsupported()
        override fun getAttributeValue(namespace: String?, name: String?): String? = unsupported()
        override fun getColumnNumber(): Int = unsupported()
        override fun getFeature(name: String?): Boolean = unsupported()
        override fun getInputEncoding(): String? = unsupported()
        override fun getLineNumber(): Int = unsupported()
        override fun getNamespace(): String? = unsupported()
        override fun getNamespace(prefix: String?): String? = unsupported()
        override fun getNamespaceCount(depth: Int): Int = unsupported()
        override fun getNamespacePrefix(pos: Int): String? = unsupported()
        override fun getNamespaceUri(pos: Int): String = unsupported()
        override fun getPositionDescription(): String = unsupported()
        override fun getPrefix(): String? = unsupported()
        override fun getProperty(name: String?): Any? = unsupported()
        override fun getText(): String? = unsupported()
        override fun getTextCharacters(holderForStartAndLength: IntArray?): CharArray = unsupported()
        override fun isAttributeDefault(index: Int): Boolean = unsupported()
        override fun isEmptyElementTag(): Boolean = unsupported()
        override fun isWhitespace(): Boolean = unsupported()
        override fun nextTag(): Int = unsupported()
        override fun nextText(): String = unsupported()
        override fun nextToken(): Int = unsupported()
        override fun require(type: Int, namespace: String?, name: String?) = unsupported()
        override fun setFeature(name: String?, state: Boolean) = unsupported()
        override fun setInput(inputStream: InputStream?, inputEncoding: String?) = unsupported()
        override fun setInput(reader: Reader?) = unsupported()
        override fun setProperty(name: String?, value: Any?) = unsupported()

        private fun unsupported(): Nothing {
            throw XmlPullParserException("Unsupported in test parser")
        }
    }
}
