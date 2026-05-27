package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.registry.DefaultViewRegistry
import com.github.donglua.layoutx2c.registry.ViewEmitRegistry
import com.squareup.kotlinpoet.CodeBlock

interface AttrEmitter {
    fun emit(builder: CodeBlock.Builder, node: AnalyzedNode)
}

class DefaultAttrEmitter(
    private val viewRegistry: ViewEmitRegistry = DefaultViewRegistry
) : AttrEmitter {

    override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
        viewRegistry.emitAttributes(builder, node)
    }
}
