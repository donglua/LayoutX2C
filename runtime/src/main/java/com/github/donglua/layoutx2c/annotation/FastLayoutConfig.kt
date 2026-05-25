package com.github.donglua.layoutx2c.runtime.annotation

/**
 * 标记 LayoutX2C 配置对象。
 *
 * 用法：
 * ```
 * @FastLayoutConfig
 * object LayoutX2CConfig {
 *     val layouts = intArrayOf(R.layout.activity_main)
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class FastLayoutConfig
