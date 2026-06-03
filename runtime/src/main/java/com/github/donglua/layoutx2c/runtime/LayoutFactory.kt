package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.github.donglua.layoutx2c.runtime.annotation.PublicApi

/**
 * 编译期生成的 Layout Factory 接口。
 * 每个 opt-in 的 layout XML 会生成一个实现类。
 */
@PublicApi
interface LayoutFactory {

    /**
     * 创建 View 树。
     * @param context 创建 View 使用的 Context
     * @param parent 父容器，用于生成正确的 LayoutParams（可为 null）
     * @return 生成的根 View
     */
    fun create(context: Context, parent: ViewGroup? = null): View
}
