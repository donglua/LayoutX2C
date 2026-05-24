package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes

/**
 * 注册表：layoutResId -> LayoutFactory 的映射。
 * 编译期生成的代码会在 App 启动时自动注册。
 */
object LayoutX2CRegistry {

    private val factories = mutableMapOf<Int, LayoutFactory>()

    /**
     * 注册一个 layout 的 generated factory。
     */
    fun register(@LayoutRes layoutId: Int, factory: LayoutFactory) {
        factories[layoutId] = factory
    }

    /**
     * 尝试用 generated factory 创建 View。
     * 如果没有注册对应的 factory，返回 null（调用方应 fallback 到 LayoutInflater）。
     */
    fun create(@LayoutRes layoutId: Int, parent: ViewGroup?): View? {
        return factories[layoutId]?.create(parent)
    }

    /**
     * 是否有对应 layout 的 generated factory。
     */
    fun has(@LayoutRes layoutId: Int): Boolean {
        return factories.containsKey(layoutId)
    }

    /**
     * 便捷方法：优先用 generated factory，fallback 到 LayoutInflater。
     */
    fun inflate(
        context: Context,
        @LayoutRes layoutId: Int,
        parent: ViewGroup?,
        attachToRoot: Boolean = false
    ): View {
        val view = create(layoutId, parent)
        if (view != null) {
            if (attachToRoot && parent != null) {
                parent.addView(view)
            }
            return view
        }
        return LayoutInflater.from(context).inflate(layoutId, parent, attachToRoot)
    }
}
