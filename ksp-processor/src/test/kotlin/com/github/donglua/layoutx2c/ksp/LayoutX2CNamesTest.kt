package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutX2CNamesTest {

    @Test
    fun `binding facade class name appends x2c binding`() {
        assertThat(LayoutX2CNames.bindingFacadeClassName("item_stock_card"))
            .isEqualTo("ItemStockCardX2CBinding")
    }
}
