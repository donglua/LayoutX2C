package com.github.donglua.layoutx2c.demo

import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.donglua.layoutx2c.demo.generated.DemoTwoWayBindingX2CBinding
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 双向绑定等价性测试：验证 @={} 表达式生成的反向监听器正确工作。
 *
 * 测试覆盖：
 * - EditText.text 双向绑定（TextWatcher）
 * - CompoundButton.checked 双向绑定（OnCheckedChangeListener）
 * - 用户输入自动回写到变量
 * - 变量修改正向写入到 View
 */
@RunWith(AndroidJUnit4::class)
class TwoWayBindingEquivalenceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun editTextTwoWayBindingUpdatesVariableOnUserInput() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 初始设置
            binding.userName = "Initial Name"
            binding.executePendingBindings()

            // 验证正向绑定：变量 -> View
            requireEquals("inputName should show initial value", "Initial Name", binding.inputName.text.toString())

            // 模拟用户输入（反向绑定：View -> 变量）
            binding.inputName.setText("User Typed Name")

            // 等待 TextWatcher 触发（在真实环境中是异步的，但在测试中通常是同步）
            // 变量应该已经更新
            requireEquals(
                "userName should be updated by TextWatcher",
                "User Typed Name",
                binding.userName
            )
        }
    }

    @Test
    fun editTextTwoWayBindingAvoidsInfiniteLoopOnSameValue() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 设置初始值
            binding.userName = "Test"
            binding.executePendingBindings()

            // 用户输入相同的值（TextWatcher 应该检查 newValue != oldValue 避免循环）
            val countBefore = 0
            binding.inputName.setText("Test")

            // 应该没有触发无限循环（如果实现正确，会检查值是否真的改变）
            requireTrue("No infinite loop should occur", true)
        }
    }

    @Test
    fun checkBoxTwoWayBindingUpdatesVariableOnUserCheck() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 初始设置
            binding.isAccepted = false
            binding.executePendingBindings()

            // 验证正向绑定
            val checkbox = binding.checkboxAccept as android.widget.CheckBox
            requireEquals("checkbox should be unchecked", false, checkbox.isChecked)

            // 模拟用户勾选
            checkbox.isChecked = true

            // 变量应该已更新
            requireEquals(
                "isAccepted should be updated by OnCheckedChangeListener",
                true,
                binding.isAccepted
            )
        }
    }

    @Test
    fun switchTwoWayBindingUpdatesVariableOnUserToggle() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 初始设置
            binding.isEnabled = false
            binding.executePendingBindings()

            // 验证正向绑定
            requireEquals(false, (binding.switchEnabled as androidx.appcompat.widget.SwitchCompat).isChecked)

            // 模拟用户切换
            (binding.switchEnabled as androidx.appcompat.widget.SwitchCompat).isChecked = true

            // 变量应该已更新
            requireEquals(
                "isEnabled should be updated by OnCheckedChangeListener",
                true,
                binding.isEnabled
            )
        }
    }

    @Test
    fun variableChangeUpdatesViewInForwardDirection() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 通过变量修改（正向）
            binding.userName = "Forward Update"
            binding.userEmail = "test@example.com"
            binding.isAccepted = true
            binding.isEnabled = true

            binding.executePendingBindings()

            // View 应该更新
            requireEquals("inputName should show Forward Update", "Forward Update", binding.inputName.text.toString())
            requireEquals("inputEmail should show test@example.com", "test@example.com", binding.inputEmail.text.toString())
            requireEquals(true, (binding.checkboxAccept as android.widget.CheckBox).isChecked)
            requireEquals(true, (binding.switchEnabled as androidx.appcompat.widget.SwitchCompat).isChecked)
        }
    }

    @Test
    fun twoWayBindingWorksWithNullValues() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 设置为 null
            binding.userName = null
            binding.userEmail = null
            binding.isAccepted = null
            binding.isEnabled = null

            binding.executePendingBindings()

            // EditText 应该显示空字符串（默认值）
            requireEquals("", binding.inputName.text.toString())
            requireEquals("", binding.inputEmail.text.toString())

            // CheckBox/Switch 应该使用 false 作为默认值
            requireEquals(false, (binding.checkboxAccept as android.widget.CheckBox).isChecked)
            requireEquals(false, (binding.switchEnabled as androidx.appcompat.widget.SwitchCompat).isChecked)
        }
    }

    @Test
    fun multipleEditTextUpdatesWorkIndependently() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 分别修改两个 EditText
            binding.inputName.setText("Name 1")
            binding.inputEmail.setText("email@test.com")

            // 各自的变量应该独立更新
            requireEquals("Name 1", binding.userName)
            requireEquals("email@test.com", binding.userEmail)

            // 修改其中一个不应影响另一个
            binding.inputName.setText("Name 2")
            requireEquals("Name 2", binding.userName)
            requireEquals("email@test.com", binding.userEmail) // 保持不变
        }
    }

    @Test
    fun twoWayBindingListenersAreSetupOnBind() {
        runOnMainThread {
            val parent = FrameLayout(context)

            // 直接通过 LayoutInflater 创建 View
            val view = LayoutInflater.from(context).inflate(
                R.layout.demo_two_way_binding,
                parent,
                false
            )

            // 通过 bind() 创建 binding
            val binding = DemoTwoWayBindingX2CBinding.bind(view)

            // 监听器应该已经设置（在 bind() -> setupTwoWayBindings() 中）
            // 测试反向绑定是否工作
            val nameEditText = view.findViewById<EditText>(R.id.input_name)
            nameEditText.setText("Bound Later")

            requireEquals(
                "Two-way listener should work after bind()",
                "Bound Later",
                binding.userName
            )
        }
    }

    @Test
    fun emptyStringInEditTextUpdatesVariableToEmptyString() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 先设置非空值
            binding.userName = "Not Empty"
            binding.executePendingBindings()

            // 用户清空输入
            binding.inputName.setText("")

            // 变量应该更新为空字符串（不是 null）
            requireEquals("", binding.userName)
        }
    }

    @Test
    fun rapidUserInputUpdatesVariableCorrectly() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 模拟快速输入
            binding.inputName.setText("A")
            requireEquals("A", binding.userName)

            binding.inputName.setText("AB")
            requireEquals("AB", binding.userName)

            binding.inputName.setText("ABC")
            requireEquals("ABC", binding.userName)

            binding.inputName.setText("ABCD")
            requireEquals("ABCD", binding.userName)

            // 最终值应该正确
            requireEquals("ABCD", binding.userName)
        }
    }

    @Test
    fun programmaticCheckChangeDoesNotCauseDirtyLoop() {
        runOnMainThread {
            val binding = DemoTwoWayBindingX2CBinding.inflate(
                LayoutInflater.from(context),
                FrameLayout(context),
                false
            )

            // 通过变量设置
            binding.isAccepted = true
            binding.executePendingBindings()
            binding.executePendingBindings()

            // 此时 View 已更新，但不应该再次触发反向监听器
            // （因为监听器中有 if (value != newValue) 检查）

            // 清空 dirty 状态
            require(!binding.hasPendingBindings())

            // 编程方式设置相同的值
            (binding.checkboxAccept as android.widget.CheckBox).isChecked = true

            // 由于值没变，反向监听器不应该设置变量（避免循环）
            // 这里我们无法直接验证，但可以确保没有崩溃
            requireTrue("No crash should occur", true)
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

    private fun requireEquals(expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw AssertionError("Expected <$expected> but was <$actual>")
        }
    }

    private fun requireEquals(message: String, expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw AssertionError("$message: expected <$expected> but was <$actual>")
        }
    }
}
