# 纯色 · Solid Color

一个「什么都没有」的 Android App：打开就是一块纯色屏幕，无状态栏、无导航栏、无任何控件。
用于护眼垫色、屏幕测试、投影背景、直播垫屏、旧手机当时钟背景等场景。

> 纯色 · 默认黑色 · 沉浸式 · 支持分屏与小窗

---

## 功能

| 功能 | 说明 |
| --- | --- |
| 纯色背景 | 默认纯黑 `#000000`，颜色可自由调节，退出后记住上次颜色 |
| 全屏沉浸 | 启动即隐藏状态栏与导航栏，内容延伸到刘海/挖孔区域 |
| 二次滑动显示系统栏 | 单次滑动只会让系统栏「闪一下」后自动收起，**需要连续两次滑动**才会常驻显示；系统栏显示时再滑一次即隐藏 |
| 状态栏图标自适应 | 显示系统栏时，状态栏/导航栏图标颜色会根据背景明暗自动切换黑/白 |
| 中心点击菜单 | 点击屏幕中央区域（默认中央 50%×50%）弹出控制窗口，其他地方点击无反应 |
| 10 秒自动关闭 | 菜单 10 秒无操作自动淡出；菜单内任何触摸都会重新计时，并实时显示剩余秒数 |
| 调色盘 | 饱和度/明度面板 + 色相条 + 18 个预设色 + 十六进制值显示，拖动即时生效 |
| 一键退出 | 菜单内「退出」按钮直接结束 App（`finishAffinity()`） |
| 分屏 / 小窗 | `resizeableActivity="true"`、不锁定方向，小窗内调色盘自动滚动适配 |
| 防误退出 | 返回手势（边缘滑动）被计为一次「滑动」，不会直接退出 App，退出只走菜单按钮 |

## 怎么用

1. 打开 App —— 全屏纯色，什么都不显示。
2. **点屏幕正中间** → 弹出菜单（`调色盘` / `退出`），10 秒不动自动关闭。
3. `调色盘` 里拖动即可实时改色，`完成` 收起。
4. 想要状态栏/导航栏：**连续滑动两次**（4 秒内）；再滑一次收起。

## 技术要点

- **Jetpack Compose**（Material 3）实现全部 UI，单 Activity。
- 沉浸式：`WindowCompat.setDecorFitsSystemWindows(window, false)` +
  `WindowInsetsControllerCompat.hide(systemBars())`，
  行为设为 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`，因此单次滑动只会临时浮出系统栏。
- 二次滑动：App 内自建手势识别（`pointerInput` + `awaitFirstDown`/`awaitPointerEvent`），
  统计滑动次数并在 4 秒窗口内累加；`BackHandler` 把返回手势也接入同一逻辑，避免误退出。
- 中心热区：取 `onSizeChanged` 得到的画布尺寸，命中中央 `x∈[25%,75%]`、`y∈[25%,75%]` 才弹菜单。
- 菜单超时：`LaunchedEffect(menuOpen, idleTick)` 内以 60ms 步进递减剩余时间，
  任何触摸让 `idleTick` 自增，从而重置倒计时。
- 调色盘：自绘 `Canvas`（水平白→色相渐变 + 垂直透明→黑渐变）实现 SV 面板，
  色相条为 7 段 HSV 渐变；取色/回填使用 `android.graphics.Color.colorToHSV / HSVToColor`。
- 颜色持久化：`SharedPreferences`（`onPause` 写入，启动读取）。
- 状态栏图标：`WindowInsetsControllerCompat.isAppearanceLightStatusBars`，
  明暗判断用 `Color.luminance(argb) > 0.6`。

## 构建

用 [CodeAssist](https://github.com/tyron12233/CodeAssist) 或任意 Android 构建环境，
模块配置见 `app/module.toml`（`compileSdk 36` / `minSdk 26` / `targetSdk 36`，`compose = true`）。

依赖：

```
androidx.activity:activity-compose:1.9.3
androidx.compose.ui:ui:1.7.5
androidx.compose.foundation:foundation:1.7.5
androidx.compose.material3:material3:1.3.1
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 目录结构

```
app/
├── module.toml
└── src/main/
    ├── AndroidManifest.xml
    ├── kotlin/com/example/solidbg/
    │   ├── MainActivity.kt     # 沉浸式、手势、菜单、倒计时
    │   └── ColorPicker.kt      # 调色盘（SV 面板 / 色相条 / 预设）
    └── res/
        ├── drawable/ic_solid.xml
        └── values/{strings,themes}.xml
```

## 可调参数

| 位置 | 含义 | 默认 |
| --- | --- | --- |
| `MENU_TIMEOUT_MS` | 菜单无操作自动关闭时间 | 10000 ms |
| `SWIPE_STREAK_WINDOW_MS` | 两次滑动之间的有效间隔 | 4000 ms |
| `swipeThreshold` | 判定为「滑动」的最小位移 | 42 dp |
| 中心热区 | 触发菜单的区域比例 | 中央 50%×50% |

## License

MIT
