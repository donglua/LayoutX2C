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
 * - executePendingBindings() 可调用
 *
 * 布局中没有 @{} 表达式，走 fast path 代码生成。
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
     * V2 生成器让变量有实际类型，不再需要 cast。
     */
    private fun applyBindingsToViews() {
        // 直接访问，无需 cast — title 是 String?
        binding.titleText.text = binding.title ?: ""
        binding.descriptionText.text = binding.description ?: ""
        binding.countText.text = (binding.count ?: 0).toString()

        // viewModel 是 ItemViewModel?，可以直接访问属性
        binding.vmNameText.text = binding.viewModel?.name ?: ""
        binding.vmStatusText.text = binding.viewModel?.status ?: ""

        binding.executePendingBindings()
    }
}
