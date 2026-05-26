package com.github.donglua.layoutx2c.parser

internal fun LayoutNode.isLinearLayout(): Boolean {
    return tagName == "LinearLayout" || tagName == "android.widget.LinearLayout"
}

internal fun LayoutNode.isFrameLayout(): Boolean {
    return tagName == "FrameLayout" || tagName == "android.widget.FrameLayout"
}

internal fun LayoutNode.isTextView(): Boolean {
    return tagName == "TextView" || tagName == "android.widget.TextView"
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
