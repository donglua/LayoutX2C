package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.LayoutNodeType
import com.squareup.kotlinpoet.ClassName

data class BindingField(
    val idName: String,
    val propertyName: String,
    val viewClass: ClassName,
    val isNestedBinding: Boolean = false,
    val nestedBindingLayoutName: String? = null
)

sealed interface BindingFieldResult {
    data class Success(val fields: List<BindingField>) : BindingFieldResult
    data class DuplicateIds(val ids: Set<String>) : BindingFieldResult
}

class BindingFieldCollector(
    private val bindingPackageName: String = ""
) {
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
        val includeNode = node.nodeType as? LayoutNodeType.Include
        node.attributes["android:id"]?.toIdName()?.let { idName ->
            val isNestedBinding = includeNode?.isDataBindingLayout == true && node.tagName != "merge"
            fields += BindingField(
                idName = idName,
                propertyName = idName.toPropertyName(),
                viewClass = bindingViewClass(node, isNestedBinding),
                isNestedBinding = isNestedBinding,
                nestedBindingLayoutName = includeNode?.layoutRef?.takeIf { isNestedBinding }
            )
        }

        if (includeNode != null) {
            return
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

    private fun bindingViewClass(node: LayoutNode, isNestedBinding: Boolean = false): ClassName {
        if (isNestedBinding) {
            val includeNode = node.nodeType as LayoutNodeType.Include
            return ClassName(bindingPackageName, includeNode.layoutRef.toPascalCase() + "Binding")
        }

        val tagName = node.attributes["class"]?.takeIf { node.tagName == "view" && it.isNotBlank() } ?: node.tagName

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
            "CheckBox", "android.widget.CheckBox" -> ClassName("android.widget", "CheckBox")
            "Switch", "android.widget.Switch" -> ClassName("android.widget", "Switch")
            "RadioButton", "android.widget.RadioButton" -> ClassName("android.widget", "RadioButton")
            "ToggleButton", "android.widget.ToggleButton" -> ClassName("android.widget", "ToggleButton")
            "ImageView", "android.widget.ImageView" -> ClassName("android.widget", "ImageView")
            "View", "android.view.View" -> ClassName("android.view", "View")
            "ViewStub", "android.view.ViewStub" -> ClassName("android.view", "ViewStub")
            "androidx.appcompat.widget.AppCompatTextView" -> ClassName("androidx.appcompat.widget", "AppCompatTextView")
            "androidx.appcompat.widget.AppCompatButton" -> ClassName("androidx.appcompat.widget", "AppCompatButton")
            "androidx.appcompat.widget.AppCompatEditText" -> ClassName("androidx.appcompat.widget", "AppCompatEditText")
            "androidx.appcompat.widget.AppCompatCheckBox" -> ClassName(
                "androidx.appcompat.widget",
                "AppCompatCheckBox"
            )
            "androidx.appcompat.widget.SwitchCompat" -> ClassName("androidx.appcompat.widget", "SwitchCompat")
            "androidx.appcompat.widget.AppCompatRadioButton" -> ClassName(
                "androidx.appcompat.widget",
                "AppCompatRadioButton"
            )
            "androidx.appcompat.widget.AppCompatImageView" -> ClassName(
                "androidx.appcompat.widget",
                "AppCompatImageView"
            )
            "androidx.recyclerview.widget.RecyclerView" -> ClassName("androidx.recyclerview.widget", "RecyclerView")
            else -> if (tagName.isFullyQualifiedClassName()) {
                ClassName.bestGuess(tagName)
            } else {
                ClassName("android.view", "View")
            }
        }
    }

    private fun String.isFullyQualifiedClassName(): Boolean {
        return '.' in this && split('.').all { it.isKotlinIdentifierPart() }
    }

    private fun String.isKotlinIdentifierPart(): Boolean {
        if (isEmpty()) return false
        if (!first().isLetter() && first() != '_') return false
        return drop(1).all { it.isLetterOrDigit() || it == '_' }
    }

    private fun String.toPascalCase(): String {
        return split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        }
    }
}
