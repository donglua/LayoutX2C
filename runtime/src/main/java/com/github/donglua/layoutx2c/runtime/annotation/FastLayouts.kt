package com.github.donglua.layoutx2c.runtime.annotation

/**
 * 标记需要编译期生成代码的 layout 列表。
 * 放在配置类或接口上，layout 名不带 .xml 后缀。
 *
 * 用法：
 * ```
 * @FastLayouts("activity_main", "fragment_home")
 * interface LayoutX2CConfig
 * ```
 */
@PublicApi
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class FastLayouts(
    vararg val layouts: String
)
