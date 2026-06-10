package com.github.donglua.layoutx2c.registry

import com.github.donglua.layoutx2c.parser.LayoutNode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConstraintLayoutRulesTest {

    @Test
    fun `validates constraint rule boundary values`() {
        assertThat(ConstraintLayoutRules.isSupportedAnchorValue("parent")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedAnchorValue("@id/title")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedAnchorValue("@+id/title")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedAnchorValue("@dimen/title")).isFalse()

        assertThat(ConstraintLayoutRules.parentIsConstraintLayout(null)).isFalse()
        assertThat(ConstraintLayoutRules.parentIsConstraintLayout("androidx.constraintlayout.widget.ConstraintLayout")).isTrue()
        assertThat(ConstraintLayoutRules.isGuideline("Guideline")).isTrue()
        assertThat(ConstraintLayoutRules.isGuideline("View")).isFalse()
        assertThat(ConstraintLayoutRules.isSupportedGuidelineOrientation("vertical")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedGuidelineOrientation("horizontal")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedGuidelineOrientation("diagonal")).isFalse()
    }

    @Test
    fun `validates constraint numeric and complex feature values`() {
        assertThat(ConstraintLayoutRules.isSupportedGuidelinePercent("0.0")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedGuidelinePercent("1.0")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedGuidelinePercent("1.1")).isFalse()
        assertThat(ConstraintLayoutRules.isSupportedGuidelinePercent("bad")).isFalse()

        assertThat(ConstraintLayoutRules.isSupportedPercentValue("0.5")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedPercentValue("-0.1")).isFalse()
        assertThat(ConstraintLayoutRules.isSupportedPercentValue("bad")).isFalse()
        assertThat(ConstraintLayoutRules.isSupportedWeightValue("2.5")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedWeightValue("heavy")).isFalse()
        assertThat(ConstraintLayoutRules.isSupportedChainStyle("spread")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedChainStyle("packed")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedChainStyle("chain")).isFalse()
        assertThat(ConstraintLayoutRules.isSupportedDimensionRatio("16:9")).isTrue()
        assertThat(ConstraintLayoutRules.isSupportedDimensionRatio("")).isFalse()
        assertThat(ConstraintLayoutRules.isSupportedDimensionRatio("@string/ratio")).isFalse()
        assertThat(ConstraintLayoutRules.isSupportedDimensionRatio("?attr/ratio")).isFalse()
    }

    @Test
    fun `detects helper tags and complex constraint attributes`() {
        assertThat(ConstraintLayoutRules.isHelperTag("Barrier")).isTrue()
        assertThat(ConstraintLayoutRules.isHelperTag("androidx.constraintlayout.widget.Group")).isTrue()
        assertThat(ConstraintLayoutRules.isHelperTag("TextView")).isFalse()
        assertThat(
            ConstraintLayoutRules.hasComplexConstraintAttribute(
                node(mapOf("app:layout_constraintCircle" to "@id/avatar"))
            )
        ).isTrue()
        assertThat(
            ConstraintLayoutRules.hasComplexConstraintAttribute(
                node(mapOf("app:layout_constraintStart_toStartOf" to "parent"))
            )
        ).isFalse()
    }

    private fun node(attributes: Map<String, String>): LayoutNode {
        return LayoutNode(tagName = "TextView", attributes = attributes, children = emptyList())
    }
}
