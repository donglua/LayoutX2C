package com.github.donglua.layoutx2c.runtime.annotation

/**
 * 按前缀匹配 layout 文件，自动生成代码。
 * 类似 Epoxy 的 @EpoxyDataBindingPattern。
 *
 * 用法：
 * ```
 * @FastLayoutPattern(rClass = R::class, layoutPrefix = "activity_")
 * package com.example.app
 * ```
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class FastLayoutPattern(
    val rClass: kotlin.reflect.KClass<*>,
    val layoutPrefix: String
)
