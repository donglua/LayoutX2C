package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.github.donglua.layoutx2c.runtime.annotation.PublicApi

/**
 * 注册表：layoutResId -> LayoutFactory 的映射。
 * 编译期生成的代码会在 App 启动时自动注册。
 */
@PublicApi
object LayoutX2CRegistry {

    private val factories = mutableMapOf<Int, LayoutFactory>()
    private val initializedPackages = mutableSetOf<String>()
    private val failedPackages = mutableSetOf<String>()

    /**
     * 负缓存：记录已知没有 generated factory 的 layoutId。
     * 避免对 fallback layout 重复查询 Map 和触发 ensureGeneratedLayoutsRegistered。
     */
    private val knownMissingLayouts = mutableSetOf<Int>()

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
        // 快速路径：已知没有 factory 的 layout 直接返回 null
        if (layoutId in knownMissingLayouts) {
            return null
        }

        ensureGeneratedLayoutsRegistered(context)
        val factory = factories[layoutId]

        // 记录负缓存：避免下次重复查询
        if (factory == null) {
            knownMissingLayouts.add(layoutId)
        }

        return factory?.create(context, parent)
    }

    /**
     * 是否有对应 layout 的 generated factory。
     */
    fun has(@LayoutRes layoutId: Int): Boolean {
        return factories.containsKey(layoutId)
    }

    /**
     * 是否有对应 layout 的 generated factory，并在查询前尝试加载生成注册表。
     */
    fun has(context: Context, @LayoutRes layoutId: Int): Boolean {
        ensureGeneratedLayoutsRegistered(context)
        return has(layoutId)
    }

    /**
     * 主动加载当前应用包名下的 generated registry。
     */
    fun initialize(context: Context): Boolean {
        return ensureGeneratedLayoutsRegistered(context)
    }

    private fun ensureGeneratedLayoutsRegistered(context: Context): Boolean {
        val packageName = context.packageName
        if (packageName in initializedPackages) {
            return true
        }
        if (packageName in failedPackages) {
            return false
        }

        return try {
            val generated = Class.forName("$packageName.generated.LayoutX2CGenerated")
            val instance = generated.getField("INSTANCE").get(null)
            generated.getMethod("register").invoke(instance)
            initializedPackages.add(packageName)
            true
        } catch (_: ClassNotFoundException) {
            failedPackages.add(packageName)
            false
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: LinkageError) {
            false
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
