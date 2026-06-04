package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CustomViewConfigParserTest {

    @Test
    fun `extracts custom view descriptors from config object`() {
        val source = """
            package com.example

            import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViews
            import com.github.donglua.layoutx2c.runtime.annotation.FastCustomView
            import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViewAttr
            import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViewAttrKind

            @FastCustomViews(
                FastCustomView(
                    viewClass = com.example.widget.PriceView::class,
                    attrs = [
                        FastCustomViewAttr(name = "app:priceColor", kind = FastCustomViewAttrKind.COLOR),
                        FastCustomViewAttr(name = "app:label", kind = FastCustomViewAttrKind.STRING)
                    ]
                )
            )
            object LayoutX2CConfig
        """.trimIndent()

        val descriptors = CustomViewConfigParser.extractCustomViews(source)

        assertThat(descriptors).hasSize(1)
        assertThat(descriptors.single().viewClassName).isEqualTo("com.example.widget.PriceView")
        assertThat(descriptors.single().attributes.map { it.name }).containsExactly(
            "app:priceColor",
            "app:label"
        )
    }
}
