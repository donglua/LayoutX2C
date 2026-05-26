package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Test

class LayoutX2CResDirResolverTest {

    @Test
    fun `infers matching flavor source-set res directory from annotated source`() {
        val projectDir = createTempDirectory().toFile()
        try {
            val debugRes = projectDir.resolve("src/debug/res").apply { mkdirs() }
            val sourceFile = projectDir
                .resolve("src/debug/java/com/example/LayoutX2CConfig.kt")
                .apply {
                    parentFile.mkdirs()
                    writeText("class LayoutX2CConfig")
                }

            val inferred = LayoutX2CResDirResolver.inferMainResDir(sourceFile)

            assertThat(inferred?.canonicalFile).isEqualTo(debugRes.canonicalFile)
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `falls back to main res directory for generated source paths`() {
        val projectDir = createTempDirectory().toFile()
        try {
            val mainRes = projectDir.resolve("src/main/res").apply { mkdirs() }
            val sourceFile = projectDir
                .resolve("build/generated/ksp/debug/kotlin/com/example/LayoutX2CConfig.kt")
                .apply {
                    parentFile.mkdirs()
                    writeText("class LayoutX2CConfig")
                }

            val inferred = LayoutX2CResDirResolver.inferMainResDir(sourceFile)

            assertThat(inferred?.canonicalFile).isEqualTo(mainRes.canonicalFile)
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `falls back to nearest project main res directory for arbitrary project paths`() {
        val projectDir = createTempDirectory().toFile()
        try {
            val mainRes = projectDir.resolve("src/main/res").apply { mkdirs() }
            val sourceFile = projectDir
                .resolve("some/nested/path/LayoutX2CConfig.kt")
                .apply {
                    parentFile.mkdirs()
                    writeText("class LayoutX2CConfig")
                }

            val inferred = LayoutX2CResDirResolver.inferMainResDir(sourceFile)

            assertThat(inferred?.canonicalFile).isEqualTo(mainRes.canonicalFile)
        } finally {
            projectDir.deleteRecursively()
        }
    }
}
