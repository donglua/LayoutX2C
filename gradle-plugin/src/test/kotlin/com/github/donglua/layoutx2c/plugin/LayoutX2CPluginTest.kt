package com.github.donglua.layoutx2c.plugin

import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class LayoutX2CPluginTest {

    @Test
    fun `apply registers layoutX2C extension with defaults`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply(LayoutX2CPlugin::class.java)

        val extension = project.extensions.getByType(LayoutX2CExtension::class.java)
        assertThat(project.extensions.findByName(LayoutX2CPlugin.EXTENSION_NAME)).isSameInstanceAs(extension)
        assertThat(extension.warnOnFallback.get()).isTrue()
        assertThat(extension.packageName.get()).isEqualTo("com.github.donglua.layoutx2c.generated")
        assertThat(extension.maxFallbackLayouts.get()).isEqualTo(Int.MAX_VALUE)
        assertThat(extension.failOnFallbackReasons.get()).isEmpty()
        assertThat(extension.enableSyntheticAttributeSet.get()).isTrue()
    }

    @Test
    fun `apply waits for android plugin before registering android tasks`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply(LayoutX2CPlugin::class.java)

        assertThat(project.plugins.hasPlugin("com.google.devtools.ksp")).isFalse()
        assertThat(project.tasks.findByName("layoutX2CReport")).isNull()
    }
}
