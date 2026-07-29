package com.github.donglua.layoutx2c.runtime.annotation

/**
 * Declares one-way DataBinding BindingAdapter methods that LayoutX2C may call directly.
 */
@PublicApi
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class FastBindingAdapters(
    vararg val value: FastBindingAdapter
)
