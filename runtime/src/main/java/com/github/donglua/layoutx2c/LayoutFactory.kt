package com.github.donglua.layoutx2c.runtime

import android.view.View
import android.view.ViewGroup

/**
 * 编译期生成的 Layout Factory 接口。
 * 每个 opt-in 的 layout XML 会生成一个实现类。
 */
interface LayoutFactory {

    /**
     * 创建 View 树。
     * @param parent 父容器，用于生成正确的 LayoutParams（可为 null）
     * @return 生成的根 View
     */
    fun create(parent: ViewGroup?): View
}
