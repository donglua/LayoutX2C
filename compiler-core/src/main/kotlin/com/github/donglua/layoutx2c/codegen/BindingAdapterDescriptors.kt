package com.github.donglua.layoutx2c.codegen

data class BindingAdapterDescriptor(
    val attrs: List<String>,
    val methodClassName: String,
    val methodName: String,
    val requireAll: Boolean = true
)
