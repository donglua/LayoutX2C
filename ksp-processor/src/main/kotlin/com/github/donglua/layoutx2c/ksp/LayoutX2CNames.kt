package com.github.donglua.layoutx2c.ksp

internal object LayoutX2CNames {

    fun factoryClassName(layoutName: String): String {
        return "Layout_" + layoutName.split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        }
    }

    fun facadeClassName(layoutName: String): String {
        return layoutName.split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        } + "X2C"
    }

    fun bindingFacadeClassName(layoutName: String): String {
        return layoutName.split("_").joinToString("") {
            it.replaceFirstChar { char -> char.uppercaseChar() }
        } + "X2CBinding"
    }
}
