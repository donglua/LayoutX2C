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
        val tagName = node.node.tagName
        val viewClass = resolveViewClass(tagName)
        if (shouldUseViewFactoryHook(tagName)) {
            val viewFactoryCompat = ClassName("com.github.donglua.layoutx2c.runtime", "ViewFactoryCompat")
            if (hasAttributes) {
                builder.addStatement(
                    "val %L = %T.createView(context, %S, null) { %T(context) }.apply {",
                    varName,
                    viewFactoryCompat,
                    tagName,
                    viewClass
                )
            } else {
                builder.addStatement(
                    "val %L = %T.createView(context, %S, null) { %T(context) }",
                    varName,
                    viewFactoryCompat,
                    tagName,
                    viewClass
                )
            }
            return
        }

        if (hasAttributes) {
            builder.addStatement("val %L = %T(context).apply {", varName, viewClass)
        } else {
            builder.addStatement("val %L = %T(context)", varName, viewClass)
        }
    }

    private fun resolveViewClass(tagName: String): ClassName {
        return viewRegistry.viewHandlerFor(tagName)?.viewClass ?: ClassName.bestGuess(tagName)
    }

    private fun shouldUseViewFactoryHook(tagName: String): Boolean {
        if (!tagName.contains('.')) return false
        val frameworkPrefixes = listOf(
            "android.",
            "androidx.",
            "com.google.android.material."
        )
        return frameworkPrefixes.none { prefix -> tagName.startsWith(prefix) }
    }
}
