package com.github.donglua.layoutx2c.runtime.annotation

import androidx.annotation.LayoutRes

/**
 * 标记需要编译期生成代码的 layout 列表。
 * 放在 package-level 声明中。
 *
 * 用法：
 * ```
 * @FastLayouts(R.layout.activity_main, R.layout.fragment_home)
 * package com.example.app
 * ```
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class FastLayouts(
    vararg val layouts: Int
)
