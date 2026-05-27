package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View

/**
 * LayoutInflater.Factory2 实现，拦截 setContentView / inflate 调用，
 * 优先使用 generated factory。
 */
class LayoutX2CFactory2(
    private val delegate: LayoutInflater.Factory2?
) : LayoutInflater.Factory2 {

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        // Factory2 拦截的是单个 View 创建，不是整个 layout。
        // LayoutX2C 的拦截点在 inflate() 层面（通过 Registry），
        // 这里只做 delegate 透传。
        return delegate?.onCreateView(parent, name, context, attrs)
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return delegate?.onCreateView(name, context, attrs)
    }
}
