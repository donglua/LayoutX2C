package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.analyzer.SupportLevel
import com.github.donglua.layoutx2c.parser.LayoutTree

enum class BindingFacadeStatus {
    NOT_DATA_BINDING_LAYOUT,
    BINDING_FACADE_GENERATED_FAST_PATH,
    BINDING_FACADE_GENERATED_FALLBACK_ONLY,
    BINDING_FACADE_SKIPPED_DUPLICATE_ID,
    BINDING_FACADE_SKIPPED_MALFORMED_LAYOUT
}

data class BindingFacadeEligibilityResult(
    val status: BindingFacadeStatus,
    val shouldGenerate: Boolean,
    val useFastPath: Boolean
)

object BindingFacadeEligibility {
    fun evaluate(
        tree: LayoutTree,
        analyzedRoot: AnalyzedNode,
        fieldCollector: BindingFieldCollector = BindingFieldCollector()
    ): BindingFacadeEligibilityResult {
        if (!tree.rootMetadata.isDataBindingLayout) {
            return BindingFacadeEligibilityResult(
                status = BindingFacadeStatus.NOT_DATA_BINDING_LAYOUT,
                shouldGenerate = false,
                useFastPath = false
            )
        }

        if (tree.rootMetadata.isMalformedDataBindingLayout) {
            return BindingFacadeEligibilityResult(
                status = BindingFacadeStatus.BINDING_FACADE_SKIPPED_MALFORMED_LAYOUT,
                shouldGenerate = false,
                useFastPath = false
            )
        }

        if (fieldCollector.collect(tree.root) is BindingFieldResult.DuplicateIds) {
            return BindingFacadeEligibilityResult(
                status = BindingFacadeStatus.BINDING_FACADE_SKIPPED_DUPLICATE_ID,
                shouldGenerate = false,
                useFastPath = false
            )
        }

        val useFastPath = analyzedRoot.supportLevel != SupportLevel.FALLBACK
        return BindingFacadeEligibilityResult(
            status = if (useFastPath) {
                BindingFacadeStatus.BINDING_FACADE_GENERATED_FAST_PATH
            } else {
                BindingFacadeStatus.BINDING_FACADE_GENERATED_FALLBACK_ONLY
            },
            shouldGenerate = true,
            useFastPath = useFastPath
        )
    }
}
