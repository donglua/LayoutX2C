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

        val rootNode = parseElement(rootElement, 0)
        return LayoutTree(root = rootNode, fileName = xmlFile.nameWithoutExtension)
    }

    fun parse(xmlContent: String, fileName: String): LayoutTree {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(xmlContent.byteInputStream())
        val rootElement = document.documentElement

        val rootNode = parseElement(rootElement, 0)
        return LayoutTree(root = rootNode, fileName = fileName)
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
}
