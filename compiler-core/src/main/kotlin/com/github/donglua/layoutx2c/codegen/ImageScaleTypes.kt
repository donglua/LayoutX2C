package com.github.donglua.layoutx2c.codegen

internal object ImageScaleTypes {
    private val values = mapOf(
        "center" to "CENTER",
        "centerCrop" to "CENTER_CROP",
        "centerInside" to "CENTER_INSIDE",
        "fitCenter" to "FIT_CENTER",
        "fitEnd" to "FIT_END",
        "fitStart" to "FIT_START",
        "fitXY" to "FIT_XY",
        "matrix" to "MATRIX"
    )

    fun supports(value: String): Boolean = value in values

    fun enumName(value: String): String? = values[value]
}
