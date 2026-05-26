# LayoutX2C

编译期 XML Layout 到代码生成工具，采用渐进式策略：能生成的生成，不能的 fallback，永远不让编译失败。

## 模块

- **runtime** — Android library，ViewFactory 接口 + FallbackInflater + 注册表
- **compiler-core** — 纯 JVM，XML 解析、支持度分析、代码生成
- **ksp-processor** — KSP 注解处理器，扫描 @FastLayoutConfig / @FastLayouts / @FastLayoutPattern
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

## 当前支持范围

支持的 View：

- `LinearLayout`, `FrameLayout`
- `RelativeLayout`
- `ScrollView`, `HorizontalScrollView`
- `androidx.recyclerview.widget.RecyclerView`（仅容器创建，不生成 adapter / layoutManager 运行时逻辑）
- `TextView`, `Button`, `EditText`
- `ImageView`, `androidx.appcompat.widget.AppCompatImageView`
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

未支持的 View 会 fallback 到原生 `LayoutInflater`；不安全或无法等价生成的属性值会触发
layout/subtree fallback。

## License

Apache License 2.0
