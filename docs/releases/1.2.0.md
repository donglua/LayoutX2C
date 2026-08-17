# LayoutX2C 1.2.0

## 中文

LayoutX2C 1.2.0 是 1.1.1 之后的兼容 minor 版本，重点加入生成代码可复用的资源加载入口，让换肤框架或自定义资源系统可以接管颜色、drawable、ColorStateList、dimension 和 string 的读取。

这个版本不引入 breaking change。默认实现仍然委托给 AndroidX `ContextCompat` 和系统 `Resources`，未配置自定义 loader 时行为保持和 1.1.1 一致。

### 主要变化

- 新增 `ResourceLoader` 接口，覆盖 color、ColorStateList、drawable、dimension 和 string 资源读取。
- 新增 `ResourceLoaderRegistry`，支持运行时设置或重置全局资源加载器。
- 新增 `ResourceCompat` 公共入口，供生成代码调用当前注册的资源加载器。
- 生成代码中的资源读取从直接调用 AndroidX `ContextCompat` 切换为 `ResourceCompat`。
- 补充资源加载器 androidTest，覆盖默认实现、自定义 loader 和 reset 行为。
- 调整 codegen / KSP 测试断言，验证生成代码引用新的 `ResourceCompat` 入口。

### 接入方式

```kotlin
plugins {
    id("io.github.donglua.layoutx2c") version "1.2.0"
}
```

手动依赖配置：

```kotlin
dependencies {
    implementation("io.github.donglua.layoutx2c:runtime:1.2.0")
    ksp("io.github.donglua.layoutx2c:ksp-processor:1.2.0")
}
```

换肤框架可以在应用初始化时注册自定义资源加载器：

```kotlin
ResourceLoaderRegistry.setResourceLoader(customResourceLoader)
```

发布坐标：

- `io.github.donglua.layoutx2c:runtime:1.2.0`
- `io.github.donglua.layoutx2c:compiler-core:1.2.0`
- `io.github.donglua.layoutx2c:ksp-processor:1.2.0`
- `io.github.donglua.layoutx2c:gradle-plugin:1.2.0`
- Gradle plugin id：`io.github.donglua.layoutx2c`

### 已知边界

- `ResourceLoaderRegistry` 是进程级全局入口，测试或多主题场景应在用例结束后调用 `reset()`。
- 自定义 loader 只影响 LayoutX2C 生成代码经过 `ResourceCompat` 的资源读取，不会改变原生 `LayoutInflater` fallback 路径。
- 未支持的 View 类或不安全属性仍会 fallback 到平台 inflation。

---

## English

LayoutX2C 1.2.0 is a compatible minor release after 1.1.1. It adds a generated-code resource loading entry point so skinning frameworks or custom resource systems can intercept color, drawable, ColorStateList, dimension, and string reads.

This release does not introduce breaking changes. The default implementation still delegates to AndroidX `ContextCompat` and platform `Resources`, so behavior stays aligned with 1.1.1 unless a custom loader is registered.

### Highlights

- Added the `ResourceLoader` interface for color, ColorStateList, drawable, dimension, and string resource reads.
- Added `ResourceLoaderRegistry` to install or reset the process-wide resource loader at runtime.
- Added the public `ResourceCompat` entry point used by generated code.
- Switched generated resource reads from direct AndroidX `ContextCompat` calls to `ResourceCompat`.
- Added androidTest coverage for the default loader, custom loaders, and reset behavior.
- Updated codegen / KSP assertions to verify generated code references the new `ResourceCompat` entry point.

### Setup

```kotlin
plugins {
    id("io.github.donglua.layoutx2c") version "1.2.0"
}
```

Manual dependency setup:

```kotlin
dependencies {
    implementation("io.github.donglua.layoutx2c:runtime:1.2.0")
    ksp("io.github.donglua.layoutx2c:ksp-processor:1.2.0")
}
```

Skinning frameworks can register a custom loader during app startup:

```kotlin
ResourceLoaderRegistry.setResourceLoader(customResourceLoader)
```

Published coordinates:

- `io.github.donglua.layoutx2c:runtime:1.2.0`
- `io.github.donglua.layoutx2c:compiler-core:1.2.0`
- `io.github.donglua.layoutx2c:ksp-processor:1.2.0`
- `io.github.donglua.layoutx2c:gradle-plugin:1.2.0`
- Gradle plugin id: `io.github.donglua.layoutx2c`

### Known boundaries

- `ResourceLoaderRegistry` is process-wide, so tests or multi-theme scenarios should call `reset()` after use.
- Custom loaders affect only LayoutX2C generated code paths that read resources through `ResourceCompat`; native `LayoutInflater` fallback paths are unchanged.
- Unsupported View classes or unsafe attributes still fall back to platform inflation.
