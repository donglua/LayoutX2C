# LayoutX2C Roadmap

> 编译期 XML Layout → Kotlin 代码生成。核心策略是「能等价生成的生成，不能等价生成的 fallback」。生成错误比 fallback 更危险，因此每个阶段都以语义正确性优先。

---

## 当前状态（v0.1.0-SNAPSHOT）

已完成的基础骨架：

- XML 解析器 → LayoutTree AST
- 支持度分析器（FULL / PARTIAL / FALLBACK 三级）
- KotlinPoet 代码生成
- KSP 注解处理器（@FastLayouts / @FastLayoutPattern / @FastLayoutConfig）
- Gradle 插件（自动 apply KSP、传递 res 路径）
- Runtime：LayoutFactory 接口 + FallbackInflater + Registry 自动注册
- Demo App + BenchmarkActivity

支持的 View：LinearLayout, FrameLayout, TextView, View
支持的属性：约 20 个（尺寸、padding、margin、gravity、visibility、text、id、orientation、weight）

当前限制：

- fallback 子树仍依赖「inflate 整棵 layout 后按 child index 取节点」的 MVP 实现，仅适合浅层 fallback。
- KSP 处理器会生成聚合 Registry，增量编译策略尚未定义清楚。
- 覆盖率指标还没有绑定真实 XML 样本集，不能直接用作发布门槛。
- `LayoutCodeGenerator` 内的 View 类型解析、属性处理、LayoutParams 生成都是硬编码的 `when` 表达式。每加一个 View 或属性都要改核心文件，这是 v0.2「代码生成分层」首先要消化的债。

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

- 属性处理器插件化：每个 View 类型注册自己的 AttributeHandler
- 引入 ViewRegistry 配置文件，支持用户自定义 View 映射

**验收口径：**

- demo 中新增 RelativeLayout、ImageView、Button、EditText 样例。
- compiler-core 覆盖新增属性和 LayoutParams 生成。
- 至少选取一个真实项目样本集，输出 FULL / PARTIAL / FALLBACK 分布报告。

---

### v0.3.0 — 覆盖率、报告与保守增量

目标：让大型项目（500+ layout）可以评估收益、定位 fallback 原因，并为增量编译建立正确的依赖模型。

**覆盖率基线：**

- 固定一组真实 XML 样本集，记录 layout 数、节点数、FULL / PARTIAL / FALLBACK 口径。
- 覆盖率按 layout 和节点分别统计，不混用指标。
- v0.3 目标：样本集中高频简单布局的 layout 级 fallback 明显下降；不以「通用 80%」作为硬承诺。

**增量编译：**

- 基于文件 hash 的缓存：layout 未变则跳过重新生成。
- `LayoutDigest` 包含 layout 文件、include 依赖、style/dimen/color 依赖。
- 先区分 per-layout factory 的 isolating 输入和 Registry 的 aggregating 输入。
- 在依赖模型明确前，不承诺 KSP `isolating processor`。
- 集成 Gradle up-to-date check。

**FallbackInflater 优化：**

- 当前实现 inflate 整棵树再取子节点，改为 XmlPullParser seek 方式
- 对 fallback 子树做 partial inflate（只 inflate 目标子树，不 inflate 兄弟节点）

**编译报告：**

- 生成 HTML/JSON 报告：每个 layout 的覆盖率、fallback 原因
- Gradle task: `./gradlew layoutX2CReport`
- CI 集成：覆盖率低于阈值时 warning/fail

**ConstraintLayout 实验子集：**

- 仅支持普通 View 的 start/end/top/bottom toOf parent 或 sibling。
- 支持 `0dp` match constraint、horizontal/vertical bias。
- 不支持 chains、barriers、guidelines、Flow、Group、dimension ratio、percent、circle constraint；遇到即 fallback。
- 默认作为实验能力关闭，开启后必须输出报告标记。

**固定样本集：**

- v0.3 基准样本固定为：K-9 Mail、AntennaPod、Telegram-FOSS。
- 扩展观察样本：NewPipe、Signal-Android，仅用于发现额外语法和属性，不作为 v0.3 验收门槛。
- 选取标准：layout 数量 200+、属性多样性高、仍有 XML 布局存量、仓库可稳定复现。
- 样本集落档到 `docs/samples.md`，记录每个项目的 commit hash，确保覆盖率指标可复现。

**验收口径：**

- 样本集上跑出 baseline 报告，按 layout 级和节点级分别统计 FULL / PARTIAL / FALLBACK。
- 增量编译有最小可验证场景：单文件改动只触发对应 factory 重新生成，Registry 是否聚合按依赖模型决定。
- HTML 报告可在 CI 上产出并归档为 artifact。
- ConstraintLayout 实验子集需配套 generated vs inflated 的 View 树等价性测试，否则不允许默认开启。

---

### v0.4.0 — 高级布局与绑定工具共存

目标：支持常见组合布局，并与现有 DataBinding / ViewBinding 项目共存。

**高级布局：**

- merge 标签支持
- include 标签支持（递归解析被 include 的 layout）
- ViewStub 支持（延迟 inflate 语义保留）
- ConstraintLayout 扩展支持（chains、guidelines 等能力逐项引入，默认保守 fallback）

**兼容性：**

- ViewBinding 共存：不干扰 ViewBinding 生成流程，不承诺实现 ViewBinding 生成类或内部接口。
- DataBinding 布局自动跳过（检测 `<layout>` 根标签）
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

- merge / include / ViewStub 各有独立集成测试，覆盖嵌套场景。
- DataBinding 布局（`<layout>` 根标签）自动跳过且不影响编译。
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

3. **增量管道**：引入 `LayoutDigest`（layout 文件 hash + 依赖的 include / style / dimen / color hash），只有 digest 变化才重新生成。

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
| 真实样本覆盖率报告 | 高 | 低 | P0 |
| ConstraintLayout 实验子集 | 中 | 高 | P1 |
| 增量编译 | 中 | 中 | P1 |
| include/merge 标签 | 中 | 中 | P1 |
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
