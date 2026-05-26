package com.github.donglua.layoutx2c.parser

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
