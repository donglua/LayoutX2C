package com.github.donglua.layoutx2c.demo

/**
 * Demo ViewModel 用于展示 DataBinding 增强功能
 */
data class ItemViewModel(
    val name: String,
    val status: String,
    val itemId: Int = 0
) {
    override fun toString(): String {
        return "ItemViewModel(name='$name', status='$status', itemId=$itemId)"
    }
}
