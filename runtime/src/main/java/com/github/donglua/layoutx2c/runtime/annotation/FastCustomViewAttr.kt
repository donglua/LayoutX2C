package com.github.donglua.layoutx2c.runtime.annotation

/**
 * Declares one custom attribute that LayoutX2C may emit for a whitelisted custom View.
 */
@PublicApi
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class FastCustomViewAttr(
    val name: String,
    val kind: FastCustomViewAttrKind
)
