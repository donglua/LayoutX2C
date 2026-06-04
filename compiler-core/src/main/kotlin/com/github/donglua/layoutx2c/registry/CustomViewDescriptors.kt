package com.github.donglua.layoutx2c.registry

enum class CustomViewAttributeKind {
    STRING,
    BOOLEAN,
    INT,
    FLOAT,
    DIMENSION,
    COLOR,
    COLOR_STATE_LIST,
    DRAWABLE_REF,
    RESOURCE_REF
}

data class CustomViewAttribute(
    val name: String,
    val kind: CustomViewAttributeKind
)

data class CustomViewDescriptor(
    val viewClassName: String,
    val attributes: List<CustomViewAttribute>
)
