package com.github.donglua.layoutx2c.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingEnhancedX2CBinding

/**
 * 增强 DataBinding Demo Activity
 *
 * 演示 LayoutX2C V2 对 DataBinding 布局的兼容性：
 * - 类型化变量：binding.title 是 String?，binding.count 是 Int?
 * - View 字段类型安全：binding.titleText 是 TextView
 * - lifecycleOwner 是 LifecycleOwner?（而不是 Any?）
 * - 简单 @{} 表达式自动绑定：android:text="@{title}" 由 executePendingBindings() 处理
 * - 属性访问 @{viewModel.name} 自动展开为 viewModel?.name ?: ""
 *
 * 布局中的 @{} 表达式由 V2 Analyzer 识别为简单表达式，仍走 fast path。
 */
class DataBindingEnhancedActivity : AppCompatActivity() {

    private lateinit var binding: DemoDataBindingEnhancedX2CBinding

    private var clickCount: Int = 0
    private var visible: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DemoDataBindingEnhancedX2CBinding.inflate(
            LayoutInflater.from(this), null, false
        )
        setContentView(binding.root)
        applySystemBarInsetsToContent()

        // V2 类型化赋值 — 编译器会检查类型
        binding.title = "DataBinding Enhanced Demo"
        binding.description = "Typed variables · Simple expressions · Fallback strategy"
        binding.count = clickCount
        binding.isVisible = visible
        binding.viewModel = ItemViewModel(
            name = "LayoutX2C Sample VM",
            status = "Ready",
            itemId = 1
        )

        // 设置 lifecycleOwner（V2 类型为 LifecycleOwner?）
        binding.lifecycleOwner = this

        applyBindingsToViews()

        binding.btnUpdate.setOnClickListener {
            clickCount += 1
            binding.count = clickCount
            binding.viewModel = ItemViewModel(
                name = "LayoutX2C Sample VM",
                status = "Updated x$clickCount",
                itemId = 1
            )
            applyBindingsToViews()
        }

        binding.btnToggle.setOnClickListener {
            visible = !visible
            binding.isVisible = visible
            binding.descriptionText.visibility =
                if (visible) View.VISIBLE else View.GONE
        }
    }

    /**
     * 将 binding 上的类型化变量同步到视图。
     * V2 生成器在 executePendingBindings() 中自动处理 @{} 绑定，
     * 不再需要手动 binding.titleText.text = binding.title。
     *
     * count 字段在 layout 里没用 @{count}（int → text 需要 toString），
     * 所以单独手动同步一次。
     */
    private fun applyBindingsToViews() {
        binding.countText.text = (binding.count ?: 0).toString()
        binding.executePendingBindings()
    }
}
