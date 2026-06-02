package com.github.donglua.layoutx2c.demo

import com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig

@FastLayoutConfig
object LayoutX2CConfig {
    val layouts = intArrayOf(
        R.layout.demo_simple,
        R.layout.demo_nested,
        R.layout.demo_form,
        R.layout.demo_relative,
        R.layout.demo_include,
        R.layout.demo_constraint,
        R.layout.demo_recycler,
        R.layout.demo_fallback,
        R.layout.demo_data_binding,
        R.layout.demo_data_binding_enhanced,
        R.layout.demo_scroll_image,
    )
}
