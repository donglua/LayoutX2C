package com.github.donglua.layoutx2c.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.github.donglua.layoutx2c.demo.generated.DemoConstraintGuidelineX2C
import com.github.donglua.layoutx2c.demo.generated.DemoConstraintX2C
import com.github.donglua.layoutx2c.demo.generated.DemoCompatWidgetsX2C
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingIncludeChildX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingIncludeParentX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingEnhancedX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoTwoWayBindingX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoFallbackX2C
import com.github.donglua.layoutx2c.demo.generated.DemoFormX2C
import com.github.donglua.layoutx2c.demo.generated.DemoIncludeX2C
import com.github.donglua.layoutx2c.demo.generated.DemoNestedX2C
import com.github.donglua.layoutx2c.demo.generated.DemoPartialFallbackParserCrashX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoRecyclerX2C
import com.github.donglua.layoutx2c.demo.generated.DemoRelativeX2C
import com.github.donglua.layoutx2c.demo.generated.DemoScrollImageX2C
import com.github.donglua.layoutx2c.demo.generated.DemoSimpleX2C

object DemoLayoutCatalog {

    enum class Status {
        Generated,
        Binding,
        Fallback,
    }

    enum class PreviewMode {
        DisplayOnly,
        Interactive,
    }

    data class Entry(
        val label: String,
        val layoutName: String,
        val layoutResId: Int,
        val generatedClassName: String,
        val summary: String,
        val status: Status,
        val codeViewerClassName: String = generatedClassName,
        val platformInflatable: Boolean = true,
        val previewMode: PreviewMode = PreviewMode.DisplayOnly,
        val configurePreview: (Context, View) -> Unit = { _, _ -> },
        val generatedInflater: (Context, ViewGroup?) -> View
    )

    val entries = listOf(
        Entry(
            "Simple",
            "demo_simple",
            R.layout.demo_simple,
            "Layout_DemoSimple",
            summary = "LinearLayout and TextView fast path",
            status = Status.Generated
        ) { context, parent ->
            DemoSimpleX2C.inflate(context, parent)
        },
        Entry(
            "Nested",
            "demo_nested",
            R.layout.demo_nested,
            "Layout_DemoNested",
            summary = "Nested LinearLayout, FrameLayout, and parent-specific LayoutParams",
            status = Status.Generated
        ) { context, parent ->
            DemoNestedX2C.inflate(context, parent)
        },
        Entry(
            "Form",
            "demo_form",
            R.layout.demo_form,
            "Layout_DemoForm",
            summary = "EditText hints, inputType, Button, and text attributes",
            status = Status.Generated
        ) { context, parent ->
            DemoFormX2C.inflate(context, parent)
        },
        Entry(
            "Relative",
            "demo_relative",
            R.layout.demo_relative,
            "Layout_DemoRelative",
            summary = "RelativeLayout sibling and parent rules",
            status = Status.Generated
        ) { context, parent ->
            DemoRelativeX2C.inflate(context, parent)
        },
        Entry(
            "Include",
            "demo_include",
            R.layout.demo_include,
            "Layout_DemoInclude",
            summary = "include, merge, and ViewStub semantics",
            status = Status.Generated
        ) { context, parent ->
            DemoIncludeX2C.inflate(context, parent)
        },
        Entry(
            "Constraint",
            "demo_constraint",
            R.layout.demo_constraint,
            "Layout_DemoConstraint",
            summary = "ConstraintLayout anchors, match constraints, and bias",
            status = Status.Generated
        ) { context, parent ->
            DemoConstraintX2C.inflate(context, parent)
        },
        Entry(
            "Constraint Guideline",
            "demo_constraint_guideline",
            R.layout.demo_constraint_guideline,
            "Layout_DemoConstraintGuideline",
            summary = "ConstraintLayout guidelines with begin, end, and percent positioning",
            status = Status.Generated
        ) { context, parent ->
            DemoConstraintGuidelineX2C.inflate(context, parent)
        },
        Entry(
            "Recycler",
            "demo_recycler",
            R.layout.demo_recycler,
            "Layout_DemoRecycler",
            summary = "RecyclerView container creation and metadata handling",
            status = Status.Generated
        ) { context, parent ->
            DemoRecyclerX2C.inflate(context, parent)
        },
        Entry(
            "Fallback",
            "demo_fallback",
            R.layout.demo_fallback,
            "Layout_DemoFallback",
            summary = "Unsupported theme attribute keeps runtime LayoutInflater semantics",
            status = Status.Fallback
        ) { context, parent ->
            DemoFallbackX2C.inflate(context, parent)
        },
        Entry(
            "Binding",
            "demo_data_binding",
            R.layout.demo_data_binding,
            "Layout_DemoDataBinding",
            summary = "DataBinding layout wrapper with generated binding facade",
            status = Status.Binding,
            codeViewerClassName = "DemoDataBindingX2CBinding"
        ) { context, parent ->
            DemoDataBindingX2CBinding.inflate(LayoutInflater.from(context), parent, false).root
        },
        Entry(
            "Binding Enhanced",
            "demo_data_binding_enhanced",
            R.layout.demo_data_binding_enhanced,
            "Layout_DemoDataBindingEnhanced",
            summary = "Typed variables and simple DataBinding expression execution",
            status = Status.Binding,
            codeViewerClassName = "DemoDataBindingEnhancedX2CBinding",
            previewMode = PreviewMode.Interactive,
            configurePreview = { context, preview ->
                val binding = DataBindingUtil.getBinding<DemoDataBindingEnhancedX2CBinding>(preview)
                    ?: DemoDataBindingEnhancedX2CBinding.bind(preview)
                DataBindingEnhancedDemo.setup(binding, context as? androidx.lifecycle.LifecycleOwner)
            }
        ) { context, parent ->
            DemoDataBindingEnhancedX2CBinding.inflate(LayoutInflater.from(context), parent, false).root
        },
        Entry(
            "Binding Include Child",
            "demo_data_binding_include_child",
            R.layout.demo_data_binding_include_child,
            "Layout_DemoDataBindingIncludeChild",
            summary = "DataBinding include child with typed variables",
            status = Status.Binding,
            codeViewerClassName = "DemoDataBindingIncludeChildX2CBinding"
        ) { context, parent ->
            DemoDataBindingIncludeChildX2CBinding.inflate(LayoutInflater.from(context), parent, false).root
        },
        Entry(
            "Binding Include Parent",
            "demo_data_binding_include_parent",
            R.layout.demo_data_binding_include_parent,
            "Layout_DemoDataBindingIncludeParent",
            summary = "DataBinding include variables propagated into X2C child binding subclasses",
            status = Status.Binding,
            codeViewerClassName = "DemoDataBindingIncludeParentX2CBinding",
            previewMode = PreviewMode.Interactive,
            configurePreview = { _, preview ->
                val binding = DataBindingUtil.getBinding<DemoDataBindingIncludeParentX2CBinding>(preview)
                    ?: DemoDataBindingIncludeParentX2CBinding.bind(preview)
                binding.dynamicText = "动态"
                binding.executePendingBindings()
            }
        ) { context, parent ->
            DemoDataBindingIncludeParentX2CBinding.inflate(LayoutInflater.from(context), parent, false).root
        },
        Entry(
            "Scroll + Image",
            "demo_scroll_image",
            R.layout.demo_scroll_image,
            "Layout_DemoScrollImage",
            summary = "ScrollView fillViewport plus ImageView src, tint, and scaleType",
            status = Status.Generated
        ) { context, parent ->
            DemoScrollImageX2C.inflate(context, parent)
        },
        Entry(
            "Two-Way Binding",
            "demo_two_way_binding",
            R.layout.demo_two_way_binding,
            "Layout_DemoTwoWayBinding",
            summary = "Two-way binding (@={}) for EditText and CompoundButton",
            status = Status.Binding,
            codeViewerClassName = "DemoTwoWayBindingX2CBinding"
        ) { context, parent ->
            DemoTwoWayBindingX2CBinding.inflate(LayoutInflater.from(context), parent, false).root
        },
        Entry(
            "Compat Widgets",
            "demo_compat_widgets",
            R.layout.demo_compat_widgets,
            "Layout_DemoCompatWidgets",
            summary = "Expanded text, widget, ConstraintLayout, card, toolbar, and pager support",
            status = Status.Generated
        ) { context, parent ->
            DemoCompatWidgetsX2C.inflate(context, parent)
        },
        Entry(
            "Partial Fallback",
            "demo_partial_fallback_parser_crash",
            R.layout.demo_partial_fallback_parser_crash,
            "Layout_DemoPartialFallbackParserCrash",
            summary = "DataBinding ConstraintLayout with direct fallback children",
            status = Status.Binding,
            codeViewerClassName = "DemoPartialFallbackParserCrashX2CBinding"
        ) { context, parent ->
            DemoPartialFallbackParserCrashX2CBinding.inflate(LayoutInflater.from(context), parent, false).root
        },
    )

    fun requireByLayoutName(layoutName: String): Entry {
        return entries.firstOrNull { it.layoutName == layoutName }
            ?: error("Unknown demo layout: $layoutName")
    }
}
