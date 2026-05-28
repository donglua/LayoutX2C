package com.github.donglua.layoutx2c.plugin

import com.android.build.api.variant.AndroidComponentsExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * LayoutX2C Gradle Plugin。
 *
 * 自动 apply KSP，注册 processor 依赖，传递生成包名和 R 包名作为 KSP arg。
 * 用户只需要：
 * ```
 * plugins {
 *     id("com.github.donglua.layoutx2c")
 * }
 * ```
 */
class LayoutX2CPlugin : Plugin<Project> {

    companion object {
        const val EXTENSION_NAME = "layoutX2C"
        const val GROUP = "com.github.donglua.layoutx2c"
        const val VERSION = "0.2.0"
    }

    override fun apply(project: Project) {
        // 1. 注册 DSL 扩展
        val extension = project.extensions.create(
            EXTENSION_NAME,
            LayoutX2CExtension::class.java
        )
        extension.warnOnFallback.convention(true)
        extension.packageName.convention("com.github.donglua.layoutx2c.generated")

        // 2. 等 Android plugin 就位
        project.plugins.withId("com.android.application") {
            setupForAndroid(project, extension)
        }
        project.plugins.withId("com.android.library") {
            setupForAndroid(project, extension)
        }
    }

    private fun setupForAndroid(project: Project, extension: LayoutX2CExtension) {
        // 自动 apply KSP plugin
        project.pluginManager.apply("com.google.devtools.ksp")

        // 自动添加 processor 依赖
        project.dependencies.add("ksp", "$GROUP:ksp-processor:$VERSION")

        // 自动添加 runtime 依赖
        project.dependencies.add("implementation", "$GROUP:runtime:$VERSION")

        // resDir 由 processor 基于注解源码路径推断，保留 flavor/source-set 语义。
        val androidComponents = project.extensions
            .findByType(AndroidComponentsExtension::class.java) ?: return

        androidComponents.onVariants { variant ->
            project.extensions.configure(KspExtension::class.java) { ksp ->
                ksp.addArg(LayoutX2CProcessorOptions.PACKAGE_NAME, extension.packageName.get())
                ksp.addArg(LayoutX2CProcessorOptions.R_PACKAGE_NAME, project.androidNamespace())
                ksp.arg(
                    LayoutX2CResourceArgumentProvider(
                        trackedResources = project.files(
                            project.fileTree(project.layout.projectDirectory.dir("src")).apply {
                                include("**/res/layout/*.xml")
                                include("**/res/values/*.xml")
                            }
                        ),
                        cacheDir = project.layout.buildDirectory
                            .dir("layoutx2c/ksp")
                            .get()
                            .asFile
                            .absolutePath
                    )
                )
            }
        }
    }

    private fun KspExtension.addArg(key: String, value: String) {
        javaClass.methods
            .first { it.name == "arg" && it.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java)) }
            .invoke(this, key, value)
    }

    private fun Project.androidNamespace(): String {
        val androidExtension = extensions.findByName("android")
        return androidExtension
            ?.javaClass
            ?.methods
            ?.firstOrNull { it.name == "getNamespace" && it.parameterCount == 0 }
            ?.invoke(androidExtension) as? String
            ?: group.toString()
    }
}

private object LayoutX2CProcessorOptions {
    const val PACKAGE_NAME = "layoutx2c.packageName"
    const val R_PACKAGE_NAME = "layoutx2c.rPackageName"
    const val CACHE_DIR = "layoutx2c.cacheDir"
}

private class LayoutX2CResourceArgumentProvider(
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val trackedResources: ConfigurableFileCollection,
    private val cacheDir: String
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> {
        return listOf("${LayoutX2CProcessorOptions.CACHE_DIR}=$cacheDir")
    }
}
