package com.github.donglua.layoutx2c.codegen

import com.squareup.kotlinpoet.CodeBlock

/**
 * 将 Android 属性名映射到 View 属性/方法调用。
 * 用于生成 @{} 表达式的绑定代码。
 */
object DataBindingAttributeMapper {

    /**
     * 属性映射信息：属性名 → (View 属性名, 类型, 默认值处理)
     */
    private val attributeMap = mapOf(
        // 文本属性
        "android:text" to AttributeMapping(
            propertyName = "text",
            viewType = "TextView",
            defaultValue = "\"\"",
            typeHint = "String?"
        ),
        "android:hint" to AttributeMapping(
            propertyName = "hint",
            viewType = "TextView",
            defaultValue = "\"\"",
            typeHint = "String?"
        ),
        "android:contentDescription" to AttributeMapping(
            propertyName = "contentDescription",
            viewType = "View",
            defaultValue = "\"\"",
            typeHint = "String?"
        ),

        // 可见性属性
        "android:visibility" to AttributeMapping(
            propertyName = "visibility",
            viewType = "View",
            defaultValue = "View.GONE",
            typeHint = "Boolean?",
            customSetter = { varName ->
                "if ($varName == true) View.VISIBLE else View.GONE"
            }
        ),

        // 勾选属性
        "android:checked" to AttributeMapping(
            propertyName = "isChecked",
            viewType = "CompoundButton",
            defaultValue = "false",
            typeHint = "Boolean?"
        ),

        // 启用/禁用
        "android:enabled" to AttributeMapping(
            propertyName = "isEnabled",
            viewType = "View",
            defaultValue = "true",
            typeHint = "Boolean?"
        ),

        // 透明度
        "android:alpha" to AttributeMapping(
            propertyName = "alpha",
            viewType = "View",
            defaultValue = "1f",
            typeHint = "Float?"
        ),

        // 背景颜色
        "android:background" to AttributeMapping(
            propertyName = "setBackgroundColor",
            viewType = "View",
            defaultValue = "0",
            typeHint = "Int?",
            isMethod = true
        ),

        // 文本颜色
        "android:textColor" to AttributeMapping(
            propertyName = "setTextColor",
            viewType = "TextView",
            defaultValue = "0",
            typeHint = "Int?",
            isMethod = true
        ),

        // 图片资源
        "android:src" to AttributeMapping(
            propertyName = "setImageResource",
            viewType = "ImageView",
            defaultValue = "0",
            typeHint = "Int?",
            isMethod = true
        ),

        // 进度
        "android:progress" to AttributeMapping(
            propertyName = "progress",
            viewType = "ProgressBar",
            defaultValue = "0",
            typeHint = "Int?"
        )
    )

    /**
     * 检查属性是否支持自动绑定。
     */
    fun isSupportedAttribute(attrName: String): Boolean {
        return attrName in attributeMap
    }

    /**
     * 双向绑定白名单：(viewType, attrName) 是否能生成反向监听器。
     * MVP 仅支持 EditText.android:text 和 CompoundButton.android:checked。
     */
    private val twoWayWhitelist = setOf(
        "EditText" to "android:text",
        "android.widget.EditText" to "android:text",
        "CompoundButton" to "android:checked",
        "android.widget.CompoundButton" to "android:checked",
        "CheckBox" to "android:checked",
        "android.widget.CheckBox" to "android:checked",
        "Switch" to "android:checked",
        "android.widget.Switch" to "android:checked",
        "RadioButton" to "android:checked",
        "android.widget.RadioButton" to "android:checked",
        "ToggleButton" to "android:checked",
        "android.widget.ToggleButton" to "android:checked"
    )

    /**
     * 检查 (viewType, attrName) 组合是否支持双向绑定的反向监听器代码生成。
     */
    fun isTwoWayBindingSupported(viewTagName: String, attrName: String): Boolean {
        return (viewTagName to attrName) in twoWayWhitelist
    }

    /**
     * 获取属性的映射信息。
     */
    fun getMapping(attrName: String): AttributeMapping? {
        return attributeMap[attrName]
    }

    /**
     * 生成绑定代码。
     * 例如：titleText.text = title ?: ""
     */
    fun generateBindingCode(
        viewFieldName: String,
        attrName: String,
        variableName: String,
        propertyPath: String? = null
    ): String? {
        val mapping = getMapping(attrName) ?: return null

        val fullVarRef = if (propertyPath != null) {
            "$variableName?.$propertyPath"
        } else {
            variableName
        }

        return if (mapping.isMethod) {
            // 方法调用：view.setProperty(value ?: default)
            "$viewFieldName.${mapping.propertyName}($fullVarRef ?: ${mapping.defaultValue})"
        } else {
            // 属性赋值：view.property = value ?: default
            if (mapping.customSetter != null) {
                val setterExpr = mapping.customSetter.invoke(fullVarRef)
                "$viewFieldName.${mapping.propertyName} = $setterExpr"
            } else {
                "$viewFieldName.${mapping.propertyName} = $fullVarRef ?: ${mapping.defaultValue}"
            }
        }
    }

    /**
     * 生成双向绑定的反向监听器代码。
     *
     * 仅对白名单 (viewType, attrName) 组合生效（参见 [isTwoWayBindingSupported]）。
     * 假定目标变量类型为 `androidx.lifecycle.MutableLiveData<T>`，
     * 通过 `value` 属性回写视图变化。
     *
     * 返回值是一段多行 Kotlin 代码（已包含必要的 import 通过完全限定名），
     * 调用方需要把它写入到 BindingFacade 的 setup 方法体中。
     * 不支持的组合返回 null。
     */
    fun generateTwoWayListenerCode(
        viewTagName: String,
        viewFieldName: String,
        attrName: String,
        variableName: String,
        propertyPath: String? = null
    ): String? {
        if (!isTwoWayBindingSupported(viewTagName, attrName)) return null

        val targetRef = if (propertyPath != null) {
            "$variableName?.$propertyPath"
        } else {
            variableName
        }

        return when (attrName) {
            "android:text" -> buildTextWatcherCode(viewFieldName, targetRef)
            "android:checked" -> buildCheckedListenerCode(viewFieldName, targetRef)
            else -> null
        }
    }

    private fun buildTextWatcherCode(viewFieldName: String, targetRef: String): String {
        // 用 doAfterTextChanged 扩展会更简洁，但避免引入 androidx.core 依赖；
        // 这里直接使用 TextWatcher 匿名子类。
        return """
            $viewFieldName.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val newValue = s?.toString() ?: ""
                    if ($targetRef?.value != newValue) {
                        $targetRef?.value = newValue
                    }
                }
            })
        """.trimIndent()
    }

    private fun buildCheckedListenerCode(viewFieldName: String, targetRef: String): String {
        return """
            $viewFieldName.setOnCheckedChangeListener { _, isChecked ->
                if ($targetRef?.value != isChecked) {
                    $targetRef?.value = isChecked
                }
            }
        """.trimIndent()
    }

    /**
     * 属性映射信息。
     */
    data class AttributeMapping(
        val propertyName: String,
        val viewType: String,
        val defaultValue: String,
        val typeHint: String,
        val isMethod: Boolean = false,
        val customSetter: ((String) -> String)? = null
    )
}
