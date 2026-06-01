package com.github.donglua.layoutx2c.parser

/**
 * Describes the semantic type of a layout node.
 * Regular views use [Regular]; special tags (include, merge, ViewStub) carry extra metadata.
 */
sealed class LayoutNodeType {
    object Regular : LayoutNodeType()
    data class Include(val layoutRef: String, val resolutionError: String? = null) : LayoutNodeType()
    object Merge : LayoutNodeType()
    data class ViewStub(val layoutRef: String, val resolutionError: String? = null) : LayoutNodeType()
}

internal fun LayoutNode.isLinearLayout(): Boolean {
    return tagName == "LinearLayout" || tagName == "android.widget.LinearLayout"
}

internal fun LayoutNode.isFrameLayout(): Boolean {
    return tagName == "FrameLayout" || tagName == "android.widget.FrameLayout"
}

internal fun LayoutNode.isRelativeLayout(): Boolean {
    return tagName == "RelativeLayout" || tagName == "android.widget.RelativeLayout"
}

internal fun LayoutNode.isTextView(): Boolean {
    return tagName == "TextView" || tagName == "android.widget.TextView"
}

internal fun LayoutNode.isButton(): Boolean {
    return tagName == "Button" ||
        tagName == "android.widget.Button" ||
        tagName == "androidx.appcompat.widget.AppCompatButton"
}

internal fun LayoutNode.isEditText(): Boolean {
    return tagName == "EditText" ||
        tagName == "android.widget.EditText" ||
        tagName == "androidx.appcompat.widget.AppCompatEditText"
}

internal fun LayoutNode.isTextLikeView(): Boolean {
    return isTextView() || isButton() || isEditText()
}

internal fun LayoutNode.isImageView(): Boolean {
    return tagName == "ImageView" ||
        tagName == "android.widget.ImageView" ||
        tagName == "androidx.appcompat.widget.AppCompatImageView"
}

internal fun LayoutNode.isScrollView(): Boolean {
    return tagName == "ScrollView" ||
        tagName == "android.widget.ScrollView" ||
        tagName == "HorizontalScrollView" ||
        tagName == "android.widget.HorizontalScrollView"
}

internal fun LayoutNode.isRecyclerView(): Boolean {
    return tagName == "androidx.recyclerview.widget.RecyclerView"
}

internal fun LayoutNode.isConstraintLayout(): Boolean {
    return tagName == "androidx.constraintlayout.widget.ConstraintLayout"
}
