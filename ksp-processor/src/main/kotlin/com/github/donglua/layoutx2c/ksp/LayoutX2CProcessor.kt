package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzerV2
import com.github.donglua.layoutx2c.codegen.BindingAdapterDescriptor
import com.github.donglua.layoutx2c.codegen.BindingFacadeEligibility
import com.github.donglua.layoutx2c.codegen.BindingFacadeStatus
import com.github.donglua.layoutx2c.codegen.BindingFacadeGeneratorV2
import com.github.donglua.layoutx2c.codegen.DefaultLayoutParamsEmitter
import com.github.donglua.layoutx2c.codegen.LayoutCodeGenerator
import com.github.donglua.layoutx2c.parser.IncludeResolver
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.github.donglua.layoutx2c.registry.CustomViewDescriptor
import com.github.donglua.layoutx2c.registry.ResourceAwareViewRegistry
import com.github.donglua.layoutx2c.report.SupportReportGenerator
import com.github.donglua.layoutx2c.resources.StaticResourceReferenceResolver
import com.github.donglua.layoutx2c.resources.StyleResourceRepository
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
 *
 * Processor 只在首轮 KSP 中运行一次。所有 layout 输出都由配置源码触发，
 * XML、values、R symbol 等非 Kotlin 输入通过 digest cache 自行追踪变化。
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
        const val OPTION_SYMBOL_FILES = "layoutx2c.symbolFiles"
        const val OPTION_ENABLE_SYNTHETIC_ATTRIBUTE_SET = "layoutx2c.enableSyntheticAttributeSet"

        const val ANNOTATION_FAST_LAYOUT_CONFIG = "com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig"
        const val ANNOTATION_FAST_LAYOUTS = "com.github.donglua.layoutx2c.runtime.annotation.FastLayouts"
        const val ANNOTATION_FAST_LAYOUT_PATTERN = "com.github.donglua.layoutx2c.runtime.annotation.FastLayoutPattern"
        private const val REGISTRY_DIGEST_KEY = "__registry__"
    }

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

            val sourceText = try {
                File(sourceFile.filePath).readText()
            } catch (e: Exception) {
                logger.warn("Cannot read LayoutX2C config source: ${sourceFile.filePath}")
                continue
            }

            configSources += LayoutX2CSource(
                file = File(sourceFile.filePath),
                packageName = annotated.packageName(),
                rPackageName = LayoutX2CConfigParser.extractRPackageName(sourceText),
                customViews = CustomViewConfigParser.extractCustomViews(sourceText)
                    .withResolvedSuperClassNames(resolver),
                bindingAdapters = BindingAdapterConfigParser.extractBindingAdapters(sourceText),
                ksFile = sourceFile
            )
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
                val sourceText = File(sourceFile.filePath).readText()
                configSources += LayoutX2CSource(
                    file = File(sourceFile.filePath),
                    packageName = annotated.packageName(),
                    rPackageName = sourceFile.rPackageName(),
                    customViews = CustomViewConfigParser.extractCustomViews(sourceText)
                        .withResolvedSuperClassNames(resolver),
                    bindingAdapters = BindingAdapterConfigParser.extractBindingAdapters(sourceText),
                    ksFile = sourceFile
                )
            }
        }

        // 处理 @FastLayoutPattern 注解 —— 字符串前缀，正常拿
        val patternAnnotated = resolver.getSymbolsWithAnnotation(ANNOTATION_FAST_LAYOUT_PATTERN).toList()
        for (annotated in patternAnnotated) {
            annotated.containingFile?.let { sourceFile ->
                val sourceText = File(sourceFile.filePath).readText()
                configSources += LayoutX2CSource(
                    file = File(sourceFile.filePath),
                    packageName = annotated.packageName(),
                    rPackageName = sourceFile.rPackageName(),
                    customViews = CustomViewConfigParser.extractCustomViews(sourceText)
                        .withResolvedSuperClassNames(resolver),
                    bindingAdapters = BindingAdapterConfigParser.extractBindingAdapters(sourceText),
                    ksFile = sourceFile
                )
            }
        }

        if (layoutNames.isEmpty() && patternAnnotated.isEmpty()) {
            return emptyList()
        }

        val config = resolveConfig(configSources)
        val layoutDirs = config.resDir.layoutResourceDirs()

        if (layoutDirs.isEmpty()) {
            logger.warn("Layout directories not found under: ${config.resDir}")
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
                patternLayoutNames.addAll(config.resDir.layoutNamesWithPrefix(prefix))
            }
        }
        layoutNames.addAll(patternLayoutNames.sorted())

        // include / ViewStub 的目标 layout 也必须生成 factory，否则上层生成代码
        // 会引用不存在的 facade。这里递归展开依赖，而不是只生成用户显式声明的入口 layout。
        layoutNames.addAll(expandLayoutNamesWithDependencies(layoutNames, config.resDir))

        // 资源符号表决定 @color/@dimen 等引用应指向当前模块 R 还是依赖包 R；
        // 同一个 stable key 也进入 digest，确保依赖资源集合变化时会重新生成。
        val resourceSymbols = LayoutX2CResourceSymbols.resolve(
            resDir = config.resDir,
            explicitSymbolFiles = config.symbolFiles
        )
        val resourceResolver = StaticResourceReferenceResolver.currentModule(
            currentPackageName = config.rPackageName,
            symbols = resourceSymbols
        )
        val viewRegistry = ResourceAwareViewRegistry(
            rPackageName = config.rPackageName,
            resourceResolver = resourceResolver,
            customViews = config.customViews
        )
        val analyzer = LayoutAnalyzerV2(
            viewRegistry = viewRegistry,
            styleResolver = StyleResourceRepository.fromResDir(config.resDir),
            bindingAdapters = config.bindingAdapters
        )

        // 为每个 layout 生成代码
        val codeGen = LayoutCodeGenerator(
            packageName = config.packageName,
            rPackageName = config.rPackageName,
            layoutParamsEmitter = DefaultLayoutParamsEmitter(
                rPackageName = config.rPackageName,
                resourceResolver = resourceResolver
            ),
            viewRegistry = viewRegistry,
            resourceResolver = resourceResolver,
            enableSyntheticAttributeSet = config.enableSyntheticAttributeSet
        )
        val bindingFacadeGen = BindingFacadeGeneratorV2(
            packageName = config.packageName,
            rPackageName = config.rPackageName,
            bindingAdapters = config.bindingAdapters
        )
        val generatedLayouts = mutableListOf<Pair<String, String>>()
        val sourceFiles = configSources.map { it.ksFile }.distinctBy { it.filePath }

        // KSP 只能对源码依赖建模；layout/resource 这类文件变化由 digestStore 负责。
        // layout 输出是 isolating，registry 聚合所有 layout，见 LayoutX2CDependencyFactory。
        val layoutDependencies = LayoutX2CDependencyFactory.layout(sourceFiles)
        val digestStore = config.manifestFile?.let(::LayoutX2CDigestStore)

        for (layoutName in layoutNames) {
            val xmlFile = config.resDir.primaryLayoutFile(layoutName)
            if (xmlFile == null) {
                logger.warn("Layout file not found for $layoutName under: ${config.resDir}")
                continue
            }

            try {
                val layoutDigest = LayoutX2CDigestCalculator.layoutDigest(
                    layoutFile = xmlFile,
                    resDir = config.resDir,
                    packageName = config.packageName,
                    rPackageName = config.rPackageName,
                    resourceSymbolsKey = resourceSymbols.stableKey(),
                    registryConfigKey = config.registryConfigKey()
                )
                val factoryClassName = LayoutX2CNames.factoryClassName(layoutName)
                val facadeClassName = LayoutX2CNames.facadeClassName(layoutName)
                val bindingFacadeClassName = LayoutX2CNames.bindingFacadeClassName(layoutName)

                // KSP 每轮仍需要 createNewFile 写出产物。digest 命中时从自有缓存
                // 恢复到 KSP output，避免重新 parse/analyze/codegen 非 Kotlin 输入。
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

                val includeResolver = IncludeResolver(
                    layoutDir = xmlFile.parentFile,
                    fallbackLayoutDirs = layoutDirs.filter { it.canonicalPath != xmlFile.parentFile.canonicalPath }
                )
                val parser = XmlLayoutParser(includeResolver = includeResolver)
                val tree = parser.parse(xmlFile)
                val analyzed = analyzer.analyze(tree.root)

                // Binding facade 只对合法 DataBinding wrapper 生成。malformed wrapper
                // 仍写 report，但跳过 factory，避免生成无法匹配原生 Binding 语义的代码。
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
                        dataBindingVariables = tree.rootMetadata.dataBindingVariables,
                        dataBindingImports = tree.rootMetadata.dataBindingImports
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
            // Registry 是 layoutId -> factory 的聚合索引，只记录本轮实际写出或恢复的 layout。
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
        val inferredRPackageName = configSources.firstNotNullOfOrNull { it.rPackageName }
        val inferredAndroidNamespace = configSources.firstNotNullOfOrNull {
            LayoutX2CRPackageResolver.inferAndroidNamespace(it.file)
        }
        val rPackageName = options[OPTION_R_PACKAGE]
            ?: inferredRPackageName
            ?: inferredAndroidNamespace
            ?: inferredPackageName
            ?: "com.github.donglua.layoutx2c"
        val packageName = options[OPTION_PACKAGE] ?: "$rPackageName.generated"
        val resDir = options[OPTION_RES_DIR]?.let(::File)
            ?: configSources.firstNotNullOfOrNull { LayoutX2CResDirResolver.inferMainResDir(it.file) }
            ?: File("src/main/res")
        val manifestFile = options[OPTION_CACHE_DIR]?.let(::File)
            ?.resolve("layoutx2c-digests.properties")
        val symbolFiles = options[OPTION_SYMBOL_FILES]
            ?.split(File.pathSeparator, ",")
            ?.mapNotNull { path -> path.trim().takeIf { it.isNotEmpty() } }
            ?.map(::File)
            ?: emptyList()
        val enableSyntheticAttributeSet = options[OPTION_ENABLE_SYNTHETIC_ATTRIBUTE_SET]
            ?.toBooleanStrictOrNull()
            ?: true

        return LayoutX2CProcessorConfig(
            resDir = resDir,
            packageName = packageName,
            rPackageName = rPackageName,
            manifestFile = manifestFile,
            symbolFiles = symbolFiles,
            customViews = configSources.flatMap { it.customViews },
            bindingAdapters = configSources.flatMap { it.bindingAdapters },
            enableSyntheticAttributeSet = enableSyntheticAttributeSet
        )
    }

    private fun KSAnnotated.packageName(): String? {
        return when (this) {
            is KSDeclaration -> packageName.asString()
            else -> containingFile?.packageName?.asString()
        }
    }

    private fun KSFile.rPackageName(): String? {
        return try {
            LayoutX2CConfigParser.extractRPackageName(File(filePath).readText())
        } catch (e: Exception) {
            logger.warn("Cannot read LayoutX2C config source: $filePath")
            null
        }
    }

    private fun List<CustomViewDescriptor>.withResolvedSuperClassNames(
        resolver: Resolver
    ): List<CustomViewDescriptor> {
        return map { descriptor ->
            descriptor.copy(
                superClassNames = descriptor.superClassNames +
                    resolveSuperClassNames(resolver, descriptor.viewClassName)
            )
        }
    }

    private fun resolveSuperClassNames(
        resolver: Resolver,
        viewClassName: String
    ): Set<String> {
        val declaration = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString(viewClassName)
        ) ?: return emptySet()
        val superClassNames = linkedSetOf<String>()

        fun visit(current: KSClassDeclaration) {
            current.superTypes.forEach { superTypeRef ->
                val superDeclaration = runCatching {
                    superTypeRef.resolve().declaration as? KSClassDeclaration
                }.getOrNull() ?: return@forEach
                val superClassName = superDeclaration.qualifiedName?.asString() ?: return@forEach
                if (superClassNames.add(superClassName)) {
                    visit(superDeclaration)
                }
            }
        }

        visit(declaration)
        return superClassNames
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

        // Registry 内容只取决于生成出的 layout 列表和包名；命中时同样恢复到 KSP output。
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
    val manifestFile: File?,
    val symbolFiles: List<File>,
    val customViews: List<CustomViewDescriptor>,
    val bindingAdapters: List<BindingAdapterDescriptor>,
    val enableSyntheticAttributeSet: Boolean
)

private fun LayoutX2CProcessorConfig.registryConfigKey(): String {
    val customViewKey = customViews
        .sortedBy { it.viewClassName }
        .joinToString(separator = "|") { descriptor ->
            val attrs = descriptor.attributes
                .sortedBy { it.name }
                .joinToString(separator = ",") { attr -> "${attr.name}:${attr.kind}" }
            val supers = descriptor.superClassNames.sorted().joinToString(separator = ",")
            "view=${descriptor.viewClassName};attrs=$attrs;supers=$supers"
        }
    val bindingAdapterKey = bindingAdapters
        .sortedWith(compareBy<BindingAdapterDescriptor> { it.methodClassName }.thenBy { it.methodName })
        .joinToString(separator = "|") { descriptor ->
            "adapter=${descriptor.methodClassName}.${descriptor.methodName};attrs=${descriptor.attrs.joinToString(",")};requireAll=${descriptor.requireAll}"
        }
    return "customViews=$customViewKey\nbindingAdapters=$bindingAdapterKey\n" +
        "enableSyntheticAttributeSet=$enableSyntheticAttributeSet"
}

/**
 * Returns the explicit layout set plus layouts referenced by include/ViewStub
 * dependencies. Generated code uses facade calls across those boundaries, so
 * every reachable dependency needs an output even when the user only annotated
 * the top-level entry layout.
 */
internal fun expandLayoutNamesWithDependencies(layoutNames: Set<String>, resDir: File): Set<String> {
    val expanded = layoutNames.toMutableSet()
    val pending = ArrayDeque(layoutNames.sorted())
    while (pending.isNotEmpty()) {
        val layoutName = pending.removeFirst()
        val layoutFile = resDir.resolve("layout/$layoutName.xml")
        if (!layoutFile.isFile) continue

        LayoutDependencyScanner.scanDependencies(layoutFile, resDir)
            .map { it.nameWithoutExtension }
            .sorted()
            .forEach { dependencyName ->
                if (expanded.add(dependencyName)) {
                    pending.addLast(dependencyName)
                }
            }
    }
    return expanded
}

private data class LayoutX2CSource(
    val file: File,
    val packageName: String?,
    val rPackageName: String?,
    val customViews: List<CustomViewDescriptor>,
    val bindingAdapters: List<BindingAdapterDescriptor>,
    val ksFile: KSFile
)

internal object LayoutX2CDependencyFactory {

    /**
     * Per-layout files are isolating with respect to annotated config sources;
     * resource-level invalidation is handled by LayoutX2CDigestStore.
     */
    fun layout(sourceFiles: List<KSFile>): Dependencies = fromSources(
        aggregating = false,
        sourceFiles = sourceFiles
    )

    /**
     * The registry aggregates every generated layout mapping, so a config source
     * change can alter the whole file even when individual layout factories are cached.
     */
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
