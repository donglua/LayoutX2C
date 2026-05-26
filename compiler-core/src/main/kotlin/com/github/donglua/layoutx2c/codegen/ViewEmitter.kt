package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

interface ViewEmitter {
    fun emitCreate(builder: CodeBlock.Builder, varName: String, node: AnalyzedNode, hasAttributes: Boolean)
}

class DefaultViewEmitter : ViewEmitter {

    override fun emitCreate(builder: CodeBlock.Builder, varName: String, node: AnalyzedNode, hasAttributes: Boolean) {
        if (hasAttributes) {
            builder.addStatement("val %L = %T(context).apply {", varName, resolveViewClass(node.node.tagName))
        } else {
            builder.addStatement("val %L = %T(context)", varName, resolveViewClass(node.node.tagName))
        }
    }

    private fun resolveViewClass(tagName: String): ClassName {
        return when (tagName) {
            "LinearLayout", "android.widget.LinearLayout" ->
                ClassName("android.widget", "LinearLayout")
            "FrameLayout", "android.widget.FrameLayout" ->
                ClassName("android.widget", "FrameLayout")
            "RelativeLayout", "android.widget.RelativeLayout" ->
                ClassName("android.widget", "RelativeLayout")
            "androidx.recyclerview.widget.RecyclerView" ->
                ClassName("androidx.recyclerview.widget", "RecyclerView")
            "ScrollView", "android.widget.ScrollView" ->
                ClassName("android.widget", "ScrollView")
            "HorizontalScrollView", "android.widget.HorizontalScrollView" ->
                ClassName("android.widget", "HorizontalScrollView")
            "TextView", "android.widget.TextView" ->
                ClassName("androidx.appcompat.widget", "AppCompatTextView")
            "Button", "android.widget.Button", "androidx.appcompat.widget.AppCompatButton" ->
                ClassName("androidx.appcompat.widget", "AppCompatButton")
            "EditText", "android.widget.EditText", "androidx.appcompat.widget.AppCompatEditText" ->
                ClassName("androidx.appcompat.widget", "AppCompatEditText")
            "ImageView", "android.widget.ImageView", "androidx.appcompat.widget.AppCompatImageView" ->
                ClassName("androidx.appcompat.widget", "AppCompatImageView")
            "View", "android.view.View" ->
                ClassName("android.view", "View")
            else -> ClassName.bestGuess(tagName)
        }
    }
}
