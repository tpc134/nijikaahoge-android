# Nijika's Ahoge — Android 版

**简体中文** | [English](README.en.md) | [日本語](README.ja.md)

![Nijika's Ahoge 在 iPhone、iPad 与 Android 真机上的实机画面](readme_pic/1.jpg)

**《虹夏的呆毛》的非官方 Android 移植版。**

| | |
| --- | --- |
| 原作 | **Nijika's Ahoge** |
| 别名 | 虹夏呆毛无限增殖中 / 呆毛可以无限增殖的虹夏酱 / 虹夏ちゃんの無限ドリトス |
| 原作链接 | <https://tamanidamani.itch.io/nijikas-ahoge> |
| 原作作者 | **ACh（ナコ）**、**TamaniDamani（塔打）** |
| 授权 | **已获得两位原作者的同意**，条件为**署名**与**非营利**；本移植**免费、无广告**。 |

原作是基于 Construct 3 的网页小游戏。本移植用 **Kotlin** 重新实现了全部游戏逻辑、渲染与音频，
物理改用 **jBox2D 2.2.1.1**，绘制走 **OpenGL ES 2.0**，不依赖任何游戏引擎。

| 项目 | 值 |
| --- | --- |
| 版本 | 1.2（versionCode 3） |
| 包名 | `com.ruich97.nijikaahoge` |
| 最低系统 | Android 5.0 / API 21 |
| 编译 / 目标 SDK | 35 / 35 |
| 图形 API | OpenGL ES 2.0 |
| 原生库 | 无 —— 通用 APK，armeabi-v7a 与 arm64-v8a 共用一份 |
| 体积 | release APK 约 2.36 MB |
| 构建链 | Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / JDK 17+ |

## 玩法

拔下虹夏头顶的呆毛，让它们落进袋子里收集；把袋子喂给凉或喜多会触发不同反应，满金袋会引出大波奇庆祝。
分数到达 **10 / 50 / 100 / 500 / 1000 / 5000** 时触发里程碑彩蛋，星歌从四个方向入场。
开屏跟随设备默认方向，1.5 秒后按传感器自由旋转，横竖屏均可用。

## 下载与安装

预编译 APK 发布在 **Releases** 页面，可直接安装（仓库源码树内不含 APK）。

| 产物 | 体积 | 说明 |
| --- | ---: | --- |
| `app-release_v1.2.apk` | 2.36 MB | release，已签名，安装用 |
| `app-debug.apk` | 2.62 MB | debug，支持 ADB 调试指令，不推荐日常使用 |

校验值（SHA-256）：

```
app-release_v1.2.apk  d56b8c45f76dc833ea5033660166ff50be6463a3699eef4331863a9f17d54bc6
app-debug.apk         e77a0794578e3bf473352cc5f6a706d87e68e6fa1bb5a1369daa8f41cfb42f40
```

重新构建后 APK 内容会变化，需重新计算校验值。

- 需要 **Android 5.0 / API 21 及以上**、支持 **OpenGL ES 2.0** 的设备。
  没有 ABI 专用原生库，armeabi-v7a 与 arm64-v8a 通用同一份 APK。
- 包名 `com.ruich97.nijikaahoge`，版本 1.2（versionCode 3），targetSdk 35。
  包名与旧版 `com.nijika.ahoge` 不同，因此**无法覆盖安装旧版**，会作为新应用并存。
- 本 APK 使用 Android **调试密钥库**（`~/.android/debug.keystore`，证书 DN `C=US, O=Android, CN=Android Debug`，
  证书 SHA-256 `935262ff0e7873606d8e311646907cc83ae43249b08f762b966114b82ef3830c`）签名。
  这是**开发签名，不是 Play 商店生产签名**，不能用于商店上架；改用其他密钥签名的 APK 也无法覆盖安装本包。
  本仓库不包含任何密钥库。
- 自己执行 `assembleRelease` 产出的 APK 是**未签名**的，需要用自己的密钥签名后才能安装。

## 构建

前置条件：JDK 17 或更高；Android SDK platform 35 与 build-tools 35.0.0；
设置环境变量 `ANDROID_HOME`，或在 `android/local.properties` 内写入 `sdk.dir`
（该文件是本机路径，已在 `.gitignore` 中排除）。

```sh
cd android
# Windows
.\gradlew.bat assembleRelease
# Linux / macOS
sh ./gradlew assembleRelease
```

首次使用 Gradle Wrapper 需要联网下载 Gradle 与依赖；已有缓存时可追加 `--offline`。

## 测试

仪表测试共 **17 项**，覆盖四方向镜像、小波奇回场、拖动释放速度、夹具与缓存一致性、袋出屏保留、
里程碑、EGL 重建后保留进度、60/120/240 根呆毛压力、手指边缘容错与直接命中优先等。

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.ruich97.nijikaahoge.test/android.test.InstrumentationTestRunner
```

`tools/build_render_atlas.py` 可从 `assets/frames/` 重新生成 `small_objects_atlas.png` 与
`render_atlas.json`（需要 Python + Pillow）。图集已随源码提供，常规构建不需要 Python。

## 实现要点

### 模块结构

| 路径 | 职责 |
| --- | --- |
| `MainActivity.kt` | 入口、沉浸式全屏、生命周期、传感器旋转 |
| `GameView.kt` | SurfaceView + GL 渲染线程、触摸事件分发 |
| `GameRenderer.kt` | 帧循环、letterbox 视口、按层绘制排序 |
| `game/Data.kt` | 解析 `game_data.json` → 类型 / 动画 / 实例 |
| `game/Obj.kt` | 对象模型、动画推进、几何（多边形 / 包围盒 / 三角形缓存） |
| `game/Phys.kt` | jBox2D 世界、夹具构建、休眠与唤醒 |
| `game/Sat.kt` | 分离轴（SAT）多边形与编辑器多边形 |
| `game/World.kt` | 实例创建 / 销毁、分层排序、父子同步 |
| `game/Logic.kt` | 全部游戏逻辑：拔毛、接触、喂食、里程碑、粒子、星歌、小波奇 |
| `game/Game.kt` | 对外门面：`update` / `touch*` |
| `game/Sys.kt` | Tween 与序列（Scheduler） |
| `game/Parts.kt` | 粒子发射器 |
| `game/Snd.kt` | SoundPool 音频：预载 + 按需解码 |
| `game/Vib.kt` | 振动反馈 |
| `gfx/SpriteBatch.kt` | 顶点批处理、VBO orphaning |
| `gfx/Textures.kt` | 纹理与图集加载 |
| `assets/` | `game_data.json`、`render_atlas.json`、`small_objects_atlas.png`、`frames/`、`media/` |
| `readme_pic/` | README 顶部题图 |

### 性能

- **图集合并**：呆毛与粒子共 26 张小图合并为一张 512×512 图集，保留原始像素；离屏对象裁剪，
  复用 GPU 顶点缓冲，用类型索引替代每帧过滤对象列表，复用 Tween 以减少每帧临时对象分配。
- **VBO orphaning（本版最大收益）**：`SpriteBatch.flush()` 每帧约 10 次往同一个 VBO 写
  `glBufferSubData` 后立刻 `glDrawElements`。Adreno 会做 buffer rename 所以此前不受影响；
  Mali 遇到「写还被引用的 buffer」会隐式串行化 —— 这正是「GPU 更强反而更慢」的原因。
  修复方式是在 `subData` 之前先 `glBufferData(..., DYNAMIC_DRAW)` 做 buffer orphaning。

  | 设备 | 优化前 | 优化后 |
  | --- | ---: | ---: |
  | 麒麟 960 | 约 35 fps | **稳定 60 fps** |
  | 骁龙 210 | 几乎满帧 | 稳定 60 fps，发热明显降低 |
  | 骁龙 410 | — | 稳定 60 fps |
  | 骁龙 W5+ Gen 1 | 略掉帧 | 约 55 fps |

- **不再对低端档限帧**：实测限帧对发热没有改善，只拖累拖拽手感。所有设备统一连续渲染，
  长帧由 dt 钳制吸收（上限 0.05 s，约 20 fps 下限）；`lowEndProfile` 只保留紧凑 UI 布局。
- **音频按需解码**：SoundPool 是单线程串行解码，整包 12 个音效共 28.5 秒音频，
  在低端机上解码要占满一个核约 20 秒，会把渲染线程拖到 30~47 fps 并持续 20 秒以上。
  现只预载高频短音效 —— 体积最小的 4 个（拔毛 0.6 s、收袋 0.26~0.49 s）加喂食必中的 `1`、`2`，
  共 6 个、4.52 秒。骁龙 210 恢复到 55 fps 的时间由 20 秒以上缩短到约 8~10 秒。
  撞上解码中的播放请求改为等 `onLoadComplete` 回调补播，不再按定时重试后静默丢弃。

### 兼容性与还原度

- 音效由 Opus/WebM 转为 **AAC/M4A**：Android 6 的某些 ROM 用自带 ffmpeg 插件解码 WebM/Opus，
  出错时走 `CHECK_EQ` → `abort()`，这是 Java 侧 `try/catch` 拦不住的 native abort，
  会导致进入游戏即闪退。改用原生解码器后该机稳定 60 fps。
- 最低系统由 API 24 降到 **API 21**；minSdk 21 下 lint 的 `NewApi` 为 0 条。
- 星歌四方向镜像改为**绕对象原点反射**（顶点、包围盒、碰撞多边形一致），
  修复旧版只交换 UV 导致视觉位置与碰撞位置不一致的问题。
- 小波奇移位回场前显式唤醒休眠刚体；松手时清零线速度与角速度并同步最后拖动位置；
  粒子恢复原版分层（凉 / 喜多为第 3 层，大波奇 / 星歌为第 5 层）。
- 手指触屏在头顶待拔呆毛的多边形边缘增加约 **12dp 容错**（上限 96 逻辑像素），
  直接命中的袋子与残骸仍优先，鼠标与手写笔保持精确判定。
- 振动：抓住 / 收进袋子 **9ms** 短振，分数里程碑 **55ms** 长振；API 31+ 走 `VibratorManager`，
  无马达设备静默跳过。

## 已知限制

- 本仓库源码构建出的 release APK 为未签名；未做全部机型的统一真机基准。
- 大量紧密堆叠的碰撞仍可能掉帧；圆屏裁切、长时间发热与功耗、低版本厂商音频实现仍需真机验收。
- 游戏状态只在旋转与短暂切后台时保留；进程被系统回收或主动退出后从开局开始，**没有持久化存档**。
- 未设置呆毛数量上限，未删除可收集物品，未降低 Box2D 求解迭代次数。

## 版权、授权与致谢

### 原作

| | |
| --- | --- |
| 原作名 | **Nijika's Ahoge**（虹夏的呆毛） |
| 别名 | 虹夏呆毛无限增殖中 / 呆毛可以无限增殖的虹夏酱 / 虹夏ちゃんの無限ドリトス |
| 原作链接 | <https://tamanidamani.itch.io/nijikas-ahoge> |
| 原作作者 | **ACh（ナコ）**、**TamaniDamani（塔打）** |

**授权状态：已获得两位原作者的同意。条件为署名与非营利；本移植免费、无广告，
不含任何付费内容或广告 SDK。**

### 素材版权（重要）

游戏图像、音效、角色以及其他源自原作的素材，版权**归原作者（画师）所有**，
**不适用本仓库的 MIT 协议**。本仓库不对这些素材主张任何权利，也不通过 MIT 协议授予任何使用权。
原作画面中的原作者署名保持可见。请勿使用原作者的肖像或官方宣传图作为封面，请使用自行截取的游玩截图。

### 代码协议

本移植自身编写的源代码以 **MIT 协议**发布，全文见 [`LICENSE`](LICENSE)。
该协议的授权范围**仅限于本项目自行编写的代码**，不覆盖上述任何原作素材。

### 第三方组件

- 物理引擎 [jBox2D](https://github.com/jbox2d/jbox2d) 2.2.1.1，按其原许可分发。
- 本仓库是**非官方同人移植**，与原作作者无关，也不代表其立场。
