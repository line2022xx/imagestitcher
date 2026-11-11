# 图片拼接（ImageStitcher）

一个轻量的 Android 长图拼接工具，功能对齐 PPIICC 的基础拼接能力：把多张截图/照片按顺序拼成一张大图。

- 语言：Kotlin 1.9.24
- UI：Jetpack Compose（Material 3）
- 构建：AGP 8.5.2 / Gradle 8.7 / JDK 17
- 最低系统：Android 7.0（API 24），目标 API 34

## 功能

### 拼接方向
- 纵向拼接：上下依次排列
- 横向拼接：左右依次排列

### 缩放模式
| 模式 | 纵向含义 | 横向含义 |
| --- | --- | --- |
| 直接拼接 | 每张图保持原始像素，画布宽取最大值，窄图按对齐方式补背景色 | 每张图保持原始像素，画布高取最大值 |
| 最大宽度 | 以最宽的一张为基准，其余图等比放大到该宽度 | 以最高的一张为基准，其余图等比放大到该高度 |
| 最小宽度 | 以最窄的一张为基准，其余图等比缩小到该宽度 | 以最矮的一张为基准，其余图等比缩小到该高度 |

示例（纵向，三张图 1080×2400、720×1280、1440×3200）：
- 最大宽度 → 1440×8960
- 最小宽度 → 720×4480
- 直接拼接 → 1440×6880（画布宽取最大，窄图补背景）

### 其他可配置项
- 交叉轴对齐：起始 / 居中 / 末尾（纵向时是左右，横向时是上下）
- 图片间距：0–120 px
- 背景色：白 / 黑 / 浅灰 / 透明（透明自动改用 PNG）
- 输出格式：JPEG（质量 60–100）/ PNG
- 输出尺寸实时预估，超内存预算时显示缩放比例

### 输出
- 保存到相册 `Pictures/ImageStitcher/`
- 一键分享（FileProvider）
- 每次导出前显示进度条，可看到逐张渲染百分比

## 目录结构

```
ImageStitcher/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/linexx/imagestitcher/
        │   │   ├── MainActivity.kt            页面路由与选图入口
        │   │   ├── core/
        │   │   │   ├── StitchPlanner.kt       纯逻辑：计算画布尺寸与每张图坐标（可单测）
        │   │   │   ├── ImageLoader.kt         尺寸探测 / EXIF 旋转校正 / 采样解码
        │   │   │   ├── Stitcher.kt            逐张绘制到画布并压缩输出
        │   │   │   └── MediaStoreSaver.kt     写入系统相册
        │   │   ├── model/ImageItem.kt         图片项、拼接配置、输出结果
        │   │   ├── ui/                        首页 / 编辑页 / 结果页 / 主题
        │   │   └── viewmodel/EditorViewModel.kt
        │   └── res/                           主题、图标、FileProvider 路径
        └── test/java/com/linexx/imagestitcher/core/StitchPlannerTest.kt
```

## 构建步骤

1. 安装 Android Studio（Koala / 2024.1 或更新版本，自带 JDK 17）。
2. `File > Open` 选择本目录 `ImageStitcher`。
3. 等待 Gradle Sync 完成（首次需下载 AGP、Compose、Coil 等依赖）。
4. 连上手机或启动模拟器，点击 Run；或 `Build > Build Bundle(s) / APK(s) > Build APK(s)` 生成 `app/build/outputs/apk/debug/app-debug.apk`。
5. 命令行构建（wrapper 已随工程提供）：

   ```bash
   ./gradlew assembleDebug            # macOS / Linux / Git Bash
   gradlew.bat assembleDebug          # Windows CMD / PowerShell
   ./gradlew testDebugUnitTest        # 跑单元测试
   ```

### 构建发布包

Release 签名信息从 `keystore.properties` 读取，该文件与 `keystore/` 目录均已加入 `.gitignore`，不入库：

```properties
RELEASE_STORE_FILE=keystore/imagestitcher-release.jks
RELEASE_STORE_PASSWORD=******
RELEASE_KEY_ALIAS=imagestitcher
RELEASE_KEY_PASSWORD=******
```

```bash
./gradlew assembleRelease    # 产物：app/build/outputs/apk/release/app-release.apk
```

没有该文件时 `assembleRelease` 只会产出未签名包（装不上），但不影响日常 debug 构建与单元测试 —— 其他人 clone 下来无需密钥即可开发。

国内网络如果下载依赖慢，可在 `settings.gradle.kts` 的两个 `repositories` 块里，把 `google()` / `mavenCentral()` 前面加上镜像地址。

## 内存策略（为什么超长图不会崩）

1. 画布只创建一张，按堆上限的 35%（ARGB_8888，4 字节/像素）反推可用像素预算。
2. 源图逐张解码 → 绘制 → 立即回收，峰值内存 ≈ 画布 + 单张源图，与图片总数无关。
3. 超出预算或边长超过 30000 px 时，整体等比降采样，并在编辑页与导出前明确告知缩放比例。
4. 解码时按目标矩形设置 `inSampleSize`，避免把 4000 万像素的原图整张读进内存。
5. Manifest 开启 `largeHeap`，为长图争取更大堆。

## 已知限制

- 单次最多 100 张图片。
- 极端超长图会按内存预算降采样，输出的实际像素尺寸可能小于原始尺寸（界面会提示）。
- 未实现「自动去除相邻图片重叠部分」，计划放在第二期。
- 仅浅色主题。

## 第二期规划

1. 重叠区域自动识别与裁切（长截图连续拼接）。
2. 图片拖拽排序、点击放大预览。
3. 圆角/描边/水印等美化选项。
4. 深色主题。

## 验证记录

本工程已完成真实构建与回归测试，环境：JDK 17.0.21 + Gradle 8.7 + AGP 8.5.2 + Android SDK 34。

| 检查项 | 结果 |
| --- | --- |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL，产出 `app-debug.apk`（9.3 MB） |
| `./gradlew testDebugUnitTest` | 19 个用例全部通过 |
| `./gradlew lintDebug` | 0 个问题（原 5 warning + 2 info 已全部修复） |
| Kotlin 编译 | 0 warning |

测试分两层：

1. **布局算法（15 个，纯 JVM）**：三种缩放模式 × 两个方向、间距、内存降采样、边长上限、宽高比保持、相邻图片无缝衔接、尺寸一致时三模式等价、极端宽高比。
2. **真实渲染（4 个，Robolectric 原生图形）**：动态生成纯色 PNG → 走完整 `probe → plan → render` 流程 → 读回输出图片校验像素。验证了每张图的落位与颜色、间距处的背景填充、透明背景的 alpha 保留。

> 回归过程中借此发现并修复了一个致命缺陷：`ImageLoader.readBounds` 用 `decodeStream` 的返回值判断读取是否成功，而 `inJustDecodeBounds` 模式下该返回值恒为 `null`，会导致选择任何图片都读不出尺寸（真机表现为"选完图没反应"）。

### 说明

`local.properties` 未包含在交付物中（内含 SDK 绝对路径，随机器而变）。用 Android Studio 打开时会自动生成；命令行构建请自行设置 `ANDROID_HOME` 或新建该文件：

```properties
sdk.dir=/your/android/sdk/path
```
