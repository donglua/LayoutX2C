package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyntheticAttributeSetTest {

    @Test
    fun `attribute lookup supports namespace name index and resource ids`() {
        val attrs = SyntheticAttributeSet.of(
            SyntheticAttributeSet.Attribute(
                namespace = SyntheticAttributeSet.ANDROID_NAMESPACE,
                name = "id",
                value = "@+id/price",
                nameResourceId = android.R.attr.id,
                valueResourceId = 1001
            ),
            SyntheticAttributeSet.Attribute(
                namespace = "http://schemas.android.com/apk/res-auto",
                name = "priceColor",
                value = "@color/red",
                nameResourceId = 2001,
                valueResourceId = 3001
            ),
            SyntheticAttributeSet.Attribute(
                namespace = null,
                name = "style",
                value = "@style/PriceStyle",
                valueResourceId = 4001
            )
        )

        assertThat(attrs.attributeCount).isEqualTo(3)
        assertThat(attrs.getAttributeName(1)).isEqualTo("priceColor")
        assertThat(attrs.getAttributeValue("http://schemas.android.com/apk/res-auto", "priceColor"))
            .isEqualTo("@color/red")
        assertThat(attrs.getAttributeNameResource(1)).isEqualTo(2001)
        assertThat(attrs.getAttributeResourceValue("http://schemas.android.com/apk/res-auto", "priceColor", -1))
            .isEqualTo(3001)
        assertThat(attrs.idAttribute).isEqualTo("@+id/price")
        assertThat(attrs.getIdAttributeResourceValue(-1)).isEqualTo(1001)
        assertThat(attrs.styleAttribute).isEqualTo(4001)
    }

    @Test
    fun `primitive getters return parsed values or defaults`() {
        val attrs = SyntheticAttributeSet.of(
            SyntheticAttributeSet.Attribute(null, "enabled", "true"),
            SyntheticAttributeSet.Attribute(null, "count", "0x10"),
            SyntheticAttributeSet.Attribute(null, "ratio", "1.5"),
            SyntheticAttributeSet.Attribute(null, "mode", "expanded")
        )

        assertThat(attrs.getAttributeBooleanValue(null, "enabled", false)).isTrue()
        assertThat(attrs.getAttributeIntValue(null, "count", -1)).isEqualTo(16)
        assertThat(attrs.getAttributeUnsignedIntValue(null, "count", -1)).isEqualTo(16)
        assertThat(attrs.getAttributeFloatValue(null, "ratio", -1f)).isEqualTo(1.5f)
        assertThat(attrs.getAttributeListValue(null, "mode", arrayOf("collapsed", "expanded"), -1)).isEqualTo(1)
        assertThat(attrs.getAttributeIntValue(null, "missing", -1)).isEqualTo(-1)
    }
}
