package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.createTempDirectory
import org.junit.Test

class LayoutX2CRPackageResolverTest {

    @Test
    fun `infers android namespace when source package differs from R package`() {
        val projectDir = createTempDirectory().toFile()
        try {
            projectDir.resolve("build.gradle").writeText(
                """
                plugins {
                    id 'com.android.library'
                }

                android {
                    namespace = 'com.example.feature.home'
                }
                """.trimIndent()
            )
            val sourceFile = projectDir
                .resolve("src/main/java/com/example/legacy/home/LayoutX2CConfig.kt")
                .apply {
                    parentFile.mkdirs()
                    writeText("package com.example.legacy.home")
                }

            val inferred = LayoutX2CRPackageResolver.inferAndroidNamespace(sourceFile)

            assertThat(inferred).isEqualTo("com.example.feature.home")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `infers android namespace from kotlin build script`() {
        val projectDir = createTempDirectory().toFile()
        try {
            projectDir.resolve("build.gradle.kts").writeText(
                """
                plugins {
                    id("com.android.library")
                }

                android {
                    namespace = "com.example.feature"
                }
                """.trimIndent()
            )
            val sourceFile = projectDir
                .resolve("src/main/kotlin/com/example/legacy/LayoutX2CConfig.kt")
                .apply {
                    parentFile.mkdirs()
                    writeText("package com.example.legacy")
                }

            val inferred = LayoutX2CRPackageResolver.inferAndroidNamespace(sourceFile)

            assertThat(inferred).isEqualTo("com.example.feature")
        } finally {
            projectDir.deleteRecursively()
        }
    }
}
