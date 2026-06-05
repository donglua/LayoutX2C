package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.resources.ResourceReference
import com.google.common.truth.Truth.assertThat
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutX2CResourceSymbolsTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `combines source res symbols with inferred AGP symbols`() {
        val projectDir = tempDir.newFolder("app")
        val resDir = projectDir.resolve("src/main/res").apply { mkdirs() }
        resDir.resolve("values").apply { mkdirs() }
            .resolve("colors.xml")
            .writeText("<resources><color name=\"local_panel\">#fff</color></resources>")
        projectDir
            .resolve("build/intermediates/runtime_symbol_list/debug/processDebugResources/R.txt")
            .apply {
                parentFile.mkdirs()
                writeText("int color dependency_panel 0x7f060001")
            }

        val symbols = LayoutX2CResourceSymbols.resolve(
            resDir = resDir,
            explicitSymbolFiles = emptyList()
        )

        assertThat(symbols.references).containsAtLeast(
            ResourceReference("color", "local_panel"),
            ResourceReference("color", "dependency_panel")
        )
    }

    @Test
    fun `combines explicit AGP symbol files`() {
        val projectDir = tempDir.newFolder("app")
        val resDir = projectDir.resolve("src/main/res").apply { mkdirs() }
        val explicitSymbols = tempDir.newFile("explicit-R.txt").apply {
            writeText("int color explicit_panel 0x7f060001")
        }

        val symbols = LayoutX2CResourceSymbols.resolve(
            resDir = resDir,
            explicitSymbolFiles = listOf(explicitSymbols)
        )

        assertThat(symbols.references)
            .contains(ResourceReference("color", "explicit_panel"))
    }

    @Test
    fun `combines R class jars from Gradle root with owner packages`() {
        val rootDir = tempDir.newFolder("root")
        rootDir.resolve("settings.gradle").writeText("include ':app'")
        val resDir = rootDir.resolve("app/src/main/res").apply { mkdirs() }
        val rJar = rootDir
            .resolve("app/build/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar")
            .apply {
                parentFile.mkdirs()
                compileRJar(this, "com.example.base", "jz_color_v4_bg_level2")
            }

        val symbols = LayoutX2CResourceSymbols.resolve(
            resDir = resDir,
            explicitSymbolFiles = emptyList()
        )

        val reference = ResourceReference("color", "jz_color_v4_bg_level2")
        assertThat(rJar.isFile).isTrue()
        assertThat(symbols.references).contains(reference)
        assertThat(symbols.owners[reference]).isEqualTo("com.example.base")
    }

    private fun compileRJar(jarFile: java.io.File, packageName: String, colorName: String) {
        val srcDir = tempDir.newFolder("r-src")
        val classesDir = tempDir.newFolder("r-classes")
        val packageDir = srcDir.resolve(packageName.replace('.', '/')).apply { mkdirs() }
        val sourceFile = packageDir.resolve("R.java")
        sourceFile.writeText(
            """
            package $packageName;
            public final class R {
                public static final class color {
                    public static int $colorName = 0;
                }
            }
            """.trimIndent()
        )

        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required for this test")
        val result = compiler.run(null, null, null, "-d", classesDir.path, sourceFile.path)
        assertThat(result).isEqualTo(0)

        JarOutputStream(jarFile.outputStream()).use { jar ->
            classesDir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { file ->
                    jar.putNextEntry(JarEntry(file.relativeTo(classesDir).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(jar) }
                    jar.closeEntry()
                }
        }
    }
}

private val java.io.File.invariantSeparatorsPath: String
    get() = path.replace(java.io.File.separatorChar, '/')
