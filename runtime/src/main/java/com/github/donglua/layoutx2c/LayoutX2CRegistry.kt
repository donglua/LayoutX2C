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
    private val initializedPackages = mutableSetOf<String>()

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
    fun create(context: Context, @LayoutRes layoutId: Int, parent: ViewGroup?): View? {
        ensureGeneratedLayoutsRegistered(context)
        return factories[layoutId]?.create(context, parent)
    }

    /**
     * 是否有对应 layout 的 generated factory。
     */
    fun has(@LayoutRes layoutId: Int): Boolean {
        return factories.containsKey(layoutId)
    }

    private fun ensureGeneratedLayoutsRegistered(context: Context) {
        val packageName = context.packageName
        if (!initializedPackages.add(packageName)) {
            return
        }

        runCatching {
            val generated = Class.forName("$packageName.generated.LayoutX2CGenerated")
            val instance = generated.getField("INSTANCE").get(null)
            generated.getMethod("register").invoke(instance)
        }
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
        val view = create(context, layoutId, parent)
        if (view != null) {
            if (attachToRoot && parent != null) {
                parent.addView(view)
            }
            return view
        }
        return LayoutInflater.from(context).inflate(layoutId, parent, attachToRoot)
    }
}
