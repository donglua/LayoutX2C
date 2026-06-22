package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.github.donglua.layoutx2c.runtime.annotation.PublicApi

/**
 * View creation hook for generated custom views.
 *
 * This is intentionally nullable-AttributeSet friendly: generated LayoutX2C
 * code does not replay the platform XML parser, so a custom factory must be
 * able to decline creation when it requires a real AttributeSet.
 */
@PublicApi
fun interface ViewFactory {
    fun createView(context: Context, name: String, attrs: AttributeSet?): View?
}

@PublicApi
object ViewFactoryRegistry {
    @Volatile
    private var factory: ViewFactory? = null

    fun setViewFactory(factory: ViewFactory) {
        this.factory = factory
    }

    fun reset() {
        factory = null
    }

    fun get(): ViewFactory? = factory
}

@PublicApi
object ViewFactoryCompat {

    @Suppress("UNCHECKED_CAST")
    fun <T : View> createView(
        context: Context,
        name: String,
        attrs: AttributeSet?,
        defaultCreator: () -> T
    ): T {
        return (ViewFactoryRegistry.get()?.createView(context, name, attrs) ?: defaultCreator()) as T
    }
}
