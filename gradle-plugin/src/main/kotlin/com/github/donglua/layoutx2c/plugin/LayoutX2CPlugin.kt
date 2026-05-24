package com.github.donglua.layoutx2c.plugin

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

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
                    append(explicit.joinToString(","))
                    if (prefixes.isNotEmpty()) {
                        if (isNotEmpty()) append(",")
                        // 前缀匹配交给 processor 在 res 目录内做
                        append("__prefix__:")
                        append(prefixes.joinToString(":"))
                    }
                }
            }

            project.extensions.configure(KspExtension::class.java) {
                it.arg(
                    LayoutX2CArgProvider(
                        mergedRes = mergedRes,
                        packageName = extension.packageName,
                        layoutsList = layoutListProvider
                    )
                )
            }
        }
    }
}

/**
 * KSP CommandLineArgumentProvider，把 merged res 路径和配置项 lazily 传给 processor。
 * 实现 CommandLineArgumentProvider 才能让 Gradle 正确处理 task 依赖和增量编译。
 */
class LayoutX2CArgProvider(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val mergedRes: Provider<Directory>,

    @get:org.gradle.api.tasks.Input
    val packageName: org.gradle.api.provider.Property<String>,

    @get:org.gradle.api.tasks.Input
    val layoutsList: Provider<String>
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> {
        return listOf(
            "layoutx2c.resDir=${mergedRes.get().asFile.absolutePath}",
            "layoutx2c.packageName=${packageName.get()}",
            "layoutx2c.layouts=${layoutsList.get()}"
        )
    }
}
