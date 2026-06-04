package com.github.donglua.layoutx2c.runtime.annotation

/**
 * Value kinds supported by whitelisted custom View attributes.
 */
@PublicApi
enum class FastCustomViewAttrKind {
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
