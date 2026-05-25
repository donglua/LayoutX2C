package com.github.donglua.layoutx2c.demo

import com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig

@FastLayoutConfig
object LayoutX2CConfig {
    val layouts = intArrayOf(
        R.layout.activity_main,
        R.layout.activity_simple,
        R.layout.activity_nested
    )
}
