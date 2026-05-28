package com.github.donglua.layoutx2c.parser

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 解析 Android layout XML 文件为 LayoutTree。
 */
class XmlLayoutParser {

    fun parse(xmlFile: File): LayoutTree {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(xmlFile)
        val rootElement = document.documentElement

        val rootNode = parseLayoutRoot(rootElement)
        return LayoutTree(root = rootNode, fileName = xmlFile.nameWithoutExtension)
    }

    fun parse(xmlContent: String, fileName: String): LayoutTree {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(xmlContent.byteInputStream())
        val rootElement = document.documentElement

        val rootNode = parseLayoutRoot(rootElement)
        return LayoutTree(root = rootNode, fileName = fileName)
    }

    private fun parseLayoutRoot(rootElement: Element): LayoutNode {
        if (rootElement.tagName != "layout") {
            return parseElement(rootElement, 0)
        }

        val elementChildren = rootElement.elementChildren()
        val viewRoot = elementChildren.singleOrNull { it.element.tagName != "data" }
            ?: return parseElement(rootElement, 0)
        return parseElement(viewRoot.element, 0)
    }

    private fun parseElement(element: Element, indexInParent: Int): LayoutNode {
        val tagName = element.tagName
        val attributes = mutableMapOf<String, String>()

        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            attributes[attr.nodeName] = attr.nodeValue
        }

        val children = mutableListOf<LayoutNode>()
        var childIndex = 0
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(parseElement(child as Element, childIndex))
                childIndex++
            }
        }

        return LayoutNode(
            tagName = tagName,
            attributes = attributes,
            children = children,
            indexInParent = indexInParent
        )
    }

    private fun Element.elementChildren(): List<IndexedElement> {
        val children = mutableListOf<IndexedElement>()
        var childIndex = 0
        val childNodes = childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(IndexedElement(childIndex, child as Element))
                childIndex++
            }
        }
        return children
    }

    private data class IndexedElement(
        val index: Int,
        val element: Element
    )
}
