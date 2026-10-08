# 36.5°数字温控系统 V21（Native Viewport + Boot/Home）

V21 以 V20 为视觉基准：**UI 布局、区域比例、蓝灰色调全部锁定，不重新设计**。本版只处理两项：

1. 左侧 3D 模特 / 右侧热力图无法双指缩放。
2. 展示终端开机自启的可靠性。

## 1. V21 触控结构改动

V20 是直接修改 `ImageView.imageMatrix`。V21 改为：

`固定媒体窗口 ZoomableMediaViewport -> 内部 ImageView -> scaleX/scaleY + translationX/translationY`

也就是说，图片/动画负责显示；外层媒体窗口独占整个矩形区域的触摸事件；缩放和平移作用于 View 本身，不再修改 drawable 的 matrix。

这样可以避免：

- 左侧 `AnimatedImageDrawable` 持续刷新时覆盖缩放状态。
- 右侧热力图每 1.2 秒替换 drawable 时覆盖缩放状态。
- 图片可见区域过窄导致触控命中不稳定。

### 左右媒体区交互

- 双指放大 / 缩小。
- 放大后单指拖动。
- 双击恢复初始大小和位置。
- 左侧动画播放期间仍可缩放。
- 右侧热力图缩放/拖动后继续 1.2 秒轮播。
- 热力图手动点击正面/右侧/背面/左侧后停留约 3 秒，再恢复自动轮播。

## 2. 开机自启：双路径

V21 同时保留两种方式：

### A. BOOT_COMPLETED

Manifest 已包含 `RECEIVE_BOOT_COMPLETED`，`BootReceiver` 监听：

- `BOOT_COMPLETED`
- `LOCKED_BOOT_COMPLETED`
- `MY_PACKAGE_REPLACED`
- 常见工业主板 Quick Boot 广播

这是 best-effort 自启。Android 10/11 某些厂商系统会限制后台直接打开 Activity。

### B. 默认 HOME / Launcher（推荐用于 86 寸展示终端）

V21 的 MainActivity 同时声明为 HOME 应用。安装后可在系统：

`设置 -> 应用/默认应用 -> 主屏幕应用/Home/Launcher -> 36.5°数字温控系统`

选择为默认 HOME 后，设备通电启动进入桌面阶段时会直接进入本系统。对固定展示终端，这比只依赖 BOOT_COMPLETED 更可靠。

若设备弹出“选择主屏幕应用”，选择 `36.5°数字温控系统` 并选择“始终”。

## 3. 诊断页

连续点击左上角主标题 5 次，仍可查看：

- 屏幕真实分辨率 / App Metrics
- density / densityDpi / fontScale
- 系统栏尺寸
- 多点触控能力
- 左、右媒体区实际收到的最大触点数
- 当前缩放倍数

## 4. 模特清晰度

V21 暂时保留 V20 的 480×854 / 120 帧原始旋转素材，不同时改动视觉素材与触控链路。触控确认稳定后可在后续版本单独升级更高分辨率旋转序列。

## 5. GitHub Actions

工程 ZIP **不依赖 `.github` 隐藏目录**。根目录提供：

`BUILD_APK_WORKFLOW.yml`

如果仓库已有可用的 `.github/workflows/build-apk.yml`，保留即可，只替换工程其他文件。

如果需要重建工作流：

1. GitHub -> Add file -> Create new file
2. 文件名填写 `.github/workflows/build-apk.yml`
3. 复制 `BUILD_APK_WORKFLOW.yml` 的全部内容
4. Commit changes
5. Actions -> Build APK -> Run workflow

## 6. 建议实机测试顺序

1. 左侧模特双指放大、缩小。
2. 放大后单指拖动，双击复位。
3. 动画播放时继续缩放，确认动画不中断。
4. 右侧热力图双指缩放、拖动。
5. 保持放大状态观察 10 秒，确认轮播持续。
6. 重启设备测试 BOOT_COMPLETED 自启。
7. 若厂商限制广播自启，将 V21 设置为默认 HOME，再断电重启测试。
