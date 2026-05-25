# LayoutX2C

编译期 XML Layout 到代码生成工具，采用渐进式策略：能生成的生成，不能的 fallback，永远不让编译失败。

## 模块

- **runtime** — Android library，ViewFactory 接口 + FallbackInflater + 注册表
- **compiler-core** — 纯 JVM，XML 解析、支持度分析、代码生成
- **ksp-processor** — KSP 注解处理器，扫描 @FastLayouts 注解
- **gradle-plugin** — Gradle 插件，自动配置 KSP、传递 res 路径
- **demo** — 示例 App + benchmark

## 使用方式

推荐使用 Gradle 插件，KSP、runtime 和 processor 参数会自动配置：

```kotlin
// build.gradle.kts
plugins {
    id("com.github.donglua.layoutx2c")
}
```

也可以只接入 KSP processor，不写额外参数。processor 会从配置类所在源码路径推导 `src/main/res`，
从配置类包名推导 `R` 包，并默认生成到 `${R包}.generated`：

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("com.github.donglua.layoutx2c:runtime:<version>")
    ksp("com.github.donglua.layoutx2c:ksp-processor:<version>")
}
```

```kotlin
// 任意 Kotlin 文件
@FastLayouts(R.layout.activity_main, R.layout.fragment_home)
package com.example.app
```

只有在资源目录、`R` 包名或生成包名不符合默认推导时，才需要手动传：
`layoutx2c.resDir`、`layoutx2c.rPackageName`、`layoutx2c.packageName`。

## License

Apache License 2.0
