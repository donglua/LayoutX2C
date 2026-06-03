package com.github.donglua.layoutx2c.runtime.annotation

/**
 * Marks LayoutX2C APIs that are stable for the 1.0 compatibility contract.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
annotation class PublicApi
