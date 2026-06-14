# LayoutX2C 1.1.0

LayoutX2C 1.1.0 是 1.0 稳定版之后的第一个 minor release。这个版本继续保持保守生成策略：
可静态保证等价的 XML 会进入 generated path，无法保证的平台语义继续 fallback 到原生
`LayoutInflater` / DataBinding，不改变 1.0 的 public API contract。

## Highlights

- 扩展常用 View 属性支持：transform、`EditText`、view state、scrollbar / over-scroll、accessibility
  importance 等安全子集可以进入 generated path。
- 支持可静态解析的 theme drawable attrs，在保持平台 inflation 语义等价的前提下减少 fallback。
- 修复 DataBinding include / root 字段绑定路径，malformed include 会更保守地处理，避免生成无效
  binding facade code。
- BindingAdapter 支持继续收紧边界：覆盖 config generation、unsupported expression、unprefixed fast
  binding attrs 等路径。
- qualified layout resources 已进入 resource digest tracking，layout 变体和 values qualifier 会参与
  cache / regeneration 判断。
- ConstraintLayout Guideline 参数保留已补齐，并加入 demo equivalence coverage。
- 新增 Kover / Codecov CI 上报，并补充 registry、dimension、fallback path、plugin provider、report
  task 等覆盖。
- 新增 fallback performance notes，明确 full-tree extraction、batched extraction，以及不采用 partial
  `XmlPullParser` inflate / native hook 的原因。

## Compatibility

- Minimum SDK: 23
- Compile SDK: 36
- Android Gradle Plugin: 9.2.1
- Kotlin: 2.2.21
- KSP: 2.3.8
- Java toolchain: 21

## Artifacts

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

## Notes

- 这个版本不引入 breaking change。
- 复杂 DataBinding 表达式、Observable / LiveData lifecycle tracking、未声明的 BindingAdapter、
  未声明的 custom View 属性和动态 theme 语义仍由原生 DataBinding 或 fallback path 处理。
- `:demo:connectedDebugAndroidTest` 仍建议在有设备或 CI emulator 的环境中补跑，用于验证真实
  Android runtime 下的 generated-vs-inflated 等价性。

## Documentation

- Usage and support matrix: `README.md`
- Benchmark guidance: `docs/BENCHMARKS.md`
- Fallback performance notes: `docs/FALLBACK_PERFORMANCE_NOTES.md`
- Release process: `docs/RELEASE.md`
