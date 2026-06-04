package com.github.donglua.layoutx2c.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DemoDataBindingEnhancedX2CBinding.inflate(
            LayoutInflater.from(this), null, false
        )
        setContentView(binding.root)
        applySystemBarInsetsToContent()

        DataBindingEnhancedDemo.setup(binding, this)
    }
}

object DataBindingEnhancedDemo {

    fun setup(
        binding: DemoDataBindingEnhancedX2CBinding,
        lifecycleOwner: LifecycleOwner?
    ) {
        var clickCount = 0
        var visible = true

        binding.title = "DataBinding Enhanced Demo"
        binding.description = "Typed variables · Simple expressions · ViewModel property access"
        binding.count = clickCount
        binding.isVisible = visible
        binding.viewModel = ItemViewModel(
            name = "LayoutX2C Sample VM",
            status = "Ready",
            itemId = 1
        )
        binding.lifecycleOwner = lifecycleOwner

        binding.applyBindingsToViews()

        binding.btnUpdate.setOnClickListener {
            clickCount += 1
            binding.count = clickCount
            binding.viewModel = ItemViewModel(
                name = "LayoutX2C Sample VM",
                status = "Updated x$clickCount",
                itemId = 1
            )
            binding.applyBindingsToViews()
        }

        binding.btnToggle.setOnClickListener {
            visible = !visible
            binding.isVisible = visible
            binding.descriptionText.visibility =
                if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun DemoDataBindingEnhancedX2CBinding.applyBindingsToViews() {
        countText.text = (count ?: 0).toString()
        executePendingBindings()
    }
}
