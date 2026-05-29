package com.github.donglua.layoutx2c.codegen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * DataBindingAttributeMapper 测试：验证属性映射和绑定代码生成
 */
class DataBindingAttributeMapperTest {

    @Test
    fun `text attribute generates property assignment with default`() {
        val code = DataBindingAttributeMapper.generateBindingCode(
            viewFieldName = "titleText",
            attrName = "android:text",
            variableName = "title",
            propertyPath = null
        )
        assertThat(code).isEqualTo("titleText.text = title ?: \"\"")
    }

    @Test
    fun `text attribute with property path generates nested access`() {
        val code = DataBindingAttributeMapper.generateBindingCode(
            viewFieldName = "nameText",
            attrName = "android:text",
            variableName = "user",
            propertyPath = "name"
        )
        assertThat(code).isEqualTo("nameText.text = user?.name ?: \"\"")
    }

    @Test
    fun `visibility attribute uses custom setter`() {
        val code = DataBindingAttributeMapper.generateBindingCode(
            viewFieldName = "panel",
            attrName = "android:visibility",
            variableName = "isVisible",
            propertyPath = null
        )
        assertThat(code).isEqualTo("panel.visibility = if (isVisible == true) View.VISIBLE else View.GONE")
    }

    @Test
    fun `checked attribute generates isChecked assignment`() {
        val code = DataBindingAttributeMapper.generateBindingCode(
            viewFieldName = "checkbox",
            attrName = "android:checked",
            variableName = "isChecked",
            propertyPath = null
        )
        assertThat(code).isEqualTo("checkbox.isChecked = isChecked ?: false")
    }

    @Test
    fun `enabled attribute generates isEnabled assignment`() {
        val code = DataBindingAttributeMapper.generateBindingCode(
            viewFieldName = "btn",
            attrName = "android:enabled",
            variableName = "canClick",
            propertyPath = null
        )
        assertThat(code).isEqualTo("btn.isEnabled = canClick ?: true")
    }

    @Test
    fun `src attribute uses method call`() {
        val code = DataBindingAttributeMapper.generateBindingCode(
            viewFieldName = "image",
            attrName = "android:src",
            variableName = "iconRes",
            propertyPath = null
        )
        assertThat(code).isEqualTo("image.setImageResource(iconRes ?: 0)")
    }

    @Test
    fun `unsupported attribute returns null`() {
        val code = DataBindingAttributeMapper.generateBindingCode(
            viewFieldName = "view",
            attrName = "android:layout_width",
            variableName = "width",
            propertyPath = null
        )
        assertThat(code).isNull()
    }

    @Test
    fun `isSupportedAttribute returns true for known attributes`() {
        assertThat(DataBindingAttributeMapper.isSupportedAttribute("android:text")).isTrue()
        assertThat(DataBindingAttributeMapper.isSupportedAttribute("android:visibility")).isTrue()
        assertThat(DataBindingAttributeMapper.isSupportedAttribute("android:checked")).isTrue()
    }

    @Test
    fun `isSupportedAttribute returns false for unknown attributes`() {
        assertThat(DataBindingAttributeMapper.isSupportedAttribute("android:layout_width")).isFalse()
        assertThat(DataBindingAttributeMapper.isSupportedAttribute("custom:attr")).isFalse()
    }
}
