# UI 层级契约（L0–L4）

本文件是 `shared/src/commonMain/kotlin/app/eros/shared/ui` 的层级契约，所有新增页面 / 浮层
先按这里选层，再写代码。守卫任务 `checkUiStructure` / `checkLayering` 会拦截常见违规。

对照来源：miuix 官方 example（`_ref/miuix-main/example`）的
`AppContent.kt`（根 Scaffold + `TopLayerHost`）、`utils/TopLayer.kt`、
`GlassPage.kt`（页面记录层 + 页面浮层 + 搜索覆盖层）。

---

## 1. 五层模型

```
L4  全屏路由        Route.Search / Route.Reader（+ 未来全屏选择器）
L3  浮层            dialog / bottom sheet / popup / 菜单
L2  页面栏          ErosTopBar、ErosSelectionToolbar、页面底部操作栏（ErosBottomBarHost 内的 ErosAppBottomBar）
L1  页面记录层      ErosGlassRecordLayer(backdrop = pageBackdrop)
L0  窗口 chrome     侧栏（原生 / 玻璃）、底栏宿主、FPS 浮层
```

自下而上绘制；**每一层只能采样它下方的层**，禁止采样自身或上方的层，否则 hwui prepare
会递归栈溢出（历史事故：`e843247`）。

### L0 窗口 chrome

- 宿主：`ErosAppChrome`（`ui/chrome/ErosAppChrome.kt`），在 `App.kt` 的 `Box` 里。
- 内容：原生侧栏 `ErosNavigationRail`、玻璃侧栏 `ErosGlassNavigationRail`、底部栏宿主
  `ErosBottomBarHost`、`FPSMonitor`。
- 规则：
  - chrome 可以采样 **L1 页面记录层**（`pageBackdrop`），自身不参与记录。
  - chrome 不得放进任何页面记录层内。
  - 底栏/侧栏的显示逻辑只由 `ErosAppChrome` 与 `BarDestination` 决定，页面不得自己画全局底栏。

### L1 页面记录层

- 唯一入口：`ErosGlassRecordLayer(backdrop) { ... }`（`ui/chrome/ErosGlassRecordLayer.kt`）。
- 页面骨架 `ErosPageScaffold` 内部已经包好；页面内容不要自己 `Modifier.layerBackdrop(...)`。
- 实际上有两块记录面（与官方 `GlassPage` 一致）：
  1. **整页记录面**：由 `ErosAppChrome` 的 `ErosGlassRecordLayer(pageBackdrop)` 记录
     「页面 + 页面栏」，供 L0 chrome（侧栏/底栏）采样；
  2. **内容记录面**：由 `ErosPageScaffold` 的 `ErosGlassRecordLayer(backdrop)` 记录页面内容，
     供 L2 页面栏与 L3 页面浮层采样（`LocalPageBackdrop` 从这里下发）。
- 两块记录面互相独立、都不得嵌套在对方内部。

### L2 页面栏

- `ErosTopBar`（`ui/ErosTopBar.kt`）由 `ErosPageScaffold` 的 `topBar` 槽位提供，采样
  **内容记录面**（页面骨架的 `backdrop`）；
- `ErosSelectionToolbar`（`ui/chrome/SelectionToolbar.kt`）用于多选工具栏；
- `ErosAppBottomBar` 由 `ErosBottomBarHost` 放在 L0 的底栏宿主里，**不要**自己放进页面。
- 规则：
  - 页面栏默认采样 L1 的内容记录面（`pageBackdrop` 参数 / `LocalPageBackdrop`），并通过
    `ErosGlassBar` 包一层以获得自采样守卫；
  - 页面栏不得进入内容记录面内部，否则采样自引用；
  - 页面栏的留白统一读 `LocalAppBottomBarInset.current`（不要自己算底栏高度）。
- 注意：`ErosPageScaffold` 目前把 `topBar` 放在 Scaffold 的 `topBar` 槽位、内容记录层在外面，
  这是正确的；但**不要**把 `topBar` 塞进 `ErosGlassRecordLayer` 里。

### L3 浮层

- 统一入口：`ErosBottomSheet` / `ErosDialog`（`ui/ErosGlassOverlays.kt`）、
  `ErosConfirmDialog`（`ui/ConfirmDialog.kt`）、miuix popup（由 App 层的
  `MiuixPopupUtils.MiuixPopupHost()` 承载）。
- 规则：
  - `backdrop` 必填（H3 起）：页面浮层传页面层 `pageBackdrop`，窗口级浮层传窗口层；
  - 浮层渲染宿主必须在 chrome 之后，保证盖住侧栏与底栏（H2 的 `ErosOverlayHost` / 根 Scaffold）；
  - 页面不要直接调用 miuix `OverlayDialog` / `OverlayBottomSheet` / `Popup`，一律走上面的封装。

### L4 全屏路由

- `Route.Search`（`ui/SearchScreen.kt`）、`Route.Reader`（`ui/reader/ReaderScreen.kt`）。
- 规则：自己负责背景与系统栏避让；Reader 是沉浸式特例（无 chrome），不套 `ErosPageScaffold`；
  搜索页套 `ErosPageScaffold` 但不显示全局底栏。

---

## 2. 采样矩阵（谁能采样谁）

| 采样方 \ 被采样 | 窗口层 | 页面层（L1） | 自己 |
|---|---|---|---|
| L0 chrome（侧栏/底栏） | ✗ | ✓ | ✗ |
| L2 页面栏 | ✗ | ✓ | ✗ |
| L3 浮层 | 视层级（窗口浮层可用窗口层） | ✓（页面浮层） | ✗ |
| L4 全屏页 | ✗ | 自己新建 | ✗ |

`ErosGlassBar(name, backdrop)` 会在运行期检查 `backdrop` 是否等于某一层正在暴露给自己的记录层，
命中时 `AppLog.e("ErosGlassBar", ...)` 报警（不再崩溃）。测试时用
`adb logcat -s ErosGlassBar` 验证。

---

## 3. 加东西时的选择

- 新增页面：套 `ErosPageScaffold`（Tabs 页 / 详情页模板），不要直接 `Scaffold`。
  沉浸式页面（阅读器类）另立模板，见 H4。
- 新增页面栏（顶部工具栏、选择栏）：放在 `ErosPageScaffold` 的 `topBar` / `floatingToolbar`
  槽位，或用 `ErosGlassBar` 包裹后放在内容层之外。
- 新增浮层：`ErosBottomSheet` / `ErosDialog` / `ErosConfirmDialog`，并显式传 `backdrop`。
- 新增全屏流程：新增 `Route`，不要用全屏 popup 冒充。

---

## 4. 守卫

- `:shared:checkUiStructure`：禁止 `ui/` 下直接 `Scaffold(`、禁止残留
  `import top.yukonga.miuix.kmp.basic.Scaffold`（`ErosPageScaffold.kt` 与 `reader/` 例外）。
- `:shared:checkLayering`（H5 起）：禁止 `ui/` 下直接 `Popup(` / `Dialog(` / `Overlay*` /
  `MiuixPopupUtils.`，禁止 `layerBackdrop(` 直达（允许 `chrome/ErosGlassRecordLayer.kt`、
  `ErosPageScaffold.kt`、`AccountScreen.kt` 的页面背景层、`liquid/`、`reader/`）。
  「`ErosBottomSheet(` / `ErosDialog(` 缺少 `backdrop =`」这条在 H3 改完签名后追加。

两项都挂在 `shared/build.gradle.kts`，CI 未接入时用 `.\gradlew.bat :shared:checkUiStructure
:shared:checkLayering` 本地跑。
