package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.resources.PermissiveResourceReferenceResolver
import com.github.donglua.layoutx2c.resources.ResourceReferenceResolver
import com.github.donglua.layoutx2c.resources.referenceCode

/**
 * Emits an integer pixel expression for XML attributes backed by Android's
 * dimension-pixel-size semantics, such as layout params, margins, and padding.
 */
internal fun dimensionToCode(
    value: String,
    resourceResolver: ResourceReferenceResolver = PermissiveResourceReferenceResolver,
    rPackageName: String = ""
): String {
    return when {
        value == "0" || value == "0dp" || value == "0px" -> "0"
        value.endsWith("dp") -> {
            val num = value.removeSuffix("dp")
            "(${num}f * density + 0.5f).toInt()"
        }
        value.endsWith("sp") -> {
            val num = value.removeSuffix("sp")
            "(${num}f * context.resources.displayMetrics.scaledDensity + 0.5f).toInt()"
        }
        value.endsWith("px") -> value.removeSuffix("px")
        value.startsWith("@dimen/") -> {
            val resName = value.removePrefix("@dimen/")
            val resCode = resourceResolver.referenceCode("dimen", resName, rPackageName) ?: return "0"
            "context.resources.getDimensionPixelSize($resCode)"
        }
        else -> "0"
    }
}

/**
 * Emits a raw pixel float expression for attributes that Android stores as
 * continuous dimensions, for example textSize or elevation.
 */
internal fun dimensionToPxFloatCode(
    value: String,
    resourceResolver: ResourceReferenceResolver = PermissiveResourceReferenceResolver,
    rPackageName: String = ""
): String {
    return when {
        value == "0" || value == "0dp" || value == "0px" -> "0f"
        value.endsWith("dp") -> {
            val num = value.removeSuffix("dp")
            "(${num}f * density)"
        }
        value.endsWith("sp") -> {
            val num = value.removeSuffix("sp")
            "android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, ${num}f, context.resources.displayMetrics)"
        }
        value.endsWith("px") -> "${value.removeSuffix("px")}f"
        value.startsWith("@dimen/") -> {
            val resName = value.removePrefix("@dimen/")
            val resCode = resourceResolver.referenceCode("dimen", resName, rPackageName) ?: return "0f"
            "context.resources.getDimension($resCode)"
        }
        else -> "0f"
    }
}

/**
 * Emits a float expression after applying dimension-pixel-size rounding.
 * This is used by setters whose parameter type is Float but whose XML behavior
 * still follows getDimensionPixelSize-style rounding.
 */
internal fun dimensionToPixelSizeFloatCode(
    value: String,
    resourceResolver: ResourceReferenceResolver = PermissiveResourceReferenceResolver,
    rPackageName: String = ""
): String {
    return when {
        value == "0" || value == "0dp" || value == "0px" -> "0f"
        value.endsWith("dp") -> {
            val num = value.removeSuffix("dp")
            "java.lang.Math.round(${num}f * density).toFloat()"
        }
        value.endsWith("sp") -> {
            val num = value.removeSuffix("sp")
            "java.lang.Math.round(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, ${num}f, context.resources.displayMetrics)).toFloat()"
        }
        value.endsWith("px") -> {
            val num = value.removeSuffix("px")
            "java.lang.Math.round(${num}f).toFloat()"
        }
        value.startsWith("@dimen/") -> {
            val resName = value.removePrefix("@dimen/")
            val resCode = resourceResolver.referenceCode("dimen", resName, rPackageName) ?: return "0f"
            "context.resources.getDimensionPixelSize($resCode).toFloat()"
        }
        else -> "(${dimensionToCode(value, resourceResolver, rPackageName)}).toFloat()"
    }
}
