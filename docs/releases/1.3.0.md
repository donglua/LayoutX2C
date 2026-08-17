# LayoutX2C 1.3.0

## 中文

LayoutX2C 1.3.0 是 1.2.0 之后的兼容 minor 版本，新增自定义 View 创建扩展点。它让生成代码在创建业务自定义 View 时，可以先交给运行时注册的 `ViewFactory`，用于接入换肤框架、自定义 View 替换或业务侧 View 创建策略。

这个版本不引入 breaking change。未注册 `ViewFactory`，或 `ViewFactory` 返回 `null` 时，生成代码继续使用默认构造函数创建 View。

### 主要变化

- 新增 `ViewFactory` 接口，允许业务侧按 XML tag name 接管自定义 View 创建。
- 新增 `ViewFactoryRegistry`，支持运行时注册和重置 View 创建工厂。
- 新增 `ViewFactoryCompat`，供生成代码统一调用自定义 View 创建 hook。
- 对 `cn.xxx`、`com.example.xxx` 等非 framework 全限定名 View，生成代码会先尝试 `ViewFactoryCompat.createView(...)`。
- 普通 `android.*`、`androidx.*`、Material 控件继续走原有直接构造路径，避免影响已验证的平台控件语义。
- README 新增运行时扩展点说明，覆盖 `ResourceLoader` 和 `ViewFactory` 的用法与边界。

### 接入方式

```kotlin
plugins {
    id("io.github.donglua.layoutx2c") version "1.3.0"
}
```

手动依赖配置：

```kotlin
dependencies {
    implementation("io.github.donglua.layoutx2c:runtime:1.3.0")
    ksp("io.github.donglua.layoutx2c:ksp-processor:1.3.0")
}
```

自定义 View 创建 hook：

```kotlin
ViewFactoryRegistry.setViewFactory { context, name, attrs ->
    when (name) {
        "com.example.widget.PriceView" -> PriceView(context, attrs)
        else -> null
    }
}
```

### 已知边界

- 当前生成路径不会重放平台 XML parser，因此传给 `ViewFactory` 的 `attrs` 可能为 `null`。
- 强依赖非空 `AttributeSet` 的第三方 inflater，应在 `attrs == null` 时返回 `null`，避免破坏默认创建路径。
- 完全等价复用强依赖 `AttributeSet` 的 inflater，还需要后续 synthetic `AttributeSet` 支持。

---

## English

LayoutX2C 1.3.0 is a compatible minor release after 1.2.0. It adds a custom View creation extension point so generated code can delegate business custom View creation to a runtime-registered `ViewFactory` before falling back to the default constructor.

This release does not introduce breaking changes. If no `ViewFactory` is registered, or if the registered factory returns `null`, generated code keeps using the default View constructor.

### Highlights

- Added the `ViewFactory` interface for custom View creation by XML tag name.
- Added `ViewFactoryRegistry` to install and reset the runtime View factory.
- Added `ViewFactoryCompat` as the generated-code entry point for custom View creation hooks.
- Generated code now tries `ViewFactoryCompat.createView(...)` for non-framework fully qualified View names such as `cn.xxx` and `com.example.xxx`.
- Platform and framework widgets such as `android.*`, `androidx.*`, and Material components keep the existing direct-constructor path.
- Updated README runtime extension documentation for `ResourceLoader` and `ViewFactory`.

### Setup

```kotlin
plugins {
    id("io.github.donglua.layoutx2c") version "1.3.0"
}
```

Manual dependency setup:

```kotlin
dependencies {
    implementation("io.github.donglua.layoutx2c:runtime:1.3.0")
    ksp("io.github.donglua.layoutx2c:ksp-processor:1.3.0")
}
```

Custom View creation hook:

```kotlin
ViewFactoryRegistry.setViewFactory { context, name, attrs ->
    when (name) {
        "com.example.widget.PriceView" -> PriceView(context, attrs)
        else -> null
    }
}
```

### Known boundaries

- The generated path does not replay the platform XML parser yet, so `attrs` passed to `ViewFactory` may be `null`.
- Third-party inflaters that require a non-null `AttributeSet` should return `null` when `attrs == null`, preserving the default creation path.
- Fully equivalent reuse of `AttributeSet`-dependent inflaters requires future synthetic `AttributeSet` support.
