# LayoutX2C

编译期 XML Layout 到代码生成工具，采用渐进式策略：能生成的生成，不能的 fallback，永远不让编译失败。

## 模块

- **runtime** — Android library，ViewFactory 接口 + FallbackInflater + 注册表
- **compiler-core** — 纯 JVM，XML 解析、支持度分析、代码生成
- **ksp-processor** — KSP 注解处理器，扫描 @FastLayoutConfig / @FastLayouts / @FastLayoutPattern
- **gradle-plugin** — Gradle 插件，自动配置 KSP、传递 res 路径、生成编译报告
- **demo** — 示例 App + benchmark + 生成代码查看页

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

推荐使用 `@FastLayoutConfig`，直接写 `R.layout.*`，processor 会从源码中提取 layout 名：

```kotlin
import com.github.donglua.layoutx2c.runtime.annotation.FastLayoutConfig

@FastLayoutConfig
object LayoutX2CConfig {
    val layouts = intArrayOf(
        R.layout.activity_main,
        R.layout.fragment_home
    )
}
```

也可以使用 `@FastLayouts` 显式声明 layout 名。这里传的是不带 `.xml` 后缀的字符串，不是
`R.layout.*`：

```kotlin
import com.github.donglua.layoutx2c.runtime.annotation.FastLayouts

@FastLayouts("activity_main", "fragment_home")
interface LayoutX2CConfig
```

如果一组 layout 有稳定前缀，可以使用 `@FastLayoutPattern` 批量匹配：

```kotlin
import com.github.donglua.layoutx2c.runtime.annotation.FastLayoutPattern

@FastLayoutPattern(rClass = R::class, layoutPrefix = "activity_")
interface LayoutX2CConfig
```

只有在资源目录、`R` 包名或生成包名不符合默认推导时，才需要手动传：
`layoutx2c.resDir`、`layoutx2c.rPackageName`、`layoutx2c.packageName`。

## 编译报告

Gradle 插件会注册 `layoutX2CReport` 任务，汇总 KSP 生成的 per-layout JSON：

```bash
./gradlew :app:layoutX2CReport
```

输出文件：

- `build/reports/layoutx2c/index.json`：稳定 JSON 汇总，包含 layout 支持度、节点计数、fallback 节点路径、原因和属性。
- `build/reports/layoutx2c/index.html`：可读 HTML 汇总，适合本地查看。

CI 中可以按 fallback layout 数量或指定 fallback reason 让报告任务失败：

```kotlin
layoutX2C {
    maxFallbackLayouts.set(0)
    failOnFallbackReasons.add("DATA_BINDING_WRAPPER")
}
```

## 当前支持范围

支持的 View：

- `LinearLayout`, `FrameLayout`
- `RelativeLayout`
- `ScrollView`, `HorizontalScrollView`
- `androidx.recyclerview.widget.RecyclerView`（仅容器创建，不生成 adapter / layoutManager 运行时逻辑）
- `androidx.constraintlayout.widget.ConstraintLayout`（安全子集）
- `TextView`, `Button`, `EditText`
- `ImageView`, `androidx.appcompat.widget.AppCompatImageView`
- `ViewStub`
- `View`

高频属性支持：

- 通用：`id`, `visibility`, `background`, `padding*`, `enabled`, `clickable`, `focusable`,
  `elevation`, `minWidth`, `minHeight`
- LayoutParams：`layout_width`, `layout_height`, `layout_margin*`, `layout_weight`,
  `layout_gravity`
- Text-like：`text`, `textColor`, `textSize`, `textStyle`, `gravity`
- EditText：`hint`, 常见 `inputType`
- ImageView：`src`, `scaleType`, `tint`
- ScrollView：`fillViewport`
- RelativeLayout：`layout_above`, `layout_below`, `layout_toStartOf`, `layout_toEndOf`,
  `layout_toLeftOf`, `layout_toRightOf`, `layout_alignStart`, `layout_alignEnd`,
  `layout_alignLeft`, `layout_alignRight`, `layout_alignTop`, `layout_alignBottom`,
  `layout_alignParentStart`, `layout_alignParentEnd`, `layout_alignParentLeft`,
  `layout_alignParentRight`, `layout_alignParentTop`, `layout_alignParentBottom`,
  `layout_centerInParent`, `layout_centerHorizontal`, `layout_centerVertical`
- RecyclerView：`app:layoutManager` 仅作为容器元数据接受并忽略
- ConstraintLayout：普通 start/end/top/bottom 约束、`0dp` match constraint、
  `layout_constraintHorizontal_bias`、`layout_constraintVertical_bias`
- ViewStub：`android:layout`, `android:inflatedId`

未支持的 View 会 fallback 到原生 `LayoutInflater`；不安全或无法等价生成的属性值会触发
layout/subtree fallback。

特殊标签：

- `<include>` 会在编译期解析目标 layout；未解析、循环 include 或超出递归深度时 fallback。
- `<merge>` 会作为虚拟容器处理，子节点直接注入父容器。
- `<ViewStub>` 保留运行时延迟 inflate 语义，LayoutX2C 只生成 stub 本身和 layout resource 引用。

DataBinding 的 `<layout>` 根标签会透明解包到真实 View 根节点参与分析和生成；异常
wrapper 会作为 `DATA_BINDING_WRAPPER` 单独归因。LayoutX2C 不替代原生 DataBinding
生成的 Binding class 或完整运行时语义。

对于根节点是 `<layout>` 的 XML，LayoutX2C 会额外生成 `{Name}X2CBinding`：

```kotlin
val binding = DemoDataBindingX2CBinding.inflate(inflater, parent, false)
binding.root
binding.titleText
```

`{Name}X2CBinding` 是 LayoutX2C 的 binding-like facade，提供 `inflate()`、
`bind()`、`root`、按 `android:id` 生成的字段，以及迁移期编译兼容用的 `<data>`
变量属性和 `lifecycleOwner` 字段。普通非 `<layout>` XML 不生成该类。

当前 facade 支持简单 `@{variable}` / `@{variable.property}` 表达式在
`executePendingBindings()` 中正向写回到白名单属性；`@={}` 仅对 `EditText.text`
和 `CompoundButton.checked` 这类白名单组合生成反向监听器。复杂表达式、
BindingAdapter、dirty flag、lifecycle 观察者和原生 DataBinding 的增量调度仍不由
LayoutX2C 替代；不安全布局会生成 fallback-only facade。

## Demo 覆盖

`demo` 模块的 `LayoutX2CConfig` 当前覆盖以下 layout，benchmark 和 Code Viewer 共用同一份
catalog：

- `demo_simple`, `demo_nested`, `demo_form`
- `demo_relative`
- `demo_include`（include + merge + ViewStub）
- `demo_constraint`（ConstraintLayout 安全子集）
- `demo_recycler`（RecyclerView 容器）
- `demo_fallback`（故意触发 runtime fallback）
- `demo_data_binding`, `demo_data_binding_enhanced`

## License

Apache License 2.0
