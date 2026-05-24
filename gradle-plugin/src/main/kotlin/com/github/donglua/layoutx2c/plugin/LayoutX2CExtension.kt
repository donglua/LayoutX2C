package com.github.donglua.layoutx2c.plugin

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * LayoutX2C 配置 DSL。
 *
 * ```kotlin
 * layoutX2C {
 *     // 显式列出要处理的 layout 文件名（不带 .xml 后缀）
 *     layouts("activity_main", "fragment_home")
 *
 *     // 或按前缀匹配
 *     prefixes("activity_", "fragment_")
 *
 *     // 生成代码的目标包名
 *     packageName.set("com.example.app.layoutx2c")
 *
 *     // 是否对不支持的节点产生警告
 *     warnOnFallback.set(true)
 * }
 * ```
 */
abstract class LayoutX2CExtension {

    abstract val layouts: ListProperty<String>

    abstract val prefixes: ListProperty<String>

    abstract val packageName: Property<String>

    abstract val warnOnFallback: Property<Boolean>

    fun layouts(vararg names: String) {
        layouts.addAll(names.toList())
    }

    fun prefixes(vararg values: String) {
        prefixes.addAll(values.toList())
    }
}
