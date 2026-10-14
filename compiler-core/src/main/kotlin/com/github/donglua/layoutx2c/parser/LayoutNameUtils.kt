package com.github.donglua.layoutx2c.parser

object LayoutNameUtils {
    fun toClassName(layoutName: String): String = 
        "Layout_" + layoutName.split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        }

    fun toFacadeName(layoutName: String): String = 
        layoutName.split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        } + "X2C"
}
