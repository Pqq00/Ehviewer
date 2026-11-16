# miuix 升级指南（v0.9.4 → 0.10.0）

本文记录我们依赖的 `top.yukonga.miuix.kmp` 从 **0.9.4**（当前使用）到 **main**（下一个发布版，预计 0.10.0）的差异，以及已经提前做好的兼容层。

## 结论

- **现在不能升级**：Maven Central 的最新正式版仍是 0.9.4（2026-09-20）；main 已 ahead 84 个提交，但 `miuix-material` / `miuix-glass` / `miuix-glass-icons` 三个新模块都还没发布（`maven-metadata.xml` 404）。GitHub Packages 上只有 `<版本>-<git短哈希>-SNAPSHOT` 快照，需要 `read:packages` 权限且版本号不可复现。
- **但升级要改的东西已经收敛**：所有会变的 API 都集中到 `shared/src/commonMain/kotlin/app/eros/shared/ui/effect/MiuixGlassCompat.kt`，其余业务代码不再直接引用 `blur.*` 里那几个即将移动/删除的符号。
- 0.10.0 发布后，预计 **1–2 小时**即可完成升级（改 1 个兼容文件 + 加依赖 + 真机调边光）。

## 比对方法（可复现）

```powershell
# 版本差异
Invoke-RestMethod 'https://api.github.com/repos/compose-miuix-ui/miuix/compare/v0.9.4...main'
# ⇒ ahead_by=84, total_commits=84, files=300

# 源码对照（_ref/ 已被 .gitignore 忽略，不入库）
git clone --depth 1 https://github.com/compose-miuix-ui/miuix _ref/miuix-main
git clone --depth 1 --branch v0.9.4 https://github.com/compose-miuix-ui/miuix _ref/miuix-094

# 我们用到的符号
Select-String -Path 'shared/**/*.kt' -Pattern '^import top\.yukonga\.miuix'
```

我们共引用了 **119 个** miuix import（按符号名去重 118 个）。两棵源码树逐符号比对：**117 个符号在 main 仍然存在**，唯一被整个删除的是 `blur.highlight.Highlight`。

## 模块结构变化

| 0.9.4 | main | 说明 |
| --- | --- | --- |
| `miuix-core` | `miuix-core` | 不变 |
| `miuix-ui` | `miuix-ui` | 不变 |
| `miuix-preference` | `miuix-preference` | 不变 |
| `miuix-shader` | — | **模块删除**，内容并入 `miuix-material`；`top.yukonga.miuix.kmp.shader.*` 包消失 |
| `miuix-blur` | `miuix-blur` | 保留，但 `RuntimeShader` / `asBrush` / `isRuntimeShaderSupported` 移入 material |
| — | `miuix-material` | 新增：材质、`RuntimeShader`、`edgeStroke`、玻璃边光 |
| — | `miuix-glass` | 新增：`glassPanel` / `glass` / `glassSurface` / `glassShadow`（OS4 玻璃） |
| — | `miuix-glass-icons` | 新增：`MiuixIcons.Glass` |
| `miuix-squircle` | `miuix-squircle` | 不变 |
| `miuix-icons` | `miuix-icons` | 不变 |
| `miuix-nav` | `miuix-nav` | 不变（公开 API 签名零差异） |
| — | `miuix-window` | 新增：窗口/标题栏相关（我们暂未用） |

## 会打断我们的 API

| 我们用的 | main 的状态 | 替换方式 | 涉及文件 |
| --- | --- | --- | --- |
| `blur.highlight.Highlight`（`GlassStrokeMiddleDark/Light`） | **整个包删除** | 已收进 `GlassHighlight` 类型别名；边框改用 `material.edgeStroke(...)` | `ui/reader/ReaderChrome.kt`、`ui/ErosBottomNavBar.kt`、`App.kt`（后者是死 import，已删） |
| `textureBlur(..., highlight = ...)` | `highlight` 参数删除 | 兼容层 `Modifier.erosGlassBlur(...)`；0.10 时边光改 `edgeStroke` | `ReaderChrome.kt`、`ErosBottomNavBar.kt` |
| `progressiveTextureBlur(..., highlight = ...)` | 同上 | 兼容层 `Modifier.erosProgressiveGlassBlur(...)` | `ReaderChrome.kt`、`ErosTopBar.kt` |
| `blur.RuntimeShader(code)` | 移到 `material.RuntimeShader(code)` | 兼容层 `glassRuntimeShader(code)` | `ui/liquid/Lens.kt`、`ui/effect/BgEffectPainter.kt` |
| `blur.RuntimeShader.asBrush()` | 移到 `material.asBrush()` | 兼容层 `asGlassBrush()` | `BgEffectPainter.kt` |
| `blur.isRuntimeShaderSupported()` | 移到 `material.isRuntimeShaderSupported()` | 兼容层 `isShaderSupported()` | `Lens.kt`、`BgEffectBackground.kt`、`ErosTopBar.kt` |
| `shader.isRuntimeShaderSupported()` | 模块删除 | 同上 | `App.kt`（死 import，已删） |
| `utils.pagerGestureOverride` | 模式枚举变化：`CrossAxisInterceptor`→`CrossAxis`，`TapToHalt`（iOS-like）删除，`iosStyleMomentumHalt` 删除；新增 `Int` 重载与 `PagerGestureNestedScrollConnection`，用法要求 `userScrollEnabled = false` + 传 `flingBehavior` | 我们只有死 import，已删；将来要用得重写 | `App.kt` |

## 不受影响的部分

- `miuix-nav` 的 `NavDisplay` / `NavDisplayEffects` / `NavKey` / `rememberNavBackStack` / `NavCornerClipMode` / `rememberNavSystemCornerRadius` / `NavSwipeDirection` 签名完全一致。
- `theme`：`ColorSchemeMode` 仍是 `System / Light / Dark / MonetSystem / MonetLight / MonetDark`，`ThemeController(...)` 签名一致。
- `blur.BlurColors` / `BlendColorEntry` / `BlurBlendMode` 结构一致；`BlurDefaults` 新增对 `material.GlassDefaults` 的依赖（所以引入 material 后即可）。
- `blur.runtimeShaderEffect` 仍在 `blur` 里，只是从 `RuntimeShader.kt` 挪到 `BackdropEffectScope.kt:114`，仍作 `BackdropEffectScope` 的扩展函数，调用形式不变。

## 兼容层：`MiuixGlassCompat.kt`

位置 `shared/src/commonMain/kotlin/app/eros/shared/ui/effect/MiuixGlassCompat.kt`，导出：

```kotlin
typealias GlassHighlight = Highlight                    // 0.10: 删除，改用 edgeStroke
@Composable fun rememberGlassHighlight(): GlassHighlight?  // 深/浅色边光
fun isShaderSupported(): Boolean                        // 0.10: material.isRuntimeShaderSupported
fun glassRuntimeShader(code: String): RuntimeShader     // 0.10: material.RuntimeShader
fun RuntimeShader.asGlassBrush(): Brush                 // 0.10: material.asBrush
fun Modifier.erosGlassBlur(...)                         // 0.10: 去掉 highlight，边光走 edgeStroke
fun Modifier.erosProgressiveGlassBlur(...)              // 同上
```

**升级时只改这个文件**即可让全部调用方继续编译。

## 升级步骤（0.10.0 发布到 Central 后）

1. `gradle/libs.versions.toml` 把 `miuix` 升到 `0.10.0`。
2. `shared/build.gradle.kts` 的 `commonMain` 增加 `libs.miuix.material`；如果要试官方玻璃，再加 `miuix-glass`、`miuix-glass-icons`。
   - 注意 `miuix-blur` 仍声明 `minSdk 33`，而 `miuix-glass` 声明 `minSdk 24`；低版本设备需 `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"`。
3. 改 `MiuixGlassCompat.kt`：
   - `import top.yukonga.miuix.kmp.blur.RuntimeShader` → `material.RuntimeShader`
   - `asBrush()` → `material.asBrush()`
   - `isRuntimeShaderSupported()` → `material.isRuntimeShaderSupported()`
   - `textureBlur(..., highlight = ...)` / `progressiveTextureBlur(..., highlight = ...)` → 去掉 `highlight` 参数；边光改用 `Modifier.edgeStroke(...)`（`EdgeStrokeModifier.kt:27`），用 `GlassEdgeStrokes` / `rememberTiltLight` 调效果
   - `typealias GlassHighlight` 删除，`rememberGlassHighlight()` 改为返回 `EdgeStroke?` 或直接内联
4. 编译 + 真机看边光强度（深色/浅色各一次），必要时调 `edgeStroke` 参数。
5. 跑验证：`.\gradlew.bat :core:test :shared:desktopTest :android:assembleDebug`。

## 可以顺带修掉的问题

main 上的这些修复升级后自动获得：

- `fix(component): handle unbounded width in BasicComponent measure (#469)` —— 组件在无界宽度下测量异常
- `fix: Optimize scroll performance in UI components` —— 列表滚动性能
- `library: Fix display cutout insets on landscape tablets and foldables` —— 横屏/折叠屏挖孔内边距
- `fix: Adjust floating navigation bar bottom spacing` / `fix: Adjust navigation bar bottom spacing on iOS` —— 底部导航栏间距
- 跨轴 pager 手势的一串修复
- `fix: Slow down the PullToRefresh completion text fade`
- `fix: Preserve glass material when reducing alpha`

## 可选：官方 `miuix-glass`（OS4 玻璃）

`miuix-glass` 提供 `glassPanel` / `glass` / `glassSurface` / `glassShadow`，正是我们现在用 `ui/liquid`（改编自 Kyant0/AndroidLiquidGlass）手写的那套效果。官方说明见 `_ref/miuix-main/docs/zh_CN/guide/glass.md`。

- 依赖 `RuntimeShader`，即 **Android 13+** 才有真实玻璃；低版本退化为不透明 `containerColor`。
- 我们的 `ui/liquid/Lens.kt` 与官方 `example/.../liquid/Lens.kt` 是同一份改编代码，迁移成本低。
- 建议 0.10.0 发布后单独评估：先用官方 `miuix-glass` 跑一遍深色/浅色，再决定是否删掉本地 `ui/liquid`。

## 风险与回滚

- 本次改造只改 import 与调用形态，**不改变任何视觉效果**（`MiuixGlassCompat.kt` 内部仍调 0.9.4 的 `textureBlur` + `Highlight`）。
- 回滚：`git revert` 本支线提交即可，无依赖变化、无数据迁移。
