package com.github.donglua.layoutx2c.runtime

import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.io.Reader

/**
 * Replays the current START_TAG once because LayoutInflater.inflate(XmlPullParser, ...)
 * calls next() before creating the root view.
 */
internal class ReplayStartTagXmlResourceParser(
    private val delegate: XmlResourceParser
) : XmlResourceParser {
    private var replayStartTag = true

    override fun close() {
        delegate.close()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    override fun next(): Int {
        if (replayStartTag) {
            replayStartTag = false
            return XmlPullParser.START_TAG
        }
        return delegate.next()
    }

    override fun getEventType(): Int {
        return if (replayStartTag) XmlPullParser.START_TAG else delegate.eventType
    }

    override fun getAttributeBooleanValue(index: Int, defaultValue: Boolean): Boolean {
        return delegate.getAttributeBooleanValue(index, defaultValue)
    }

    override fun getAttributeBooleanValue(namespace: String?, attribute: String?, defaultValue: Boolean): Boolean {
        return delegate.getAttributeBooleanValue(namespace, attribute, defaultValue)
    }

    override fun getAttributeCount(): Int {
        return delegate.attributeCount
    }

    override fun getAttributeFloatValue(index: Int, defaultValue: Float): Float {
        return delegate.getAttributeFloatValue(index, defaultValue)
    }

    override fun getAttributeFloatValue(namespace: String?, attribute: String?, defaultValue: Float): Float {
        return delegate.getAttributeFloatValue(namespace, attribute, defaultValue)
    }

    override fun getAttributeIntValue(index: Int, defaultValue: Int): Int {
        return delegate.getAttributeIntValue(index, defaultValue)
    }

    override fun getAttributeIntValue(namespace: String?, attribute: String?, defaultValue: Int): Int {
        return delegate.getAttributeIntValue(namespace, attribute, defaultValue)
    }

    override fun getAttributeListValue(index: Int, options: Array<out String>?, defaultValue: Int): Int {
        return delegate.getAttributeListValue(index, options, defaultValue)
    }

    override fun getAttributeListValue(
        namespace: String?,
        attribute: String?,
        options: Array<out String>?,
        defaultValue: Int
    ): Int {
        return delegate.getAttributeListValue(namespace, attribute, options, defaultValue)
    }

    override fun getAttributeName(index: Int): String {
        return delegate.getAttributeName(index)
    }

    override fun getAttributeNameResource(index: Int): Int {
        return delegate.getAttributeNameResource(index)
    }

    override fun getAttributeNamespace(index: Int): String {
        return delegate.getAttributeNamespace(index)
    }

    override fun getAttributePrefix(index: Int): String {
        return delegate.getAttributePrefix(index)
    }

    override fun getAttributeResourceValue(index: Int, defaultValue: Int): Int {
        return delegate.getAttributeResourceValue(index, defaultValue)
    }

    override fun getAttributeResourceValue(namespace: String?, attribute: String?, defaultValue: Int): Int {
        return delegate.getAttributeResourceValue(namespace, attribute, defaultValue)
    }

    override fun getAttributeType(index: Int): String {
        return delegate.getAttributeType(index)
    }

    override fun getAttributeUnsignedIntValue(index: Int, defaultValue: Int): Int {
        return delegate.getAttributeUnsignedIntValue(index, defaultValue)
    }

    override fun getAttributeUnsignedIntValue(namespace: String?, attribute: String?, defaultValue: Int): Int {
        return delegate.getAttributeUnsignedIntValue(namespace, attribute, defaultValue)
    }

    override fun getAttributeValue(index: Int): String {
        return delegate.getAttributeValue(index)
    }

    override fun getAttributeValue(namespace: String?, name: String?): String? {
        return delegate.getAttributeValue(namespace, name)
    }

    override fun getClassAttribute(): String? {
        return delegate.classAttribute
    }

    override fun getColumnNumber(): Int {
        return delegate.columnNumber
    }

    override fun getDepth(): Int {
        return delegate.depth
    }

    override fun getFeature(name: String?): Boolean {
        return delegate.getFeature(name)
    }

    override fun getIdAttribute(): String? {
        return delegate.idAttribute
    }

    override fun getIdAttributeResourceValue(defaultValue: Int): Int {
        return delegate.getIdAttributeResourceValue(defaultValue)
    }

    override fun getInputEncoding(): String? {
        return delegate.inputEncoding
    }

    override fun getLineNumber(): Int {
        return delegate.lineNumber
    }

    override fun getName(): String {
        return delegate.name
    }

    override fun getNamespace(): String? {
        return delegate.namespace
    }

    override fun getNamespace(prefix: String?): String? {
        return delegate.getNamespace(prefix)
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespaceCount(depth: Int): Int {
        return delegate.getNamespaceCount(depth)
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespacePrefix(pos: Int): String? {
        return delegate.getNamespacePrefix(pos)
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespaceUri(pos: Int): String {
        return delegate.getNamespaceUri(pos)
    }

    override fun getPositionDescription(): String {
        return delegate.positionDescription
    }

    override fun getPrefix(): String? {
        return delegate.prefix
    }

    override fun getProperty(name: String?): Any? {
        return delegate.getProperty(name)
    }

    override fun getStyleAttribute(): Int {
        return delegate.styleAttribute
    }

    override fun getText(): String? {
        return delegate.text
    }

    override fun getTextCharacters(holderForStartAndLength: IntArray?): CharArray? {
        return delegate.getTextCharacters(holderForStartAndLength)
    }

    @Throws(XmlPullParserException::class)
    override fun isAttributeDefault(index: Int): Boolean {
        return delegate.isAttributeDefault(index)
    }

    @Throws(XmlPullParserException::class)
    override fun isEmptyElementTag(): Boolean {
        return delegate.isEmptyElementTag
    }

    @Throws(XmlPullParserException::class)
    override fun isWhitespace(): Boolean {
        return delegate.isWhitespace
    }

    @Throws(IOException::class, XmlPullParserException::class)
    override fun nextTag(): Int {
        if (replayStartTag) {
            replayStartTag = false
            return XmlPullParser.START_TAG
        }
        return delegate.nextTag()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    override fun nextText(): String {
        if (replayStartTag) {
            // nextText() must run while the underlying parser is still on the current START_TAG.
            replayStartTag = false
        }
        return delegate.nextText()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    override fun nextToken(): Int {
        if (replayStartTag) {
            replayStartTag = false
            return XmlPullParser.START_TAG
        }
        return delegate.nextToken()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    override fun require(type: Int, namespace: String?, name: String?) {
        if (replayStartTag) {
            if (type != XmlPullParser.START_TAG || (namespace != null && namespace != getNamespace()) ||
                (name != null && name != getName())
            ) {
                throw XmlPullParserException("expected ${XmlPullParser.TYPES[type]}", this, null)
            }
            return
        }
        delegate.require(type, namespace, name)
    }

    @Throws(XmlPullParserException::class)
    override fun setFeature(name: String?, state: Boolean) {
        delegate.setFeature(name, state)
    }

    @Throws(XmlPullParserException::class)
    override fun setInput(inputStream: InputStream?, inputEncoding: String?) {
        delegate.setInput(inputStream, inputEncoding)
    }

    @Throws(XmlPullParserException::class)
    override fun setInput(reader: Reader?) {
        delegate.setInput(reader)
    }

    @Throws(XmlPullParserException::class)
    override fun setProperty(name: String?, value: Any?) {
        delegate.setProperty(name, value)
    }

    @Throws(XmlPullParserException::class)
    override fun defineEntityReplacementText(entityName: String?, replacementText: String?) {
        delegate.defineEntityReplacementText(entityName, replacementText)
    }
}
