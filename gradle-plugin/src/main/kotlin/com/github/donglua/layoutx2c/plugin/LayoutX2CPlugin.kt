package com.github.donglua.layoutx2c.plugin

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * LayoutX2C Gradle Plugin。
 *
 * 自动 apply KSP，注册 processor 依赖，传递 res 路径作为 KSP arg。
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
        const val VERSION = "0.1.0-SNAPSHOT"
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

        // 通过 AGP variant API 拿 merged res 路径，per-variant 配 KSP arg
        val androidComponents = project.extensions
            .findByType(AndroidComponentsExtension::class.java) ?: return

        androidComponents.onVariants { variant ->
            val mergedRes = variant.artifacts.get(SingleArtifact.MERGED_RES)

            // 收集 layout 名（显式 + 前缀匹配）
            val layoutListProvider = project.provider {
                val explicit = extension.layouts.getOrElse(emptyList())
                val prefixes = extension.prefixes.getOrElse(emptyList())
                buildString {
                    append(explicit.joinToString(":"))
                    if (prefixes.isNotEmpty()) {
                        if (isNotEmpty()) append(":")
                        // 前缀匹配交给 processor 在 res 目录内做
                        append("__prefix__:")
                        append(prefixes.joinToString(":"))
                    }
                }
            }

            project.extensions.configure(KspExtension::class.java) {
                it.arg(LayoutX2CProcessorOptions.RES_DIR, mergedRes.get().asFile.absolutePath)
                it.arg(LayoutX2CProcessorOptions.PACKAGE_NAME, extension.packageName.get())
                it.arg(LayoutX2CProcessorOptions.R_PACKAGE_NAME, project.androidNamespace())
                it.arg(LayoutX2CProcessorOptions.LAYOUTS, layoutListProvider.get())
            }
        }
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
    const val RES_DIR = "layoutx2c.resDir"
    const val PACKAGE_NAME = "layoutx2c.packageName"
    const val R_PACKAGE_NAME = "layoutx2c.rPackageName"
    const val LAYOUTS = "layoutx2c.layouts"
}
