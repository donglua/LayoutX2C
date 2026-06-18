package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.github.donglua.layoutx2c.runtime.annotation.PublicApi

/**
 * 资源加载接口，用于支持换肤等场景。
 * 默认使用 Android 标准 API，可在运行时替换为自定义实现。
 */
@PublicApi
interface ResourceLoader {
    @ColorInt
    fun getColor(context: Context, @ColorRes colorRes: Int): Int

    fun getColorStateList(context: Context, @ColorRes colorRes: Int): ColorStateList?

    fun getDrawable(context: Context, @DrawableRes drawableRes: Int): Drawable?

    fun getDimension(context: Context, @DimenRes dimenRes: Int): Float

    fun getString(context: Context, @StringRes stringRes: Int): String
}

/**
 * 默认实现：直接使用 Android 原生 API
 */
internal object DefaultResourceLoader : ResourceLoader {
    override fun getColor(context: Context, colorRes: Int): Int {
        return ContextCompat.getColor(context, colorRes)
    }

    override fun getColorStateList(context: Context, colorRes: Int): ColorStateList? {
        return ContextCompat.getColorStateList(context, colorRes)
    }

    override fun getDrawable(context: Context, drawableRes: Int): Drawable? {
        return ContextCompat.getDrawable(context, drawableRes)
    }

    override fun getDimension(context: Context, dimenRes: Int): Float {
        return context.resources.getDimension(dimenRes)
    }

    override fun getString(context: Context, stringRes: Int): String {
        return context.getString(stringRes)
    }
}

/**
 * 全局资源加载器管理
 */
@PublicApi
object ResourceLoaderRegistry {
    @Volatile
    private var loader: ResourceLoader = DefaultResourceLoader

    /**
     * 设置自定义资源加载器
     */
    fun setResourceLoader(loader: ResourceLoader) {
        this.loader = loader
    }

    /**
     * 重置为默认加载器
     */
    fun reset() {
        this.loader = DefaultResourceLoader
    }

    /**
     * 获取当前资源加载器
     */
    fun get(): ResourceLoader = loader
}
