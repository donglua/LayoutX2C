package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.codegen.LayoutCodeGenerator
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.github.donglua.layoutx2c.report.SupportReportGenerator
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import java.io.File

/**
 * KSP Processor：扫描 @FastLayouts / @FastLayoutPattern 注解，
 * 解析对应的 layout XML，生成 LayoutFactory 实现类。
 */
class LayoutX2CProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    companion object {
        const val OPTION_RES_DIR = "layoutx2c.resDir"
        const val OPTION_PACKAGE = "layoutx2c.packageName"
        const val OPTION_R_PACKAGE = "layoutx2c.rPackageName"

        const val ANNOTATION_FAST_LAYOUT_CONFIG = "com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig"
        const val ANNOTATION_FAST_LAYOUTS = "com.github.donglua.layoutx2c.runtime.annotation.FastLayouts"
        const val ANNOTATION_FAST_LAYOUT_PATTERN = "com.github.donglua.layoutx2c.runtime.annotation.FastLayoutPattern"
    }

    private val parser = XmlLayoutParser()
    private val analyzer = LayoutAnalyzer()
    private val reportGenerator = SupportReportGenerator()
    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) {
            return emptyList()
        }
        processed = true

        val resDir = options[OPTION_RES_DIR]
        if (resDir == null) {
            logger.error("Missing KSP option: $OPTION_RES_DIR. " +
                "Make sure the LayoutX2C Gradle plugin is applied.")
            return emptyList()
        }

        val packageName = options[OPTION_PACKAGE] ?: "com.github.donglua.layoutx2c.generated"
        val rPackageName = options[OPTION_R_PACKAGE] ?: packageName
        val layoutDir = File(resDir, "layout")

        if (!layoutDir.exists()) {
            logger.warn("Layout directory not found: $layoutDir")
            return emptyList()
        }

        val layoutNames = mutableSetOf<String>()

        // 处理 @FastLayoutConfig：从配置对象源码里提取 R.layout.xxx。
        val configAnnotated = resolver.getSymbolsWithAnnotation(ANNOTATION_FAST_LAYOUT_CONFIG)
        val visitedConfigFiles = mutableSetOf<String>()
        for (annotated in configAnnotated) {
            val sourceFile = annotated.containingFile ?: continue
            if (!visitedConfigFiles.add(sourceFile.filePath)) continue

            val sourceText = try {
                File(sourceFile.filePath).readText()
            } catch (e: Exception) {
                logger.warn("Cannot read LayoutX2C config source: ${sourceFile.filePath}")
                continue
            }

            layoutNames.addAll(LayoutX2CConfigParser.extractLayoutNames(sourceText))
        }

        // 处理 @FastLayouts 注解
        val fastLayoutsAnnotated = resolver.getSymbolsWithAnnotation(ANNOTATION_FAST_LAYOUTS)
        for (annotated in fastLayoutsAnnotated) {
            val annotation = annotated.annotations.first {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_FAST_LAYOUTS
            }
            val layouts = annotation.arguments.firstOrNull { it.name?.asString() == "layouts" }
                ?.value as? List<*>

            layouts?.filterIsInstance<String>()?.forEach { layoutName ->
                if (layoutName.isNotBlank()) {
                    layoutNames.add(layoutName.trim())
                }
            }
        }

        // 处理 @FastLayoutPattern 注解 —— 字符串前缀，正常拿
        val patternAnnotated = resolver.getSymbolsWithAnnotation(ANNOTATION_FAST_LAYOUT_PATTERN)
        for (annotated in patternAnnotated) {
            val annotation = annotated.annotations.first {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_FAST_LAYOUT_PATTERN
            }
            val prefix = annotation.arguments
                .firstOrNull { it.name?.asString() == "layoutPrefix" }
                ?.value as? String ?: ""

            if (prefix.isNotEmpty()) {
                layoutDir.listFiles()?.filter {
                    it.isFile && it.extension == "xml" && it.nameWithoutExtension.startsWith(prefix)
                }?.forEach {
                    layoutNames.add(it.nameWithoutExtension)
                }
            }
        }

        // 为每个 layout 生成代码
        val codeGen = LayoutCodeGenerator(packageName, rPackageName)
        val generatedLayouts = mutableListOf<Pair<String, String>>()

        for (layoutName in layoutNames) {
            val xmlFile = File(layoutDir, "$layoutName.xml")
            if (!xmlFile.exists()) {
                logger.warn("Layout file not found: $xmlFile")
                continue
            }

            try {
                val tree = parser.parse(xmlFile)
                val analyzed = analyzer.analyze(tree.root)
                val layoutResId = "R.layout.$layoutName"
                val fileSpec = codeGen.generate(analyzed, layoutName, layoutResId)

                // 写入生成的 Kotlin 文件
                val file = codeGenerator.createNewFile(
                    Dependencies(false),
                    packageName,
                    fileSpec.name
                )
                file.writer().use { writer ->
                    fileSpec.writeTo(writer)
                }

                // 写入 report
                val report = reportGenerator.generate(analyzed, layoutName)
                val reportFile = codeGenerator.createNewFile(
                    Dependencies(false),
                    packageName,
                    "${layoutName}_report",
                    "json"
                )
                reportFile.writer().use { it.write(report) }

                generatedLayouts += layoutName to fileSpec.name
                logger.info("Generated factory for layout: $layoutName")
            } catch (e: Exception) {
                logger.error("Failed to process layout $layoutName: ${e.message}")
            }
        }

        if (generatedLayouts.isNotEmpty()) {
            generateRegistry(packageName, rPackageName, generatedLayouts)
        }

        return emptyList()
    }

    private fun generateRegistry(
        packageName: String,
        rPackageName: String,
        generatedLayouts: List<Pair<String, String>>
    ) {
        val registerFun = FunSpec.builder("register")
            .apply {
                for ((layoutName, factoryClassName) in generatedLayouts) {
                    addStatement(
                        "%T.register(R.layout.%L, %T())",
                        ClassName("com.github.donglua.layoutx2c.runtime", "LayoutX2CRegistry"),
                        layoutName,
                        ClassName(packageName, factoryClassName)
                    )
                }
            }
            .build()

        val fileSpec = FileSpec.builder(packageName, "LayoutX2CGenerated")
            .addImport(rPackageName, "R")
            .addType(
                TypeSpec.objectBuilder("LayoutX2CGenerated")
                    .addFunction(registerFun)
                    .build()
            )
            .build()

        val file = codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            fileSpec.name
        )
        file.writer().use { writer ->
            fileSpec.writeTo(writer)
        }
    }
}
