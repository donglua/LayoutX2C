package com.github.donglua.layoutx2c.demo

object DemoLayoutCatalog {

    data class Entry(
        val label: String,
        val layoutName: String,
        val layoutResId: Int,
        val generatedClassName: String
    )

    val entries = listOf(
        Entry("Simple", "demo_simple", R.layout.demo_simple, "Layout_DemoSimple"),
        Entry("Nested", "demo_nested", R.layout.demo_nested, "Layout_DemoNested"),
        Entry("Form", "demo_form", R.layout.demo_form, "Layout_DemoForm"),
        Entry("Relative", "demo_relative", R.layout.demo_relative, "Layout_DemoRelative"),
        Entry("Recycler", "demo_recycler", R.layout.demo_recycler, "Layout_DemoRecycler"),
        Entry("Fallback", "demo_fallback", R.layout.demo_fallback, "Layout_DemoFallback"),
    )
}
