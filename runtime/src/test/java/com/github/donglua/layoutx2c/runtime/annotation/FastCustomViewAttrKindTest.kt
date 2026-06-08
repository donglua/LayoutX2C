package com.github.donglua.layoutx2c.runtime.annotation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FastCustomViewAttrKindTest {

    @Test
    fun `values expose all supported custom view attribute kinds in declaration order`() {
        assertThat(FastCustomViewAttrKind.entries).containsExactly(
            FastCustomViewAttrKind.STRING,
            FastCustomViewAttrKind.BOOLEAN,
            FastCustomViewAttrKind.INT,
            FastCustomViewAttrKind.FLOAT,
            FastCustomViewAttrKind.DIMENSION,
            FastCustomViewAttrKind.COLOR,
            FastCustomViewAttrKind.COLOR_STATE_LIST,
            FastCustomViewAttrKind.DRAWABLE_REF,
            FastCustomViewAttrKind.RESOURCE_REF
        ).inOrder()
    }
}
