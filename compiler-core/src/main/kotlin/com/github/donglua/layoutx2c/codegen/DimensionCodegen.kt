package com.github.donglua.layoutx2c.codegen

internal fun dimensionToCode(value: String): String {
    return when {
        value == "0" || value == "0dp" || value == "0px" -> "0"
        value.endsWith("dp") -> {
            val num = value.removeSuffix("dp")
            "(${num}f * context.resources.displayMetrics.density + 0.5f).toInt()"
        }
        value.endsWith("sp") -> {
            val num = value.removeSuffix("sp")
            "(${num}f * context.resources.displayMetrics.scaledDensity + 0.5f).toInt()"
        }
        value.endsWith("px") -> value.removeSuffix("px")
        else -> "0"
    }
}
