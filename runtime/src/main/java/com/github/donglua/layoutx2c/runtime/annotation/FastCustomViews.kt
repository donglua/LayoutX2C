package com.github.donglua.layoutx2c.runtime.annotation

/**
 * Declares the custom View classes that LayoutX2C may generate for this config object.
 */
@PublicApi
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class FastCustomViews(
    vararg val value: FastCustomView
)
