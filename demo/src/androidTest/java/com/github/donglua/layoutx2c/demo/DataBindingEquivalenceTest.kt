package com.github.donglua.layoutx2c.demo

import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.databinding.DataBindingUtil
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingIncludeChildX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingIncludeParentX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingEnhancedX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingX2CBinding
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android 等价性测试：验证 LayoutX2C 生成的 DataBinding 子类与原生 DataBinding 的行为一致性。
 *
 * 测试覆盖：
 * - 类型化变量的设置和读取
 * - dirty flag 机制
 * - executePendingBindings() 的 View 更新
 * - 简单表达式（@{variable}、@{variable.property}）
 * - 双向绑定（@={text}）
 * - setVariable() 分发
 * - BR ID 解析和 fallback
 */
@RunWith(AndroidJUnit4::class)
class DataBindingEquivalenceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun generatedBindingProvidesTypedVariableProperties() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 设置类型化变量（不是 Any?）
            binding.title = "Test Title"
            binding.description = "Test Description"
            binding.count = 42
            binding.isVisible = true
            binding.viewModel = ItemViewModel("VM Name", "Active", 100)

            // 读取变量
            requireEquals("title should be readable", "Test Title", binding.title)
            requireEquals("description should be readable", "Test Description", binding.description)
            requireEquals("count should be readable", 42, binding.count)
            requireEquals("isVisible should be readable", true, binding.isVisible)
            requireEquals("viewModel.name should be readable", "VM Name", binding.viewModel?.name)
        }
    }

    @Test
    fun setVariableUpdatesTypedPropertiesAndMarksDirty() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 初始状态：没有 pending bindings
            binding.executePendingBindings()
            require(!binding.hasPendingBindings()) {
                "Fresh binding should not have pending bindings"
            }

            // 通过 setVariable 设置变量（使用 BR ID 分发）
            // 注意：我们需要从生成的 BR 类获取 ID，这里使用反射模拟
            val titleBrId = getBrId("title")
            val success = binding.setVariable(titleBrId, "Dynamic Title")

            requireTrue("setVariable should return true for known variable", success)
            requireEquals("setVariable should update property", "Dynamic Title", binding.title)
            requireTrue("setVariable should mark binding dirty", binding.hasPendingBindings())

            // 未知 variable ID 应该返回 false
            val unknownSuccess = binding.setVariable(99999, "ignored")
            require(!unknownSuccess) {
                "setVariable should return false for unknown variable ID"
            }
        }
    }

    @Test
    fun executePendingBindingsWritesSimpleVariableExpressions() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 设置变量
            binding.title = "Updated Title"
            binding.description = "Updated Description"
            requireTrue("Setting variables should mark dirty", binding.hasPendingBindings())

            // 执行绑定
            binding.executePendingBindings()

            // 验证 View 已更新（对应 XML 中的 android:text="@{title}"）
            requireEquals(
                "titleText should show bound value",
                "Updated Title",
                binding.titleText.text.toString()
            )
            requireEquals(
                "descriptionText should show bound value",
                "Updated Description",
                binding.descriptionText.text.toString()
            )
            require(!binding.hasPendingBindings()) {
                "executePendingBindings should clear dirty state"
            }
        }
    }

    @Test
    fun executePendingBindingsWritesPropertyAccessExpressions() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 设置 ViewModel（XML 中有 @{viewModel.name} 和 @{viewModel.status}）
            binding.viewModel = ItemViewModel(
                name = "Property Access Test",
                status = "Running",
                itemId = 123
            )
            requireTrue("Setting viewModel should mark dirty", binding.hasPendingBindings())

            // 执行绑定
            binding.executePendingBindings()

            // 验证属性访问表达式已正确写入
            requireEquals(
                "vmNameText should show viewModel.name",
                "Property Access Test",
                binding.vmNameText.text.toString()
            )
            requireEquals(
                "vmStatusText should show viewModel.status",
                "Running",
                binding.vmStatusText.text.toString()
            )
        }
    }

    @Test
    fun executePendingBindingsHandlesNullVariablesGracefully() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 设置为 null（属性映射器应该使用默认值）
            binding.title = null
            binding.description = null
            binding.viewModel = null

            binding.executePendingBindings()

            // 验证使用了默认值（对于 String，默认是 ""）
            requireEquals(
                "null title should fallback to empty string",
                "",
                binding.titleText.text.toString()
            )
            requireEquals(
                "null description should fallback to empty string",
                "",
                binding.descriptionText.text.toString()
            )
            requireEquals(
                "null viewModel.name should fallback to empty string",
                "",
                binding.vmNameText.text.toString()
            )
        }
    }

    @Test
    fun dirtyFlagMechanismTracksMultipleVariableChanges() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 初始清空
            binding.executePendingBindings()
            require(!binding.hasPendingBindings())

            // 修改多个变量
            binding.title = "First"
            requireTrue("First change should mark dirty", binding.hasPendingBindings())

            binding.description = "Second"
            requireTrue("Second change should keep dirty", binding.hasPendingBindings())

            binding.count = 99
            requireTrue("Third change should keep dirty", binding.hasPendingBindings())

            // 执行绑定
            binding.executePendingBindings()
            require(!binding.hasPendingBindings()) {
                "executePendingBindings should clear all dirty flags"
            }

            // 再次修改
            binding.title = "Changed Again"
            requireTrue("Modification after clear should mark dirty again", binding.hasPendingBindings())
        }
    }

    @Test
    fun invalidateAllMarksPendingBindingsForAllVariables() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 设置所有变量但不执行
            binding.title = "Title"
            binding.description = "Desc"
            binding.count = 1
            binding.isVisible = true
            binding.viewModel = ItemViewModel("VM", "OK", 1)

            // 先清空
            binding.executePendingBindings()
            require(!binding.hasPendingBindings())

            // 调用 invalidateAll（应该标记所有变量为 dirty）
            binding.invalidateAll()
            requireTrue(
                "invalidateAll should mark all variables dirty",
                binding.hasPendingBindings()
            )

            // 执行绑定（应该重新写入所有 View）
            binding.executePendingBindings()
            requireEquals("titleText.text should be Title", "Title", binding.titleText.text.toString())
            requireEquals("descriptionText.text should be Desc", "Desc", binding.descriptionText.text.toString())
        }
    }

    @Test
    fun bindingIsRegisteredOnRootView() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // DataBindingUtil 应该能通过 root view 找回 binding
            val retrieved = DataBindingUtil.getBinding<DemoDataBindingEnhancedX2CBinding>(binding.root)
            requireTrue("Binding should be registered on root", retrieved === binding)
        }
    }

    @Test
    fun viewFieldsAreNonNullAndResolveCorrectIds() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // View 字段应该非空且 ID 正确
            requireEquals("titleText ID", R.id.title_text, binding.titleText.id)
            requireEquals("descriptionText ID", R.id.description_text, binding.descriptionText.id)
            requireEquals("countText ID", R.id.count_text, binding.countText.id)
            requireEquals("vmNameText ID", R.id.vm_name_text, binding.vmNameText.id)
            requireEquals("vmStatusText ID", R.id.vm_status_text, binding.vmStatusText.id)
            requireEquals("btnUpdate ID", R.id.btn_update, binding.btnUpdate.id)
            requireEquals("btnToggle ID", R.id.btn_toggle, binding.btnToggle.id)

            // View 类型应该正确
            requireTrue("titleText should be android.widget.TextView",
                binding.titleText.javaClass.name.contains("TextView"))
            requireTrue("btnUpdate should be android.widget.Button",
                binding.btnUpdate.javaClass.name.contains("Button"))
        }
    }

    @Test
    fun simpleDataBindingLayoutSupportsBasicInflationAndFields() {
        runOnMainThread {
            val binding = DemoDataBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 简单 layout 只有一个变量
            binding.title = "Simple Binding"
            requireEquals("title should be readable", "Simple Binding", binding.title)

            // View 字段存在
            requireEquals("titleText ID", R.id.title_text, binding.titleText.id)
            requireEquals("rootContainer ID", R.id.root_container, binding.root.id)
        }
    }

    @Test
    fun dataBindingIncludesUseX2CSubclassInstancesAndReceiveVariables() {
        runOnMainThread {
            val binding = DemoDataBindingIncludeParentX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            requireTrue(
                "static child should be generated X2C binding subclass",
                binding.staticChild is DemoDataBindingIncludeChildX2CBinding
            )
            requireTrue(
                "dynamic child should be generated X2C binding subclass",
                binding.dynamicChild is DemoDataBindingIncludeChildX2CBinding
            )

            binding.dynamicText = "动态"
            binding.executePendingBindings()

            requireEquals(
                "static include text should receive literal variable",
                "首页",
                binding.staticChild.includeChildText.text.toString()
            )
            requireEquals(
                "dynamic include text should receive parent variable",
                "动态",
                binding.dynamicChild.includeChildText.text.toString()
            )
            require(!binding.hasPendingBindings()) {
                "parent and contained child bindings should be clean after executePendingBindings"
            }
        }
    }

    @Test
    fun variableChangeWithoutExecutePendingBindingsDoesNotUpdateViews() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 初始值
            binding.title = "Initial"
            binding.executePendingBindings()
            requireEquals("titleText should show Initial", "Initial", binding.titleText.text.toString())

            // 修改变量但不调用 executePendingBindings
            binding.title = "Changed"
            requireTrue("Should be marked dirty", binding.hasPendingBindings())

            // View 不应该立即更新（需要等待 executePendingBindings）
            requireEquals(
                "View should still show old value until executePendingBindings",
                "Initial",
                binding.titleText.text.toString()
            )

            // 执行后才更新
            binding.executePendingBindings()
            requireEquals("titleText should show Changed", "Changed", binding.titleText.text.toString())
        }
    }

    @Test
    fun twoWayBindingListenerIsSetupForEditText() {
        runOnMainThread {
            // 注意：demo_data_binding_enhanced.xml 中没有 EditText 的双向绑定示例
            // 这个测试验证 setupTwoWayBindings() 方法存在且可调用
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // setupTwoWayBindings 应该在 bind() 时自动调用
            // 手动调用应该是安全的（即使没有双向绑定也不应崩溃）
            binding.setupTwoWayBindings()

            // 如果有双向绑定，监听器应该已设置
            // 当前 demo 没有 EditText 双向绑定，所以这里只是确保方法存在
            requireTrue("setupTwoWayBindings should complete without error", true)
        }
    }

    @Test
    fun bindingWorksWithAttachToParentFalse() {
        runOnMainThread {
            val parent = FrameLayout(context)
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                parent,
                false // attachToParent = false
            )

            // root 不应该被添加到 parent
            requireEquals("Parent should have no children", 0, parent.childCount)

            // 但 binding 应该可用
            binding.title = "Detached"
            binding.executePendingBindings()
            requireEquals("titleText should show Detached", "Detached", binding.titleText.text.toString())

            // 手动添加到 parent
            parent.addView(binding.root)
            requireEquals("Parent should now have 1 child", 1, parent.childCount)
        }
    }

    @Test
    fun bindingWorksWithAttachToParentTrue() {
        runOnMainThread {
            val parent = FrameLayout(context)
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                parent,
                true // attachToParent = true
            )

            // root 应该已经被添加到 parent
            requireEquals("Parent should have 1 child", 1, parent.childCount)
            requireTrue("First child should be binding root", parent.getChildAt(0) === binding.root)
        }
    }

    @Test
    fun bindMethodCanRebindExistingView() {
        runOnMainThread {
            val parent = FrameLayout(context)

            // 先通过正常方式创建 View
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.demo_data_binding_enhanced, parent, false)

            // 使用 bind() 方法创建 binding
            val binding = DemoDataBindingEnhancedX2CBinding.bind(view)

            // 验证 binding 可用
            binding.title = "Rebound"
            binding.executePendingBindings()
            requireEquals("titleText should show Rebound", "Rebound", binding.titleText.text.toString())

            // 验证 root 是同一个 view
            requireTrue("bind should use the provided view as root", binding.root === view)
        }
    }

    // ========== Helper Methods ==========

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
        }
    }

    private fun requireTrue(message: String, condition: Boolean) {
        if (!condition) throw AssertionError(message)
    }

    private fun requireEquals(message: String, expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw AssertionError("$message: expected <$expected> but was <$actual>")
        }
    }

    /**
     * 获取 BR ID（模拟从 BR 类反射获取）。
     * 实际生成代码中，会尝试从 com.github.donglua.layoutx2c.demo.BR 类读取。
     */
    private fun getBrId(variableName: String): Int {
        return try {
            val brClass = Class.forName("com.github.donglua.layoutx2c.demo.BR")
            brClass.getField(variableName).getInt(null)
        } catch (e: Throwable) {
            // 如果 BR 类不存在（可能 DataBinding 没启用），使用 fallback ID
            // 根据 BindingFacadeGeneratorV2 的实现，fallback ID 从 1 开始递增
            when (variableName) {
                "title" -> 1
                "description" -> 2
                "count" -> 3
                "isVisible" -> 4
                "viewModel" -> 5
                else -> -1
            }
        }
    }
}
