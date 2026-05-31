# LayoutX2C Roadmap

> 编译期 XML Layout → Kotlin 代码生成。核心策略是「能等价生成的生成，不能等价生成的 fallback」。生成错误比 fallback 更危险，因此每个阶段都以语义正确性优先。

---

## 当前状态（v0.4.0 P0 已落地）

已完成的基础骨架：

- XML 解析器 → LayoutTree AST
- 支持度分析器（FULL / PARTIAL / FALLBACK 三级）
- KotlinPoet 代码生成
- KSP 注解处理器（@FastLayouts / @FastLayoutPattern / @FastLayoutConfig）
- Gradle 插件（自动 apply KSP、传递 res 路径）
- Runtime：LayoutFactory 接口 + FallbackInflater + Registry 自动注册
- Demo App + BenchmarkActivity
- 代码生成已拆分为 `ViewEmitter` / `AttrEmitter` / `LayoutParamsEmitter`
- fallback 子树定位支持完整 child path，并对非法路径输出可诊断错误
- fallback 子树已优先走 `XmlPullParser` seek + partial inflate；`fragment` 和无法安全局部生成的特殊节点保留整棵 layout inflate 的兼容路径
- View / 属性支持面已迁移到 `DefaultViewRegistry` 和注册式 `ViewHandler` / `AttributeHandler`
- KSP 生成输出已显式声明依赖：per-layout factory 关联触发生成的源码，聚合 Registry 关联配置源码
- 生成代码提供 direct X2C inflate facade，demo 可直接走生成入口做 benchmark
- Gradle 插件已传入 `layoutx2c.cacheDir`，KSP 侧已落地保守 digest cache：digest 未变时可从 cache 恢复 per-layout factory、facade 和 report
- DataBinding `<layout>` 根标签已透明解包到真实 View 根节点；异常 wrapper 仍输出 `DATA_BINDING_WRAPPER`，避免把绑定包装层误归因为普通不支持 View
- DataBinding `<layout>` 布局会生成独立 `{Name}X2CBinding`，提供 `inflate()`、`bind()`、`root`、ID 字段和迁移期编译兼容成员；表达式和 DataBinding runtime 语义不替代
- `include` 已支持递归解析被引用 layout，并处理 include 标签上的 LayoutParams、`android:id` 和 `android:visibility` 覆盖语义
- `merge` 已作为虚拟容器处理，子节点会按父 ViewGroup 的 LayoutParams 语义直接注入父级
- `ViewStub` 已支持作为普通 View 创建，并保留 `android:layout` / `android:inflatedId` 等延迟 inflate 关键语义
- KSP 侧已接入 `IncludeResolver`，可以在 layout 目录内解析 include 依赖；循环 include 和超过深度限制的 include 会保守 fallback

支持的 View：LinearLayout, FrameLayout, RelativeLayout, ScrollView, HorizontalScrollView,
RecyclerView（仅容器）, TextView, Button, EditText, ImageView, View, ViewStub
支持的属性：尺寸、padding、margin、gravity、visibility、text、id、orientation、weight、
background、textColor、textSize、textStyle、hint、inputType、src、scaleType、tint、fillViewport、
RelativeLayout 常见规则等。

当前限制：

- `fragment`、无法解析的 include、循环 include、超出递归深度限制的 include，以及无法等价生成的特殊语义仍会保守 fallback。
- KSP 处理器仍会生成聚合 Registry；Gradle task 资源输入声明、单 layout 改动的端到端行为和 Registry 内容 digest/cache 已有 functional test 覆盖。
- 编译报告已有节点级 JSON，但尚未形成可发布的 HTML/JSON 汇总和 top fallback reason 汇总。

---

## 里程碑

### v0.2.0 — 正确性地基与核心 View 覆盖

目标：先保证「生成代码与原始 inflate 语义等价」，再扩展高频 View 和属性。v0.2 不追求完整覆盖率，优先减少明显可生成布局的 fallback。

**P0：fallback 语义修正**

- 明确 fallback 粒度：layout 级、子树级、节点级各自的输入和输出约束。
- 修复嵌套 fallback 子树定位，避免仅依赖 root child index。
- fallback 子树必须保留父级 `LayoutParams` 语义。
- 对无法安全局部 fallback 的节点，整棵 layout 回退到原始 `LayoutInflater`。

**P0：代码生成分层**

- 拆分 `ViewEmitter`、`AttrEmitter`、`LayoutParamsEmitter`。
- 每个 parent ViewGroup 单独负责自己的 `LayoutParams` 生成。
- 属性支持必须有单元测试和最小 demo 验证。

**新增 View 支持：**

- ImageView / AppCompatImageView（src, scaleType, tint）
- Button / AppCompatButton
- EditText / AppCompatEditText
- ScrollView / HorizontalScrollView
- RecyclerView（仅容器创建，不含 adapter 逻辑）
- RelativeLayout（常见规则属性，见下方范围）

**新增属性支持：**

- background（@color/, @drawable/, #hex）
- textSize, textColor, textStyle, fontFamily
- src, scaleType（ImageView）
- enabled, clickable, focusable
- elevation, translationZ
- minWidth, minHeight, maxWidth, maxHeight
- @dimen/ 资源引用

**RelativeLayout 范围：**

- 支持 `layout_above`、`layout_below`、`layout_toStartOf`、`layout_toEndOf`、`layout_alignStart`、`layout_alignEnd`。
- 支持 `layout_toLeftOf`、`layout_toRightOf`、`layout_alignLeft`、`layout_alignRight`、`layout_alignTop`、`layout_alignBottom`。
- 支持 `layout_alignParentStart`、`layout_alignParentEnd`、`layout_alignParentLeft`、`layout_alignParentRight`、`layout_alignParentTop`、`layout_alignParentBottom`。
- 支持 `layout_centerInParent`、`layout_centerHorizontal`、`layout_centerVertical`。
- 暂不支持 `layout_alignBaseline`、`layout_alignWithParentIfMissing` 等边角语义；遇到时 fallback。
- `left/right` 与 `start/end` 分开处理，避免 RTL 语义漂移。

**架构改进：**

- 属性处理器插件化：每个 View 类型注册自己的 AttributeHandler（已落地默认 Registry）
- 引入 ViewRegistry 配置文件，支持用户自定义 View 映射

**验收口径：**

- demo 中新增 RelativeLayout、RecyclerView、fallback、ImageView、Button、EditText 样例。
- compiler-core 覆盖新增属性、LayoutParams 生成和默认 ViewRegistry 行为。
- demo 覆盖 generated inflate 等价性最小验收。

---

### v0.3.0 — 编译报告与保守增量

目标：让项目可以定位 fallback 原因、判断单个 layout 的生成收益，并为增量编译建立正确的依赖模型。

**编译诊断：**

- 报告每个 layout 的 FULL / PARTIAL / FALLBACK 结果、节点级支持度和 fallback 原因。
- 支持按 layout、节点、属性三级定位问题，避免只给一个总覆盖率数字。
- 汇总 top fallback reason，帮助开发者优先处理收益最高的 unsupported 语义。

**增量编译：**

- Gradle 插件必须把 `src/**/res/layout/*.xml` 和 `src/**/res/values/*.xml` 声明为 KSP task 输入，避免 processor 直接读 XML 但 Gradle/KSP 不感知资源变更（已落地并由 functional test 覆盖）。
- 插件路径传入 `layoutx2c.cacheDir` 后启用保守 digest cache（已落地）；裸 KSP 不传 cacheDir 时继续全量生成，避免生成目录被清理后跳过输出。
- `LayoutDigest` v1 包含 layout 文件、values XML、生成包名、R 包名和 digest schema version（已落地）；values 先作为 coarse input，保证正确性优先。
- digest 未变时从 cache 恢复 per-layout factory、facade 和 report 到 KSP 输出目录；digest 变化时重新 parse/analyze/codegen 并更新 cache（已落地，单 layout 改动只更新对应 digest / factory 的 functional test 已覆盖）。
- Registry 仍是 aggregating 输出：只引用本轮成功生成或成功恢复的 layout factory；已通过内容 digest/cache 避免 Registry 内容未变时重复走完整生成路径。
- 先区分 per-layout factory 的可缓存输入和 Registry 的 aggregating 输入；在 XML/include/style 依赖模型完整前，不承诺 KSP `isolating processor`。
- 后续再把 `LayoutDigest` 扩展到 include 依赖和精确 style/dimen/color/string/drawable 引用图，减少无关 values 改动带来的重跑。

**FallbackInflater 优化：**

- 普通 fallback 子树已改为 `XmlPullParser` seek + partial inflate，不再 inflate 兄弟节点。
- `fragment` 和无法安全局部生成的特殊节点仍保留整棵 layout inflate 的 legacy 路径，以避免破坏原生 `LayoutInflater` 语义。
- `include` / `merge` / `ViewStub` 已在 v0.4.0 P0 中进入编译期支持路径；无法解析或存在循环引用时仍按保守 fallback 处理。

**编译报告：**

- 生成 HTML/JSON 报告：每个 layout 的支持度、fallback 原因和 top fallback reason 汇总
- Gradle task: `./gradlew layoutX2CReport`
- CI 集成：可选按指定 fallback reason 或 FALLBACK layout 数量 warning/fail

**ConstraintLayout 实验子集：**

- 仅支持普通 View 的 start/end/top/bottom toOf parent 或 sibling。
- 支持 `0dp` match constraint、horizontal/vertical bias。
- 不支持 chains、barriers、guidelines、Flow、Group、dimension ratio、percent、circle constraint；遇到即 fallback。
- 默认作为实验能力关闭，开启后必须输出报告标记。

**验收口径：**

- Demo 和仓库内测试 layout 可产出 HTML/JSON 报告，按 layout 级和节点级分别统计 FULL / PARTIAL / FALLBACK。
- 增量编译有最小可验证场景：单文件改动只触发对应 factory 重新生成，Registry 是否聚合按依赖模型决定。
- HTML 报告可在 CI 上产出并归档为 artifact。
- ConstraintLayout 实验子集需配套 generated vs inflated 的 View 树等价性测试，否则不允许默认开启。

---

### v0.4.0 — 高级布局与绑定工具共存

目标：支持常见组合布局，并与现有 DataBinding / ViewBinding 项目共存。

**已完成 P0：高级布局基础支持**

- `include` 标签支持：递归解析被 include 的 layout，保留 include 标签上的 LayoutParams 覆盖、`android:id` 和 `android:visibility` 语义。
- `merge` 标签支持：作为虚拟容器处理，子节点直接注入父级，include 到 merge root 的场景按父级 LayoutParams 分析。
- `ViewStub` 支持：创建 `android.view.ViewStub`，写入 layout 资源和 inflatedId，延迟 inflate 语义不在编译期展开。
- Parser / Analyzer / CodeGen / ViewRegistry / KSP 均已接入特殊节点语义。
- 已有 `IncludeResolverTest`、`XmlLayoutParserTest`、`LayoutAnalyzerIncludeTest`、`LayoutCodeGeneratorTest` 覆盖基础和嵌套场景。

**后续高级布局：**

- Include 依赖纳入 `LayoutDigest`，被 include layout 改动时只重新生成受影响 factory。
- Include 跨边界 fallback 定位继续收敛；复杂 fallback include 暂时保持整棵 fallback。
- Merge 编译期内联继续优化，减少不必要的中间 factory 调用。
- ViewStub 引用 layout 的延迟生成可作为后续优化，支持运行时走生成 factory inflate。
- ConstraintLayout 扩展支持（chains、guidelines 等能力逐项引入，默认保守 fallback）

**兼容性：**

- ViewBinding 共存：不干扰 ViewBinding 生成流程，不承诺实现 ViewBinding 生成类或内部接口。
- DataBinding runtime 语义不替代：binding expression、BindingAdapter、dirty flag 和
  lifecycle 观察者逻辑继续由原生 DataBinding 处理；LayoutX2C 只透明解包 `<layout>` 包装层并生成真实 View 根。
- LayoutX2C binding-like facade：为 `<layout>` XML 生成 `{Name}X2CBinding`，接管 `inflate()`、`bind()`、`root`、ID 字段，以及 `<data>` 变量属性、`lifecycleOwner` 字段和 `executePendingBindings()` 空方法。
- 自定义 View 白名单：用户声明哪些自定义 View 可以安全生成

**自定义 View 白名单约束：**

- 白名单中的 View 必须满足：有 `(Context)` 或 `(Context, AttributeSet)` 公开构造函数。
- 不依赖 `defStyleAttr` / `defStyleRes` 构造参数（即不从 theme 读取默认样式）。
- 用户通过 DSL 声明白名单时需指定该 View 支持的属性子集；未声明的属性遇到即跳过（PARTIAL）。
- 编译期做静态检查：反射验证构造函数签名，不满足则报 warning 并拒绝生成。

**Style / Theme 部分支持：**

- 内联已知 style 属性（编译期解析 styles.xml）。
- `?attr/` 默认继续 fallback。
- 对 `?attr/` 的编译期替换只作为显式 opt-in 能力，并要求用户提供 theme 映射。
- debug 模式提供 generated vs inflated 的 View 树差异报告。

**验收口径：**

- merge / include / ViewStub 各有独立单元测试，覆盖嵌套 include、include + merge、LayoutParams 覆盖和 ViewStub 创建场景。
- 端到端 Android 集成样例仍需补齐，尤其是 generated vs inflated 的真实 View 树等价性验证。
- DataBinding 布局（`<layout>` 根标签）透明解包且不影响编译；异常 wrapper 保留专门报告归因，不替代 DataBinding runtime 语义。
- `{Name}X2CBinding` 仅为 `<layout>` XML 生成；普通 XML 不生成该类。
- 自定义 View 白名单 DSL 有文档和 demo。
- Style 内联仅在 `styles.xml` 可静态解析时生效，不可解析时 fallback 而非报错。

---

### v0.5.0 — Lint、调试与开发体验

目标：让开发者能快速评估收益、定位问题。

**Lint 规则：**

- 检测可避免的 fallback（如不必要的 style 引用）
- 建议将 `?attr/colorPrimary` 替换为具体颜色以获得编译期生成

**调试支持：**

- 生成代码中保留 XML 行号注释
- Runtime 可选 debug 模式：对比 generated vs inflated 的 View 树差异

**验收口径：**

- Lint 规则有 LintFix 测试覆盖。
- 调试模式的 View 树差异报告可输出到 logcat 和文件。
- HTML 编译报告能定位 layout、节点、属性三级 fallback 原因。

**post-1.0 候选：IDE 插件（IntelliJ / Android Studio）**

- Layout 文件内 gutter icon 显示支持度（绿/黄/红）。
- 点击跳转到生成的 Kotlin 代码。
- Quick-fix：对 PARTIAL 节点提示如何改写以获得 FULL 支持。
- 只有在 HTML 报告无法覆盖主要排查需求时再启动，不作为 v1.0 前置条件。

---

### v1.0.0 — 生产就绪

目标：稳定 API，发布到 Maven Central，可用于生产项目。

**稳定性：**

- API 冻结：LayoutFactory, Registry, 注解接口不再 breaking change
- 全面的集成测试（覆盖 Android API 21-35）
- 与 AGP 8.x / 9.x 兼容性矩阵测试
- ProGuard/R8 规则自动生成

**发布：**

- Maven Central 发布（runtime, ksp-processor, gradle-plugin）
- Gradle Plugin Portal 发布
- 版本化文档站点

**性能基准：**

- 公开 benchmark 数据：inflate 时间对比（冷启动 / 热启动）
- 内存占用对比
- 编译时间开销量化

**验收口径：**

- API surface 有 `@PublicApi` / `@ExperimentalApi` 标注，breaking change 有迁移指南。
- 兼容性矩阵覆盖 AGP 8.4+ / 9.x，Android API 21-35，KSP 2.x。
- Maven Central 发布流程自动化（CI 触发 + staging → release）。
- benchmark 数据在 README 和文档站点公开，包含测试设备和方法论说明。

---

## 架构演进

### 当前架构

```
XML File → XmlLayoutParser → LayoutTree → LayoutAnalyzer → AnalyzedNode
                                                                ↓
                                                      LayoutCodeGenerator → .kt file
```

单一管道，分析器和生成器耦合度低但扩展性有限。

### 目标架构（v0.3+）

```
                          ┌─────────────────────┐
                          │   ViewRegistry      │  ← 可扩展的 View/属性注册表
                          └────────┬────────────┘
                                   │
XML File → Parser → LayoutTree → Analyzer → AnalyzedTree
                                                │
                                   ┌────────────┼────────────┐
                                   ↓            ↓            ↓
                              CodeGen      ReportGen     LintCheck
                                   │
                          ┌────────┼────────┐
                          ↓        ↓        ↓
                     ViewEmitter  AttrEmitter  LayoutParamsEmitter
                     (per-view)   (per-attr)   (per-parent-type)
```

关键设计决策：

1. **ViewRegistry 驱动**：每种 View 类型注册一个 `ViewHandler`，包含支持的属性列表和代码生成逻辑。新增 View 支持 = 新增一个 Handler 文件，无需修改核心逻辑。

2. **Emitter 分层**：将代码生成拆分为 View 创建、属性设置、LayoutParams 三个独立 Emitter，各自可扩展。

3. **增量管道**：已引入保守 `LayoutDigest`（layout 文件 + values XML + 生成包名 + R 包名 + schema version），只有 digest 变化才重新生成；include 和精确资源引用图仍是后续优化。

4. **多后端**：CodeGen 抽象为接口，未来可支持 Java 代码生成（兼容纯 Java 项目）或 Compose 迁移辅助。

---

## 优先级矩阵

| 特性 | 影响 | 复杂度 | 优先级 |
|------|------|--------|--------|
| fallback 子树语义修正 | 高 | 中 | P0 |
| LayoutParamsEmitter | 高 | 中 | P0 |
| ImageView/Button 支持 | 高 | 低 | P0 |
| RelativeLayout 支持 | 高 | 中 | P0 |
| background 属性 | 高 | 中 | P0 |
| textSize/textColor | 高 | 低 | P0 |
| ConstraintLayout 实验子集 | 中 | 高 | P1 |
| 增量编译输入声明与端到端验证 | 中 | 中 | P1 |
| include/merge/ViewStub 基础支持 | 中 | 中 | Done |
| 编译报告 | 中 | 低 | P1 |
| IDE 插件 | 中 | 高 | Post-1.0 |
| Style 内联 | 中 | 高 | P2 |
| ConstraintLayout 完整 | 中 | 很高 | P2 |
| ViewBinding 兼容 | 低 | 中 | P2 |
| Maven Central 发布 | 高 | 低 | P1 |

---

## 非目标（明确不做）

- 替代 Jetpack Compose — LayoutX2C 服务于仍在使用 XML 布局的项目
- 运行时动态 layout 加载 — 这是编译期工具，不做运行时 DSL
- 支持所有可能的自定义 View — 通过白名单机制让用户自行扩展
- 100% 覆盖率 — 渐进式策略的核心就是接受 fallback 的存在
- 默认把运行时 Theme 语义编译成常量 — 这类能力只能显式 opt-in
