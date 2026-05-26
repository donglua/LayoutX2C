package com.github.donglua.layoutx2c.codegen

internal fun gravityToCode(value: String): String {
    val parts = value.split("|")
    return parts.joinToString(" or ") { part ->
        when (part.trim()) {
            "center" -> "android.view.Gravity.CENTER"
            "center_horizontal" -> "android.view.Gravity.CENTER_HORIZONTAL"
            "center_vertical" -> "android.view.Gravity.CENTER_VERTICAL"
            "top" -> "android.view.Gravity.TOP"
            "bottom" -> "android.view.Gravity.BOTTOM"
            "left", "start" -> "android.view.Gravity.START"
            "right", "end" -> "android.view.Gravity.END"
            else -> "android.view.Gravity.NO_GRAVITY"
        }
    }
}
