# LayoutX2C

编译期 XML Layout 到代码生成工具，采用渐进式策略：能生成的生成，不能的 fallback，永远不让编译失败。

## 模块

- **runtime** — Android library，ViewFactory 接口 + FallbackInflater + 注册表
- **compiler-core** — 纯 JVM，XML 解析、支持度分析、代码生成
- **ksp-processor** — KSP 注解处理器，扫描 @FastLayouts 注解
- **gradle-plugin** — Gradle 插件，自动配置 KSP、传递 res 路径
- **demo** — 示例 App + benchmark

## 使用方式

```kotlin
// build.gradle.kts
plugins {
    id("com.github.donglua.layoutx2c")
}

// 任意 Kotlin 文件
@FastLayouts(R.layout.activity_main, R.layout.fragment_home)
package com.example.app
```

## License

Apache License 2.0
