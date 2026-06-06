package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BindingAdapterConfigParserTest {

    @Test
    fun `extracts binding adapter descriptors from config object`() {
        val source = """
            package com.example

            import com.example.binding.SampleBindingAdapters
            import com.github.donglua.layoutx2c.runtime.annotation.FastBindingAdapter
            import com.github.donglua.layoutx2c.runtime.annotation.FastBindingAdapters

            @FastBindingAdapters(
                FastBindingAdapter(
                    attrs = [
                        "app:stateColorRes",
                        "app:stateSizeDp"
                    ],
                    methodClass = SampleBindingAdapters::class,
                    methodName = "setViewState",
                    requireAll = true
                ),
                FastBindingAdapter(
                    attrs = ["app:labelText"],
                    methodClass = com.example.binding.LabelBindingAdapters::class,
                    methodName = "setLabelText",
                    requireAll = false
                )
            )
            object LayoutX2CConfig
        """.trimIndent()

        val descriptors = BindingAdapterConfigParser.extractBindingAdapters(source)

        assertThat(descriptors).hasSize(2)
        assertThat(descriptors[0].attrs).containsExactly(
            "app:stateColorRes",
            "app:stateSizeDp"
        ).inOrder()
        assertThat(descriptors[0].methodClassName).isEqualTo("com.example.binding.SampleBindingAdapters")
        assertThat(descriptors[0].methodName).isEqualTo("setViewState")
        assertThat(descriptors[0].requireAll).isTrue()
        assertThat(descriptors[1].attrs).containsExactly("app:labelText")
        assertThat(descriptors[1].methodClassName).isEqualTo("com.example.binding.LabelBindingAdapters")
        assertThat(descriptors[1].methodName).isEqualTo("setLabelText")
        assertThat(descriptors[1].requireAll).isFalse()
    }
}
