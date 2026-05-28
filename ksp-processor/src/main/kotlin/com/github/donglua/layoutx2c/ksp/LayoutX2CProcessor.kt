package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.codegen.BindingFacadeEligibility
import com.github.donglua.layoutx2c.codegen.BindingFacadeStatus
import com.github.donglua.layoutx2c.codegen.BindingFacadeGenerator
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
        const val OPTION_CACHE_DIR = "layoutx2c.cacheDir"

        const val ANNOTATION_FAST_LAYOUT_CONFIG = "com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig"
        const val ANNOTATION_FAST_LAYOUTS = "com.github.donglua.layoutx2c.runtime.annotation.FastLayouts"
        const val ANNOTATION_FAST_LAYOUT_PATTERN = "com.github.donglua.layoutx2c.runtime.annotation.FastLayoutPattern"
        private const val REGISTRY_DIGEST_KEY = "__registry__"
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

        val layoutNames = mutableSetOf<String>()
        val configSources = mutableListOf<LayoutX2CSource>()

        // 处理 @FastLayoutConfig：从配置对象源码里提取 R.layout.xxx。
        val configAnnotated = resolver.getSymbolsWithAnnotation(ANNOTATION_FAST_LAYOUT_CONFIG)
        val visitedConfigFiles = mutableSetOf<String>()
        for (annotated in configAnnotated) {
            val sourceFile = annotated.containingFile ?: continue
            if (!visitedConfigFiles.add(sourceFile.filePath)) continue
            configSources += LayoutX2CSource(
                file = File(sourceFile.filePath),
                packageName = annotated.packageName(),
                ksFile = sourceFile
            )

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
            annotated.containingFile?.let { sourceFile ->
                configSources += LayoutX2CSource(
                    file = File(sourceFile.filePath),
                    packageName = annotated.packageName(),
                    ksFile = sourceFile
                )
            }
        }

        // 处理 @FastLayoutPattern 注解 —— 字符串前缀，正常拿
        val patternAnnotated = resolver.getSymbolsWithAnnotation(ANNOTATION_FAST_LAYOUT_PATTERN).toList()
        for (annotated in patternAnnotated) {
            annotated.containingFile?.let { sourceFile ->
                configSources += LayoutX2CSource(
                    file = File(sourceFile.filePath),
                    packageName = annotated.packageName(),
                    ksFile = sourceFile
                )
            }
        }

        if (layoutNames.isEmpty() && patternAnnotated.isEmpty()) {
            return emptyList()
        }

        val config = resolveConfig(configSources)
        val layoutDir = File(config.resDir, "layout")

        if (!layoutDir.exists()) {
            logger.warn("Layout directory not found: $layoutDir")
            return emptyList()
        }

        val patternLayoutNames = mutableSetOf<String>()
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
                    patternLayoutNames.add(it.nameWithoutExtension)
                }
            }
        }
        layoutNames.addAll(patternLayoutNames.sorted())


        // 为每个 layout 生成代码
        val codeGen = LayoutCodeGenerator(config.packageName, config.rPackageName)
        val bindingFacadeGen = BindingFacadeGenerator(config.packageName, config.rPackageName)
        val generatedLayouts = mutableListOf<Pair<String, String>>()
        val sourceFiles = configSources.map { it.ksFile }.distinctBy { it.filePath }
        val layoutDependencies = LayoutX2CDependencyFactory.layout(sourceFiles)
        val digestStore = config.manifestFile?.let(::LayoutX2CDigestStore)

        for (layoutName in layoutNames) {
            val xmlFile = File(layoutDir, "$layoutName.xml")
            if (!xmlFile.exists()) {
                logger.warn("Layout file not found: $xmlFile")
                continue
            }

            try {
                val layoutDigest = LayoutX2CDigestCalculator.layoutDigest(
                    layoutFile = xmlFile,
                    resDir = config.resDir,
                    packageName = config.packageName,
                    rPackageName = config.rPackageName
                )
                val factoryClassName = LayoutX2CNames.factoryClassName(layoutName)
                val facadeClassName = LayoutX2CNames.facadeClassName(layoutName)
                val bindingFacadeClassName = LayoutX2CNames.bindingFacadeClassName(layoutName)

                if (digestStore?.isUnchanged(layoutName, layoutDigest) == true) {
                    val cachedOutputs = mutableListOf(
                        CachedOutput(factoryClassName, "kt"),
                        CachedOutput(facadeClassName, "kt"),
                        CachedOutput("${layoutName}_report", "json")
                    )
                    val cachedBindingFacade = digestStore.cachedFile(
                        layoutName,
                        layoutDigest,
                        bindingFacadeClassName,
                        "kt"
                    )
                    if (cachedBindingFacade.isFile) {
                        cachedOutputs += CachedOutput(bindingFacadeClassName, "kt")
                    }
                    val restored = restoreCachedLayoutOutputs(
                        digestStore = digestStore,
                        dependencies = layoutDependencies,
                        packageName = config.packageName,
                        layoutName = layoutName,
                        layoutDigest = layoutDigest,
                        outputNames = cachedOutputs
                    )
                    if (restored) {
                        generatedLayouts += layoutName to factoryClassName
                        digestStore.record(layoutName, layoutDigest)
                        logger.info("Restored unchanged layout from cache: $layoutName")
                        continue
                    }
                }

                val tree = parser.parse(xmlFile)
                val analyzed = analyzer.analyze(tree.root)
                val bindingFacadeEligibility = BindingFacadeEligibility.evaluate(tree, analyzed)
                val report = reportGenerator.generate(analyzed, layoutName, tree)
                if (bindingFacadeEligibility.status == BindingFacadeStatus.BINDING_FACADE_SKIPPED_MALFORMED_LAYOUT) {
                    val reportFile = codeGenerator.createNewFile(
                        layoutDependencies,
                        config.packageName,
                        "${layoutName}_report",
                        "json"
                    )
                    reportFile.writer().use { it.write(report) }
                    digestStore?.cacheGeneratedOutput(layoutName, layoutDigest, "${layoutName}_report", "json", report)
                    digestStore?.record(layoutName, layoutDigest)
                    logger.info("Skipped LayoutX2C factory generation for malformed DataBinding layout: $layoutName")
                    continue
                }

                val layoutResId = "R.layout.$layoutName"
                val fileSpec = codeGen.generate(analyzed, layoutName, layoutResId)
                val facadeFileSpec = codeGen.generateFacade(layoutName)

                // 写入生成的 Kotlin 文件
                val file = codeGenerator.createNewFile(
                    layoutDependencies,
                    config.packageName,
                    fileSpec.name
                )
                file.writer().use { writer ->
                    fileSpec.writeTo(writer)
                }
                digestStore?.cacheGeneratedOutput(layoutName, layoutDigest, fileSpec.name, "kt", fileSpec.toString())

                val facadeFile = codeGenerator.createNewFile(
                    layoutDependencies,
                    config.packageName,
                    facadeFileSpec.name
                )
                facadeFile.writer().use { writer ->
                    facadeFileSpec.writeTo(writer)
                }
                digestStore?.cacheGeneratedOutput(layoutName, layoutDigest, facadeFileSpec.name, "kt", facadeFileSpec.toString())

                if (bindingFacadeEligibility.shouldGenerate) {
                    val bindingFacadeFileSpec = bindingFacadeGen.generate(
                        analyzedRoot = analyzed,
                        layoutName = layoutName,
                        layoutResId = layoutResId,
                        useFastPath = bindingFacadeEligibility.useFastPath,
                        dataBindingVariables = tree.rootMetadata.dataBindingVariables
                    )
                    val bindingFacadeFile = codeGenerator.createNewFile(
                        layoutDependencies,
                        config.packageName,
                        bindingFacadeFileSpec.name
                    )
                    bindingFacadeFile.writer().use { writer ->
                        bindingFacadeFileSpec.writeTo(writer)
                    }
                    digestStore?.cacheGeneratedOutput(
                        layoutName,
                        layoutDigest,
                        bindingFacadeFileSpec.name,
                        "kt",
                        bindingFacadeFileSpec.toString()
                    )
                }

                // 写入 report
                val reportFile = codeGenerator.createNewFile(
                    layoutDependencies,
                    config.packageName,
                    "${layoutName}_report",
                    "json"
                )
                reportFile.writer().use { it.write(report) }
                digestStore?.cacheGeneratedOutput(layoutName, layoutDigest, "${layoutName}_report", "json", report)

                generatedLayouts += layoutName to fileSpec.name
                digestStore?.record(layoutName, layoutDigest)
                logger.info("Generated factory for layout: $layoutName")
            } catch (e: Exception) {
                logger.error("Failed to process layout $layoutName: ${e.message}")
            }
        }

        if (generatedLayouts.isNotEmpty()) {
            generateRegistry(
                config.packageName,
                config.rPackageName,
                generatedLayouts,
                sourceFiles,
                digestStore
            )
        }
        digestStore?.save()

        return emptyList()
    }

    private fun resolveConfig(configSources: List<LayoutX2CSource>): LayoutX2CProcessorConfig {
        val inferredPackageName = configSources.firstNotNullOfOrNull { it.packageName }
        val rPackageName = options[OPTION_R_PACKAGE] ?: inferredPackageName ?: "com.github.donglua.layoutx2c"
        val packageName = options[OPTION_PACKAGE] ?: "$rPackageName.generated"
        val resDir = options[OPTION_RES_DIR]?.let(::File)
            ?: configSources.firstNotNullOfOrNull { LayoutX2CResDirResolver.inferMainResDir(it.file) }
            ?: File("src/main/res")
        val manifestFile = options[OPTION_CACHE_DIR]?.let(::File)
            ?.resolve("layoutx2c-digests.properties")

        return LayoutX2CProcessorConfig(
            resDir = resDir,
            packageName = packageName,
            rPackageName = rPackageName,
            manifestFile = manifestFile
        )
    }

    private fun KSAnnotated.packageName(): String? {
        return when (this) {
            is KSDeclaration -> packageName.asString()
            else -> containingFile?.packageName?.asString()
        }
    }

    private fun generateRegistry(
        packageName: String,
        rPackageName: String,
        generatedLayouts: List<Pair<String, String>>,
        sourceFiles: List<KSFile>,
        digestStore: LayoutX2CDigestStore?
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

        val content = fileSpec.toString()
        val registryDigest = LayoutX2CDigestCalculator.contentDigest(
            content = content,
            packageName = packageName,
            rPackageName = rPackageName
        )

        if (digestStore?.isUnchanged(REGISTRY_DIGEST_KEY, registryDigest) == true) {
            val cachedRegistry = digestStore.cachedFile(
                REGISTRY_DIGEST_KEY,
                registryDigest,
                fileSpec.name,
                "kt"
            )
            if (cachedRegistry.isFile) {
                val file = codeGenerator.createNewFile(
                    LayoutX2CDependencyFactory.registry(sourceFiles),
                    packageName,
                    fileSpec.name
                )
                file.writer().use { writer ->
                    writer.write(cachedRegistry.readText())
                }
                digestStore.record(REGISTRY_DIGEST_KEY, registryDigest)
                logger.info("Restored unchanged registry from cache: ${fileSpec.name}")
                return
            }
        }

        val file = codeGenerator.createNewFile(
            LayoutX2CDependencyFactory.registry(sourceFiles),
            packageName,
            fileSpec.name
        )
        file.writer().use { writer ->
            writer.write(content)
        }
        digestStore?.cacheGeneratedOutput(REGISTRY_DIGEST_KEY, registryDigest, fileSpec.name, "kt", content)
        digestStore?.record(REGISTRY_DIGEST_KEY, registryDigest)
    }

    private fun LayoutX2CDigestStore.cacheGeneratedOutput(
        layoutName: String,
        layoutDigest: String,
        fileName: String,
        extensionName: String,
        content: String
    ) {
        val cacheFile = cachedFile(layoutName, layoutDigest, fileName, extensionName)
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText(content)
    }

    private fun restoreCachedLayoutOutputs(
        digestStore: LayoutX2CDigestStore,
        dependencies: Dependencies,
        packageName: String,
        layoutName: String,
        layoutDigest: String,
        outputNames: List<CachedOutput>
    ): Boolean {
        val cachedFiles = outputNames.map {
            it to digestStore.cachedFile(layoutName, layoutDigest, it.fileName, it.extensionName)
        }
        if (cachedFiles.any { !it.second.isFile }) {
            return false
        }

        for ((output, cachedFile) in cachedFiles) {
            val generated = codeGenerator.createNewFile(
                dependencies,
                packageName,
                output.fileName,
                output.extensionName
            )
            generated.writer().use { writer ->
                writer.write(cachedFile.readText())
            }
        }
        return true
    }
}

private data class CachedOutput(
    val fileName: String,
    val extensionName: String
)

private data class LayoutX2CProcessorConfig(
    val resDir: File,
    val packageName: String,
    val rPackageName: String,
    val manifestFile: File?
)

private data class LayoutX2CSource(
    val file: File,
    val packageName: String?,
    val ksFile: KSFile
)

internal object LayoutX2CDependencyFactory {

    fun layout(sourceFiles: List<KSFile>): Dependencies = fromSources(
        aggregating = false,
        sourceFiles = sourceFiles
    )

    fun registry(sourceFiles: List<KSFile>): Dependencies = fromSources(
        aggregating = true,
        sourceFiles = sourceFiles
    )

    private fun fromSources(aggregating: Boolean, sourceFiles: List<KSFile>): Dependencies {
        if (sourceFiles.isEmpty()) {
            return Dependencies.ALL_FILES
        }
        return Dependencies(aggregating, *sourceFiles.toTypedArray())
    }
}
