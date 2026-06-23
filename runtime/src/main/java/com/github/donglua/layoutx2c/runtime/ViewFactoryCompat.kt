package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.github.donglua.layoutx2c.runtime.annotation.PublicApi

/**
 * View creation hook for generated custom views.
 *
 * attrs may be a LayoutX2C SyntheticAttributeSet when generated metadata is
 * available, or null when the call site has no XML attributes to replay.
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
