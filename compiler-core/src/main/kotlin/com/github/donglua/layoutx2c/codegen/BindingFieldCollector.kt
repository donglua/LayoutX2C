package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.parser.LayoutNode
import com.squareup.kotlinpoet.ClassName

data class BindingField(
    val idName: String,
    val propertyName: String,
    val viewClass: ClassName
)

sealed interface BindingFieldResult {
    data class Success(val fields: List<BindingField>) : BindingFieldResult
    data class DuplicateIds(val ids: Set<String>) : BindingFieldResult
}

class BindingFieldCollector {
    fun collect(root: LayoutNode): BindingFieldResult {
        val fields = mutableListOf<BindingField>()
        collectFields(root, fields)

        val duplicateIds = fields
            .groupBy { it.idName }
            .filterValues { it.size > 1 }
            .keys
            .toSortedSet()

        if (duplicateIds.isNotEmpty()) {
            return BindingFieldResult.DuplicateIds(duplicateIds)
        }

        return BindingFieldResult.Success(fields)
    }

    private fun collectFields(node: LayoutNode, fields: MutableList<BindingField>) {
        node.attributes["android:id"]?.toIdName()?.let { idName ->
            fields += BindingField(
                idName = idName,
                propertyName = idName.toPropertyName(),
                viewClass = bindingViewClass(node.tagName)
            )
        }

        node.children.forEach { child -> collectFields(child, fields) }
    }

    private fun String.toIdName(): String {
        return removePrefix("@+id/").removePrefix("@id/")
    }

    private fun String.toPropertyName(): String {
        return split('_')
            .filter { it.isNotEmpty() }
            .mapIndexed { index, part ->
                if (index == 0) {
                    part
                } else {
                    part.replaceFirstChar { char -> char.uppercaseChar() }
                }
            }
            .joinToString("")
    }

    private fun bindingViewClass(tagName: String): ClassName {
        return when (tagName) {
            "LinearLayout", "android.widget.LinearLayout" -> ClassName("android.widget", "LinearLayout")
            "FrameLayout", "android.widget.FrameLayout" -> ClassName("android.widget", "FrameLayout")
            "RelativeLayout", "android.widget.RelativeLayout" -> ClassName("android.widget", "RelativeLayout")
            "ScrollView", "android.widget.ScrollView" -> ClassName("android.widget", "ScrollView")
            "HorizontalScrollView", "android.widget.HorizontalScrollView" -> ClassName(
                "android.widget",
                "HorizontalScrollView"
            )
            "TextView", "android.widget.TextView" -> ClassName("android.widget", "TextView")
            "Button", "android.widget.Button" -> ClassName("android.widget", "Button")
            "EditText", "android.widget.EditText" -> ClassName("android.widget", "EditText")
            "ImageView", "android.widget.ImageView" -> ClassName("android.widget", "ImageView")
            "View", "android.view.View" -> ClassName("android.view", "View")
            "androidx.appcompat.widget.AppCompatTextView" -> ClassName("androidx.appcompat.widget", "AppCompatTextView")
            "androidx.appcompat.widget.AppCompatButton" -> ClassName("androidx.appcompat.widget", "AppCompatButton")
            "androidx.appcompat.widget.AppCompatEditText" -> ClassName("androidx.appcompat.widget", "AppCompatEditText")
            "androidx.appcompat.widget.AppCompatImageView" -> ClassName(
                "androidx.appcompat.widget",
                "AppCompatImageView"
            )
            "androidx.recyclerview.widget.RecyclerView" -> ClassName("androidx.recyclerview.widget", "RecyclerView")
            else -> ClassName("android.view", "View")
        }
    }
}
