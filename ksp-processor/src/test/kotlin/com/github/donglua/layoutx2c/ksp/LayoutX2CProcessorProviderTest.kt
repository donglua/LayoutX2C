package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import org.junit.Test

class LayoutX2CProcessorProviderTest {

    @Test
    fun `create returns layoutx2c processor wired from environment`() {
        val environment = SymbolProcessorEnvironment(
            options = mapOf("layoutx2c.packageName" to "com.example.generated"),
            kotlinVersion = KotlinVersion.CURRENT,
            codeGenerator = NoOpCodeGenerator,
            logger = NoOpLogger
        )

        val processor = LayoutX2CProcessorProvider().create(environment)

        assertThat(processor).isInstanceOf(LayoutX2CProcessor::class.java)
    }

    private object NoOpCodeGenerator : CodeGenerator {
        override fun createNewFile(
            dependencies: Dependencies,
            packageName: String,
            fileName: String,
            extensionName: String
        ): OutputStream = ByteArrayOutputStream()

        override fun createNewFileByPath(
            dependencies: Dependencies,
            path: String,
            extensionName: String
        ): OutputStream = ByteArrayOutputStream()

        override fun associate(
            sources: List<KSFile>,
            packageName: String,
            fileName: String,
            extensionName: String
        ) = Unit

        override fun associateByPath(
            sources: List<KSFile>,
            path: String,
            extensionName: String
        ) = Unit

        override fun associateWithClasses(
            classes: List<KSClassDeclaration>,
            packageName: String,
            fileName: String,
            extensionName: String
        ) = Unit

        override val generatedFile: Collection<File> = emptyList()
    }

    private object NoOpLogger : KSPLogger {
        override fun logging(message: String, symbol: KSNode?) = Unit
        override fun info(message: String, symbol: KSNode?) = Unit
        override fun warn(message: String, symbol: KSNode?) = Unit
        override fun error(message: String, symbol: KSNode?) = Unit
        override fun exception(e: Throwable) = Unit
    }
}
