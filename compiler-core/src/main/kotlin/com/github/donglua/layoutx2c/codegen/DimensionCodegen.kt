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

internal fun dimensionToPxFloatCode(value: String): String {
    return when {
        value == "0" || value == "0dp" || value == "0px" -> "0f"
        value.endsWith("dp") -> {
            val num = value.removeSuffix("dp")
            "(${num}f * context.resources.displayMetrics.density)"
        }
        value.endsWith("sp") -> {
            val num = value.removeSuffix("sp")
            "android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, ${num}f, context.resources.displayMetrics)"
        }
        value.endsWith("px") -> "${value.removeSuffix("px")}f"
        value.startsWith("@dimen/") -> "context.resources.getDimension(R.dimen.${value.removePrefix("@dimen/")})"
        else -> "0f"
    }
}
