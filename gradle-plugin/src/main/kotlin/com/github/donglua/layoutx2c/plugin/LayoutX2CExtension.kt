package com.github.donglua.layoutx2c.plugin

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
 * }
 * ```
 */
abstract class LayoutX2CExtension {

    abstract val packageName: Property<String>

    abstract val warnOnFallback: Property<Boolean>
}
