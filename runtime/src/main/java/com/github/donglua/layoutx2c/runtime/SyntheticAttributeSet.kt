package com.github.donglua.layoutx2c.runtime

import android.util.AttributeSet
import com.github.donglua.layoutx2c.runtime.annotation.PublicApi

/**
 * AttributeSet implementation backed by compile-time XML metadata.
 */
@PublicApi
class SyntheticAttributeSet private constructor(
    private val attributes: Array<out Attribute>
) : AttributeSet {

    @PublicApi
    data class Attribute(
        val namespace: String?,
        val name: String,
        val value: String,
        val nameResourceId: Int = 0,
        val valueResourceId: Int = 0
    )

    override fun getAttributeCount(): Int = attributes.size

    override fun getAttributeName(index: Int): String = attributes[index].name

    override fun getAttributeValue(index: Int): String = attributes[index].value

    override fun getAttributeValue(namespace: String?, name: String): String? {
        return find(namespace, name)?.value
    }

    override fun getPositionDescription(): String = "LayoutX2C synthetic AttributeSet"

    override fun getAttributeNameResource(index: Int): Int = attributes[index].nameResourceId

    override fun getAttributeListValue(
        namespace: String?,
        attribute: String,
        options: Array<out String>,
        defaultValue: Int
    ): Int = findListValue(find(namespace, attribute)?.value, options, defaultValue)

    override fun getAttributeBooleanValue(namespace: String?, attribute: String, defaultValue: Boolean): Boolean {
        return find(namespace, attribute)?.value?.toBooleanStrictOrNull() ?: defaultValue
    }

    override fun getAttributeResourceValue(namespace: String?, attribute: String, defaultValue: Int): Int {
        return find(namespace, attribute)?.valueResourceId?.takeIf { it != 0 } ?: defaultValue
    }

    override fun getAttributeIntValue(namespace: String?, attribute: String, defaultValue: Int): Int {
        return parseInt(find(namespace, attribute)?.value) ?: defaultValue
    }

    override fun getAttributeUnsignedIntValue(namespace: String?, attribute: String, defaultValue: Int): Int {
        return parseInt(find(namespace, attribute)?.value)?.takeIf { it >= 0 } ?: defaultValue
    }

    override fun getAttributeFloatValue(namespace: String?, attribute: String, defaultValue: Float): Float {
        return find(namespace, attribute)?.value?.toFloatOrNull() ?: defaultValue
    }

    override fun getAttributeListValue(index: Int, options: Array<out String>, defaultValue: Int): Int {
        return findListValue(attributes[index].value, options, defaultValue)
    }

    override fun getAttributeBooleanValue(index: Int, defaultValue: Boolean): Boolean {
        return attributes[index].value.toBooleanStrictOrNull() ?: defaultValue
    }

    override fun getAttributeResourceValue(index: Int, defaultValue: Int): Int {
        return attributes[index].valueResourceId.takeIf { it != 0 } ?: defaultValue
    }

    override fun getAttributeIntValue(index: Int, defaultValue: Int): Int {
        return parseInt(attributes[index].value) ?: defaultValue
    }

    override fun getAttributeUnsignedIntValue(index: Int, defaultValue: Int): Int {
        return parseInt(attributes[index].value)?.takeIf { it >= 0 } ?: defaultValue
    }

    override fun getAttributeFloatValue(index: Int, defaultValue: Float): Float {
        return attributes[index].value.toFloatOrNull() ?: defaultValue
    }

    override fun getIdAttribute(): String? {
        return find(ANDROID_NAMESPACE, "id")?.value
    }

    override fun getClassAttribute(): String? {
        return find(null, "class")?.value
    }

    override fun getIdAttributeResourceValue(defaultValue: Int): Int {
        return find(ANDROID_NAMESPACE, "id")?.valueResourceId?.takeIf { it != 0 } ?: defaultValue
    }

    override fun getStyleAttribute(): Int {
        return find(null, "style")?.valueResourceId ?: 0
    }

    private fun find(namespace: String?, name: String): Attribute? {
        return attributes.firstOrNull { it.namespace == namespace && it.name == name }
    }

    private fun findListValue(value: String?, options: Array<out String>, defaultValue: Int): Int {
        if (value == null) return defaultValue
        val index = options.indexOf(value)
        return if (index >= 0) index else defaultValue
    }

    private fun parseInt(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return runCatching { Integer.decode(value) }.getOrNull()
    }

    companion object {
        const val ANDROID_NAMESPACE: String = "http://schemas.android.com/apk/res/android"

        @JvmStatic
        fun of(vararg attributes: Attribute): SyntheticAttributeSet {
            return SyntheticAttributeSet(attributes)
        }
    }
}
