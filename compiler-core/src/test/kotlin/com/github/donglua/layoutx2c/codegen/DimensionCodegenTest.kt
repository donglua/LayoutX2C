package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.resources.ResourceReference
import com.github.donglua.layoutx2c.resources.ResourceSymbolTable
import com.github.donglua.layoutx2c.resources.StaticResourceReferenceResolver
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DimensionCodegenTest {

    private val resolver = StaticResourceReferenceResolver.currentModule(
        currentPackageName = "com.example",
        symbols = ResourceSymbolTable(setOf(ResourceReference("dimen", "gap")))
    )

    @Test
    fun `dimension pixel size expressions cover literals resources and fallbacks`() {
        assertThat(dimensionToCode("0")).isEqualTo("0")
        assertThat(dimensionToCode("0dp")).isEqualTo("0")
        assertThat(dimensionToCode("8dp")).isEqualTo("(8f * density + 0.5f).toInt()")
        assertThat(dimensionToCode("14sp")).isEqualTo("(14f * context.resources.displayMetrics.scaledDensity + 0.5f).toInt()")
        assertThat(dimensionToCode("3px")).isEqualTo("3")
        assertThat(dimensionToCode("@dimen/gap", resolver, "com.example"))
            .isEqualTo("context.resources.getDimensionPixelSize(R.dimen.gap)")
        assertThat(dimensionToCode("@dimen/missing", resolver, "com.example")).isEqualTo("0")
        assertThat(dimensionToCode("bad")).isEqualTo("0")
    }

    @Test
    fun `dimension pixel offset expressions cover literals resources and fallbacks`() {
        assertThat(dimensionToPixelOffsetCode("0px")).isEqualTo("0")
        assertThat(dimensionToPixelOffsetCode("8dp")).isEqualTo("(8f * density).toInt()")
        assertThat(dimensionToPixelOffsetCode("14sp"))
            .isEqualTo("android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics).toInt()")
        assertThat(dimensionToPixelOffsetCode("3px")).isEqualTo("(3f).toInt()")
        assertThat(dimensionToPixelOffsetCode("@dimen/gap", resolver, "com.example"))
            .isEqualTo("context.resources.getDimensionPixelOffset(R.dimen.gap)")
        assertThat(dimensionToPixelOffsetCode("@dimen/missing", resolver, "com.example")).isEqualTo("0")
        assertThat(dimensionToPixelOffsetCode("bad")).isEqualTo("0")
    }

    @Test
    fun `dimension float expressions cover literals resources and fallbacks`() {
        assertThat(dimensionToPxFloatCode("0dp")).isEqualTo("0f")
        assertThat(dimensionToPxFloatCode("8dp")).isEqualTo("(8f * density)")
        assertThat(dimensionToPxFloatCode("14sp"))
            .isEqualTo("android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics)")
        assertThat(dimensionToPxFloatCode("3px")).isEqualTo("3f")
        assertThat(dimensionToPxFloatCode("@dimen/gap", resolver, "com.example"))
            .isEqualTo("context.resources.getDimension(R.dimen.gap)")
        assertThat(dimensionToPxFloatCode("@dimen/missing", resolver, "com.example")).isEqualTo("0f")
        assertThat(dimensionToPxFloatCode("bad")).isEqualTo("0f")
    }

    @Test
    fun `dimension pixel size float expressions cover literals resources and fallbacks`() {
        assertThat(dimensionToPixelSizeFloatCode("0")).isEqualTo("0f")
        assertThat(dimensionToPixelSizeFloatCode("8dp")).isEqualTo("java.lang.Math.round(8f * density).toFloat()")
        assertThat(dimensionToPixelSizeFloatCode("14sp"))
            .isEqualTo("java.lang.Math.round(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics)).toFloat()")
        assertThat(dimensionToPixelSizeFloatCode("3px")).isEqualTo("java.lang.Math.round(3f).toFloat()")
        assertThat(dimensionToPixelSizeFloatCode("@dimen/gap", resolver, "com.example"))
            .isEqualTo("context.resources.getDimensionPixelSize(R.dimen.gap).toFloat()")
        assertThat(dimensionToPixelSizeFloatCode("@dimen/missing", resolver, "com.example")).isEqualTo("0f")
        assertThat(dimensionToPixelSizeFloatCode("bad")).isEqualTo("(0).toFloat()")
    }
}
