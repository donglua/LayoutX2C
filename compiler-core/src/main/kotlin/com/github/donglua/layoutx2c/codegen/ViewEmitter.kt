package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.registry.DefaultViewRegistry
import com.github.donglua.layoutx2c.registry.ViewEmitRegistry
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

interface ViewEmitter {
    fun emitCreate(builder: CodeBlock.Builder, varName: String, node: AnalyzedNode, hasAttributes: Boolean)
}

class DefaultViewEmitter(
    private val viewRegistry: ViewEmitRegistry = DefaultViewRegistry
) : ViewEmitter {

    override fun emitCreate(builder: CodeBlock.Builder, varName: String, node: AnalyzedNode, hasAttributes: Boolean) {
        if (hasAttributes) {
            builder.addStatement("val %L = %T(context).apply {", varName, resolveViewClass(node.node.tagName))
        } else {
            builder.addStatement("val %L = %T(context)", varName, resolveViewClass(node.node.tagName))
        }
    }

    private fun resolveViewClass(tagName: String): ClassName {
        return viewRegistry.viewHandlerFor(tagName)?.viewClass ?: ClassName.bestGuess(tagName)
    }
}
