package com.github.donglua.layoutx2c.codegen

data class BindingAdapterDescriptor(
    val attrs: List<String>,
    val methodClassName: String,
    val methodName: String,
    val requireAll: Boolean = true
)

internal fun bindingAdapterXmlAttrName(
    declaredAttrName: String,
    availableAttributeNames: Set<String>
): String? {
    val xmlAttrName = if (declaredAttrName.contains(":")) declaredAttrName else "app:$declaredAttrName"
    return xmlAttrName.takeIf { it in availableAttributeNames }
}
