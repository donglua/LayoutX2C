package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class GeneratedLayoutRegistryLoaderTest {

    @Before
    fun resetProviders() {
        FirstTestRegistry.registerCount = 0
        SecondTestRegistry.registerCount = 0
    }

    @Test
    fun `load skips a broken provider and registers every valid provider`() {
        val loadedCount = GeneratedLayoutRegistryLoader.load(javaClass.classLoader!!)

        assertThat(loadedCount).isEqualTo(2)
        assertThat(FirstTestRegistry.registerCount).isEqualTo(1)
        assertThat(SecondTestRegistry.registerCount).isEqualTo(1)
    }
}

class FirstTestRegistry : GeneratedLayoutRegistry {
    override fun register() {
        registerCount++
    }

    companion object {
        var registerCount: Int = 0
    }
}

class SecondTestRegistry : GeneratedLayoutRegistry {
    override fun register() {
        registerCount++
    }

    companion object {
        var registerCount: Int = 0
    }
}
