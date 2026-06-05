package com.github.donglua.layoutx2c.demo

object CodeViewerSelector {

    data class Entry(
        val buttonId: Int,
        val layoutName: String,
    )

    val entries = listOf(
        Entry(R.id.btn_demo_simple, "demo_simple"),
        Entry(R.id.btn_demo_nested, "demo_nested"),
        Entry(R.id.btn_demo_form, "demo_form"),
        Entry(R.id.btn_demo_relative, "demo_relative"),
        Entry(R.id.btn_demo_include, "demo_include"),
        Entry(R.id.btn_demo_constraint, "demo_constraint"),
        Entry(R.id.btn_demo_recycler, "demo_recycler"),
        Entry(R.id.btn_demo_fallback, "demo_fallback"),
        Entry(R.id.btn_demo_binding, "demo_data_binding"),
        Entry(R.id.btn_demo_binding_enhanced, "demo_data_binding_enhanced"),
        Entry(R.id.btn_demo_binding_include_child, "demo_data_binding_include_child"),
        Entry(R.id.btn_demo_binding_include_parent, "demo_data_binding_include_parent"),
        Entry(R.id.btn_demo_scroll_image, "demo_scroll_image"),
        Entry(R.id.btn_demo_two_way_binding, "demo_two_way_binding"),
        Entry(R.id.btn_demo_compat_widgets, "demo_compat_widgets"),
        Entry(R.id.btn_demo_partial_fallback, "demo_partial_fallback_parser_crash"),
    )
}
