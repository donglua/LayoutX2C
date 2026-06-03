package com.github.donglua.layoutx2c.runtime.annotation

/**
 * Marks LayoutX2C APIs that are available for early use but may change.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
annotation class ExperimentalApi
