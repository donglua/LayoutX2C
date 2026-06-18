package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.donglua.layoutx2c.runtime.annotation.PublicApi

/**
 * 资源获取辅助类，供生成代码使用。
 * 通过 ResourceLoaderRegistry 支持自定义资源加载器。
 */
@PublicApi
object ResourceCompat {

    fun getColor(context: Context, @ColorRes colorRes: Int): Int {
        return ResourceLoaderRegistry.get().getColor(context, colorRes)
    }

    fun getColorStateList(context: Context, @ColorRes colorRes: Int): ColorStateList? {
        return ResourceLoaderRegistry.get().getColorStateList(context, colorRes)
    }

    fun getDrawable(context: Context, @DrawableRes drawableRes: Int): Drawable? {
        return ResourceLoaderRegistry.get().getDrawable(context, drawableRes)
    }

    fun getDimension(context: Context, @DimenRes dimenRes: Int): Float {
        return ResourceLoaderRegistry.get().getDimension(context, dimenRes)
    }

    fun getString(context: Context, @StringRes stringRes: Int): String {
        return ResourceLoaderRegistry.get().getString(context, stringRes)
    }
}
