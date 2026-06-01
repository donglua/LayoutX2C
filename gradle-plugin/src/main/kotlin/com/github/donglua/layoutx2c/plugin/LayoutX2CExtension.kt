package com.github.donglua.layoutx2c.plugin

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * LayoutX2C 配置 DSL。
 *
 * ```kotlin
 * layoutX2C {
 *     // 生成代码的目标包名
 *     packageName.set("com.example.app.layoutx2c")
 *
 *     // 是否对不支持的节点产生警告
 *     warnOnFallback.set(true)
 *
 *     // CI 中允许的 fallback layout 数量，超过则让 layoutX2CReport 失败
 *     maxFallbackLayouts.set(0)
 *
 *     // CI 中不允许出现的 fallback 原因
 *     failOnFallbackReasons.add("DATA_BINDING_WRAPPER")
 * }
 * ```
 */
abstract class LayoutX2CExtension {

    abstract val packageName: Property<String>

    abstract val warnOnFallback: Property<Boolean>

    abstract val maxFallbackLayouts: Property<Int>

    abstract val failOnFallbackReasons: ListProperty<String>
}
