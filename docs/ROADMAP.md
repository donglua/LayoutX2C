# LayoutX2C Roadmap

> 编译期 XML Layout -> Kotlin 代码生成。核心策略是「能等价生成的生成，不能等价生成的 fallback」。生成错误比 fallback 更危险，因此路线图优先围绕语义正确性、可诊断性和可发布性展开。

---

## Current

当前主干已经具备一条可用的 XML -> AST -> Analyze -> CodeGen -> Runtime inflate 管道。

**核心管道**

- XML 解析器输出 `LayoutTree` AST，并保留节点路径、DataBinding root metadata 和特殊节点语义。
- 支持度分析器按 FULL / PARTIAL / FALLBACK 三级给出节点级结果。
- KotlinPoet 代码生成已拆分为 `ViewEmitter` / `AttrEmitter` / `LayoutParamsEmitter`。
- View / 属性支持面已迁移到 `DefaultViewRegistry` 和注册式 `ViewHandler` / `AttributeHandler`。
- Runtime 提供 `LayoutFactory`、`FallbackInflater` 和 Registry 自动注册。
- 生成代码提供 direct X2C inflate facade，demo 可直接走生成入口做 benchmark。

**已支持的布局和 View**

- 容器：`LinearLayout`、`FrameLayout`、`RelativeLayout`、`ScrollView`、`HorizontalScrollView`。
- 常用 View：`TextView`、`Button`、`EditText`、`ImageView`、`View`。
- AndroidX：`RecyclerView` 作为容器创建；`ConstraintLayout` 已有安全子集支持。
- 特殊标签：`include`、`merge`、`ViewStub` 已进入编译期支持路径。
- DataBinding `<layout>` root 会透明解包到真实 View root，并生成 LayoutX2C 的 binding-like facade。

**已支持的关键属性**

- 通用：尺寸、padding、margin、gravity、visibility、id、background。
- Text-like：text、textColor、textSize、textStyle、hint、inputType。
- ImageView：src、scaleType、tint。
- ScrollView：fillViewport。
- RelativeLayout 常见规则。
- ConstraintLayout 安全子集：普通 start/end/top/bottom 约束、`0dp` match constraint、horizontal/vertical bias。

**Fallback 和增量**

- fallback 子树定位支持完整 child path，并对非法路径输出可诊断错误。
- 普通 fallback 子树优先走 `XmlPullParser` seek + partial inflate；无法安全局部生成的特殊节点保留整棵 layout inflate 兼容路径。
- Gradle 插件已声明 layout / values XML 输入，并传入 `layoutx2c.cacheDir`。
- KSP 侧已落地保守 digest cache：digest 未变时可恢复 per-layout factory、facade 和 report。
- Registry 仍是 aggregating 输出，但已通过内容 digest/cache 避免 Registry 内容未变时重复走完整生成路径。

**当前限制**

- `fragment`、无法解析的 include、循环 include、超出递归深度限制的 include，以及无法等价生成的特殊语义仍会保守 fallback。
- `LayoutDigest` 还没有 include 依赖图和精确资源引用图；values XML 仍作为 coarse input。
- 编译报告已有节点级 JSON，但还没有产品化 HTML/JSON 汇总和 top fallback reason 视图。
- Android 端到端 generated vs inflated 等价性覆盖还需要系统补齐。

---

## Next

短期只推进会直接提高可用性和发布可信度的工作。

### 1. 编译报告产品化

目标：让用户能快速看出哪些 layout 生成成功、哪些节点 fallback、最值得优先处理的原因是什么。

- 输出项目级 HTML/JSON 汇总。
- 汇总每个 layout 的 FULL / PARTIAL / FALLBACK 结果。
- 汇总 top fallback reason，支持按 layout、节点、属性三级定位。
- 提供稳定的 Gradle 入口，例如 `./gradlew layoutX2CReport`。
- CI 可选按 FALLBACK layout 数量或指定 fallback reason warning/fail。

验收口径：

- Demo 和仓库内测试 layout 可产出可读报告。
- 报告能定位 layout、节点、属性三级 fallback 原因。
- JSON schema 稳定，后续 HTML、CI 和 IDE 能复用。

### 2. Include 依赖进入 Digest

目标：被 include 的 layout 改动时，只重新生成受影响 factory，同时保持保守正确。

- `LayoutDigest` 纳入 include 引用图。
- 检测 include 循环、缺失和深度限制，并把原因写入 report。
- Registry 仍可保持 aggregating 输出，不提前承诺 KSP isolating processor。
- 后续再扩展到精确 style / dimen / color / string / drawable 引用图。

验收口径：

- 修改被 include layout 会触发引用方重新生成。
- 修改无关 layout 不触发无关 factory 重生成。
- 循环 include 不崩溃，并可诊断地 fallback。

### 3. Android 等价性测试补齐

目标：用真实 Android inflate 结果约束高风险语义，避免代码生成只在字符串测试层面正确。

- 覆盖 include、nested include、include + merge、ViewStub、ConstraintLayout 安全子集。
- 覆盖 fallback 子树和父级 LayoutParams 保留语义。
- 对比 generated vs inflated 的 View tree、id、visibility、LayoutParams 和关键属性。

验收口径：

- 每类特殊语义都有最小端到端 Android 测试。
- 失败信息能指出 layout、节点路径和不一致属性。

### 4. README 和 Demo 同步

目标：让用户看到的支持范围与实际 Roadmap 保持一致。

- README 更新 include / merge / ViewStub / ConstraintLayout 安全子集支持范围。
- Demo 补齐对应 XML 样例和 benchmark 入口。
- 明确 DataBinding facade 不替代 DataBinding runtime expression、BindingAdapter、dirty flag 和 lifecycle 观察者语义。

验收口径：

- README 的支持范围、限制和 Roadmap 一致。
- Demo 能覆盖主要成功路径和 fallback 路径。

---

## Later

这些方向有价值，但不应阻塞短期可用性。

**Style / Theme 部分支持**

- 内联 `styles.xml` 中可静态解析的已知 style 属性。
- `?attr/` 默认继续 fallback。
- `?attr/` 编译期替换只作为显式 opt-in，并要求用户提供 theme 映射。

**自定义 View 白名单**

- 用户声明哪些自定义 View 可以安全生成。
- 白名单 View 必须有 `(Context)` 或 `(Context, AttributeSet)` 公开构造函数。
- 用户通过 DSL 声明支持属性子集；未声明属性遇到即 PARTIAL / fallback。
- 编译期静态检查构造函数签名，不满足则 warning 并拒绝生成。

**ViewStub 延迟生成**

- 为 ViewStub 引用的 layout 生成独立 factory。
- 运行时 ViewStub inflate 可选择走生成 factory。
- 必须保持原生 ViewStub 替换、inflatedId 和 LayoutParams 语义。

**ConstraintLayout 扩展**

- 逐项引入 chains、guidelines、barriers、dimension ratio、percent 等能力。
- 默认保守 fallback；每项能力必须有 generated vs inflated 等价性测试。
- 复杂 ConstraintLayout 不作为 v1.0 前置条件。

**Lint / IDE 辅助**

- Lint 检测可避免的 fallback，并给出可操作建议。
- IDE gutter icon、跳转生成代码和 quick-fix 作为 post-1.0 候选。
- 只有当 HTML/JSON 报告无法覆盖主要排查需求时再投入 IDE 插件。

**其他候选**

- Java 代码生成后端，用于兼容纯 Java 项目。
- 更精确的资源引用图，减少无关 values 改动带来的重跑。
- Runtime debug 模式输出 generated vs inflated 差异报告。

---

## 1.0

v1.0 的目标是稳定 API，发布到 Maven Central，并能被生产项目保守接入。

**API 稳定**

- `LayoutFactory`、Registry 和注解接口冻结。
- Public API 使用 `@PublicApi` / `@ExperimentalApi` 标注。
- breaking change 必须有迁移指南。

**兼容性矩阵**

- 覆盖 AGP 8.4+ / 9.x。
- 覆盖 KSP 2.x。
- 覆盖 Android API 21-35。
- 与 ViewBinding / DataBinding 共存，不干扰原生生成流程。

**发布工程**

- Maven Central 发布 runtime、compiler-core、ksp-processor、gradle-plugin。
- Gradle Plugin Portal 发布插件。
- CI 自动化 staging -> release。
- 自动生成 ProGuard / R8 规则。

**性能和文档**

- 公开 benchmark 数据：inflate 时间、内存占用、编译时间开销。
- README 和文档站点说明测试设备、方法论和限制。
- 示例项目覆盖 generated inflate、fallback、DataBinding facade 和报告输出。

---

## Non-goals

- 替代 Jetpack Compose：LayoutX2C 服务于仍在使用 XML 布局的项目。
- 运行时动态 layout 加载：这是编译期工具，不做运行时 DSL。
- 100% 覆盖率：渐进式策略的核心就是接受 fallback 的存在。
- 默认把运行时 Theme 语义编译成常量：这类能力只能显式 opt-in。
- 替代 DataBinding runtime：表达式、BindingAdapter、dirty flag 和 lifecycle 观察者逻辑继续由原生 DataBinding 负责。
