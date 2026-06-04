package com.github.donglua.layoutx2c.runtime.annotation

import kotlin.reflect.KClass

/**
 * Describes one custom View class and the attributes that may be generated for it.
 */
@PublicApi
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class FastCustomView(
    val viewClass: KClass<*>,
    val attrs: Array<FastCustomViewAttr> = []
)
