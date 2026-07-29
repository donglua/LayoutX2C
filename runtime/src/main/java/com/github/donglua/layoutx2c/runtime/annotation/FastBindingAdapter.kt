package com.github.donglua.layoutx2c.runtime.annotation

import kotlin.reflect.KClass

/**
 * Declares one one-way DataBinding BindingAdapter method that LayoutX2C may call directly.
 * Two-way expressions require an inverse adapter contract and are not generated.
 */
@PublicApi
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class FastBindingAdapter(
    val attrs: Array<String>,
    val methodClass: KClass<*>,
    val methodName: String,
    val requireAll: Boolean = true
)
