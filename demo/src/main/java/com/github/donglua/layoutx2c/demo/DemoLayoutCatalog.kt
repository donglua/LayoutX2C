package com.github.donglua.layoutx2c.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.donglua.layoutx2c.demo.generated.DemoConstraintX2C
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingEnhancedX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoFallbackX2C
import com.github.donglua.layoutx2c.demo.generated.DemoFormX2C
import com.github.donglua.layoutx2c.demo.generated.DemoIncludeX2C
import com.github.donglua.layoutx2c.demo.generated.DemoNestedX2C
import com.github.donglua.layoutx2c.demo.generated.DemoRecyclerX2C
import com.github.donglua.layoutx2c.demo.generated.DemoRelativeX2C
import com.github.donglua.layoutx2c.demo.generated.DemoSimpleX2C

object DemoLayoutCatalog {

    data class Entry(
        val label: String,
        val layoutName: String,
        val layoutResId: Int,
        val generatedClassName: String,
        val codeViewerClassName: String = generatedClassName,
        val platformInflatable: Boolean = true,
        val generatedInflater: (Context, ViewGroup?) -> View
    )

    val entries = listOf(
        Entry("Simple", "demo_simple", R.layout.demo_simple, "Layout_DemoSimple") { context, parent ->
            DemoSimpleX2C.inflate(context, parent)
        },
        Entry("Nested", "demo_nested", R.layout.demo_nested, "Layout_DemoNested") { context, parent ->
            DemoNestedX2C.inflate(context, parent)
        },
        Entry("Form", "demo_form", R.layout.demo_form, "Layout_DemoForm") { context, parent ->
            DemoFormX2C.inflate(context, parent)
        },
        Entry("Relative", "demo_relative", R.layout.demo_relative, "Layout_DemoRelative") { context, parent ->
            DemoRelativeX2C.inflate(context, parent)
        },
        Entry("Include", "demo_include", R.layout.demo_include, "Layout_DemoInclude") { context, parent ->
            DemoIncludeX2C.inflate(context, parent)
        },
        Entry("Constraint", "demo_constraint", R.layout.demo_constraint, "Layout_DemoConstraint") { context, parent ->
            DemoConstraintX2C.inflate(context, parent)
        },
        Entry("Recycler", "demo_recycler", R.layout.demo_recycler, "Layout_DemoRecycler") { context, parent ->
            DemoRecyclerX2C.inflate(context, parent)
        },
        Entry("Fallback", "demo_fallback", R.layout.demo_fallback, "Layout_DemoFallback") { context, parent ->
            DemoFallbackX2C.inflate(context, parent)
        },
        Entry(
            "Binding",
            "demo_data_binding",
            R.layout.demo_data_binding,
            "Layout_DemoDataBinding",
            codeViewerClassName = "DemoDataBindingX2CBinding"
        ) { context, parent ->
            DemoDataBindingX2CBinding.inflate(LayoutInflater.from(context), parent, false).root
        },
        Entry(
            "Binding Enhanced",
            "demo_data_binding_enhanced",
            R.layout.demo_data_binding_enhanced,
            "Layout_DemoDataBindingEnhanced",
            codeViewerClassName = "DemoDataBindingEnhancedX2CBinding"
        ) { context, parent ->
            DemoDataBindingEnhancedX2CBinding.inflate(LayoutInflater.from(context), parent, false).root
        },
    )
}
