# LayoutX2C 1.1.0

## 中文

LayoutX2C 1.1.0 是 1.0 稳定版之后的第一个 minor 版本，重点扩展安全可生成属性、修复 DataBinding / BindingAdapter 边界问题，并增强资源追踪和发布验证。

这个版本继续保持 1.0 的保守契约：可静态确认等价的 XML 会进入生成代码，不支持或无法确认等价的平台语义继续走原生 `LayoutInflater`、原生 DataBinding 或 fallback path。1.1.0 不引入 breaking change。

### 主要变化

- 扩展常用 View 属性支持，包括 transform、`EditText` 常用属性、view state、scrollbar / over-scroll 和 accessibility importance 等安全子集。
- 支持可静态解析的 theme drawable attrs，在保持平台 inflation 语义等价的前提下减少 fallback。
- 修复 malformed DataBinding include 处理，避免生成无效 binding facade code。
- 修复 DataBinding include / root 字段绑定路径，保持 include root ID 和 binding 字段语义一致。
- 补齐 BindingAdapter config generation、unsupported expression 和 unprefixed fast binding attrs 等路径覆盖。
- qualified layout resources 已进入 resource digest tracking，layout 变体和 values qualifier 会参与 cache / regeneration 判断。
- ConstraintLayout Guideline 参数保留已补齐，并加入 demo equivalence coverage。
- 新增 Kover / Codecov CI 上报，补充 registry、dimension、fallback path、plugin provider 和 report task 等覆盖。
- 新增 fallback performance notes，明确 full-tree extraction、batched extraction，以及不采用 partial `XmlPullParser` inflate / native hook 的原因。

### 从 1.0.0 到 1.1.0

- 新增 transform / EditText / view state / theme drawable attrs 的安全生成能力。
- 改进 DataBinding include 和 BindingAdapter 的生成边界，遇到不安全结构时更保守地 fallback。
- 改进 layout resource digest，避免 qualified resources 或 values 变体变化时复用过期生成结果。
- 补充 generated-vs-inflated 等价性测试，覆盖 TextView、ImageView、RelativeLayout、EditText、view state 和 Guideline 场景。
- 强化 CI 覆盖率上报和报告任务测试，便于持续观察 fallback 和生成质量。
- 记录 fallback 性能边界，避免重新引入不安全的 runtime partial inflate 方案。

### 兼容信息

- Minimum SDK：23
- Compile SDK：36
- Target SDK：36
- Android Gradle Plugin：9.2.1
- Kotlin：2.2.21
- KSP：2.3.8
- Java source / target compatibility：11
- Release build toolchain：JDK 21

### 接入方式

```kotlin
plugins {
    id("io.github.donglua.layoutx2c") version "1.1.0"
}
```

手动依赖配置：

```kotlin
dependencies {
    implementation("io.github.donglua.layoutx2c:runtime:1.1.0")
    ksp("io.github.donglua.layoutx2c:ksp-processor:1.1.0")
}
```

发布坐标：

- `io.github.donglua.layoutx2c:runtime:1.1.0`
- `io.github.donglua.layoutx2c:compiler-core:1.1.0`
- `io.github.donglua.layoutx2c:ksp-processor:1.1.0`
- `io.github.donglua.layoutx2c:gradle-plugin:1.1.0`
- Gradle plugin id：`io.github.donglua.layoutx2c`

### 已知边界

- 未支持的 View 类或不安全属性仍会 fallback 到平台 inflation。
- 动态 theme 语义不会默认编译成常量，`?attr/` 等运行时主题能力仍保持保守处理。
- DataBinding 支持仍是有边界的。LayoutX2C 不替代完整原生 DataBinding runtime、复杂表达式引擎、Observable / LiveData 订阅模型或 lifecycle 分发。
- 自定义 View 和 BindingAdapter 只有显式加入白名单后才会进入生成代码。
- `:demo:connectedDebugAndroidTest` 建议在有设备或 CI emulator 的环境中补跑，用于验证真实 Android runtime 下的 generated-vs-inflated 等价性。

---

## English

LayoutX2C 1.1.0 is the first minor release after the 1.0 stable release. It expands safe attribute generation, fixes DataBinding / BindingAdapter boundary cases, and improves resource tracking and release verification.

This release keeps the 1.0 conservative contract: XML that can be statically proven equivalent is generated into code, while unsupported or unsafe platform semantics continue to use native `LayoutInflater`, native DataBinding, or the fallback path. 1.1.0 does not introduce breaking changes.

### Highlights

- Expanded common View attribute support, including safe subsets for transform, common `EditText` attributes, view state, scrollbar / over-scroll, and accessibility importance.
- Added support for statically resolvable theme drawable attrs when the generated path can preserve platform inflation semantics.
- Fixed malformed DataBinding include handling so invalid structures do not generate invalid binding facade code.
- Fixed DataBinding include / root field binding paths so include root IDs and binding fields stay consistent.
- Added coverage for BindingAdapter config generation, unsupported expressions, and unprefixed fast binding attrs.
- Added qualified layout resources to resource digest tracking, so layout variants and values qualifiers participate in cache / regeneration decisions.
- Preserved ConstraintLayout Guideline params in generated code and added demo equivalence coverage.
- Added Kover / Codecov CI reporting and expanded coverage for registry, dimension, fallback path, plugin provider, and report task behavior.
- Added fallback performance notes covering full-tree extraction, batched extraction, and why partial `XmlPullParser` inflate / native hooks are not used.

### Changes since 1.0.0

- Added safe generation support for transform / EditText / view state / theme drawable attrs.
- Improved DataBinding include and BindingAdapter generation boundaries, with more conservative fallback for unsafe structures.
- Improved layout resource digests to avoid reusing stale generated outputs when qualified resources or values variants change.
- Expanded generated-vs-inflated equivalence tests for TextView, ImageView, RelativeLayout, EditText, view state, and Guideline cases.
- Hardened CI coverage reporting and report task tests for ongoing fallback and generation quality checks.
- Documented fallback performance boundaries to avoid reintroducing unsafe runtime partial-inflate approaches.

### Compatibility

- Minimum SDK: 23
- Compile SDK: 36
- Target SDK: 36
- Android Gradle Plugin: 9.2.1
- Kotlin: 2.2.21
- KSP: 2.3.8
- Java source / target compatibility: 11
- Release build toolchain: JDK 21

### Setup

```kotlin
plugins {
    id("io.github.donglua.layoutx2c") version "1.1.0"
}
```

Manual dependency setup:

```kotlin
dependencies {
    implementation("io.github.donglua.layoutx2c:runtime:1.1.0")
    ksp("io.github.donglua.layoutx2c:ksp-processor:1.1.0")
}
```

Published coordinates:

- `io.github.donglua.layoutx2c:runtime:1.1.0`
- `io.github.donglua.layoutx2c:compiler-core:1.1.0`
- `io.github.donglua.layoutx2c:ksp-processor:1.1.0`
- `io.github.donglua.layoutx2c:gradle-plugin:1.1.0`
- Gradle plugin id: `io.github.donglua.layoutx2c`

### Known boundaries

- Unsupported View classes or unsafe attributes still fall back to platform inflation.
- Dynamic theme semantics are not compiled into constants by default. Runtime theme features such as `?attr/` remain conservative.
- DataBinding support is intentionally scoped. LayoutX2C does not replace the full native DataBinding runtime, complex expression engine, Observable / LiveData subscription model, or lifecycle dispatch.
- Custom views and BindingAdapters only enter generated code when explicitly whitelisted.
- `:demo:connectedDebugAndroidTest` should still be run in an environment with a device or CI emulator to verify generated-vs-inflated equivalence on a real Android runtime.
