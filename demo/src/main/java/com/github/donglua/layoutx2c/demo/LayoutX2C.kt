package com.github.donglua.layoutx2c.demo

import com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig
import com.github.donglua.layoutx2c.runtime.annotation.FastCustomView
import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViewAttr
import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViewAttrKind
import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViews

@FastLayoutConfig
@FastCustomViews(
    FastCustomView(
        viewClass = com.github.donglua.layoutx2c.demo.SyntheticAttrsPriceView::class,
        attrs = [
            FastCustomViewAttr(name = "app:priceColor", kind = FastCustomViewAttrKind.COLOR),
            FastCustomViewAttr(name = "app:priceFormat", kind = FastCustomViewAttrKind.STRING)
        ]
    )
)
object LayoutX2CConfig {
    val layouts = intArrayOf(
        R.layout.demo_simple,
        R.layout.demo_nested,
        R.layout.demo_form,
        R.layout.demo_relative,
        R.layout.demo_include,
        R.layout.demo_constraint,
        R.layout.demo_constraint_guideline,
        R.layout.demo_recycler,
        R.layout.demo_fallback,
        R.layout.demo_data_binding,
        R.layout.demo_data_binding_enhanced,
        R.layout.demo_data_binding_include_child,
        R.layout.demo_data_binding_include_parent,
        R.layout.demo_scroll_image,
        R.layout.demo_two_way_binding,
        R.layout.demo_compat_widgets,
        R.layout.demo_partial_fallback_parser_crash,
        R.layout.demo_synthetic_attrs,
    )
}
