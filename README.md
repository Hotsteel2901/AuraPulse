# AuraPulse

一个把「自己的音频」放回中心的 Android 音乐播放器。不需要账号即可播放；Material 3 Expressive 完整落地；所有在线功能只联系你能看见的接口。

[简体中文](#简体中文) · [English](#english)

---

## 简体中文

### 它是什么

AuraPulse 2.0 是一次彻底重写：新的包名（`com.hotsteel.aurapulse`）、新的数据层、新的 UI。它读取你用系统文件选择器挑选的音频，在本机建立资料库，并可选地通过 GitHub Gist 备份歌单结构。

**它不是什么**：没有 Firebase，没有 Google 登录，没有统计 SDK，没有广告，也没有「无损 / Bit-Perfect / 固定采样率」这类无法自证的宣传。旧版本 README 里承诺但从未成立的登录与云端方案已被整体删除。

### 主要功能

| 领域 | 能力 |
| --- | --- |
| 播放 | Media3 / ExoPlayer + `MediaSessionService`：后台播放、系统媒体通知、锁屏与蓝牙/线控按键、音频焦点、拔出耳机自动暂停 |
| 队列 | 完整队列编辑：跳转、上移/下移、移除、清空、下一首播放、追加；队列快照在重启后可恢复 |
| 变速 | 0.5×–2.0× 速度、0.5×–2.0× 音调（互不影响）、播放器音量、跳过静音（由解码器决定是否生效） |
| A–B 循环 | 由服务端逐帧校正，界面关闭后仍然生效 |
| 睡眠定时 | 15/30/45/60/90 分钟或「本曲结束」；最后 15 秒淡出，屏幕关闭后继续计时 |
| 资料库 | SAF 导入文件或整个文件夹、持久化授权、标签与内嵌封面读取、重新扫描、丢失文件标记 |
| 组织 | 专辑 / 艺术家 / 曲目三种视图、5 种排序、3 种筛选、多选批量删除与撤销 |
| 歌单 | 新建、重命名、复制、排序、发布为 `.m3u` Gist、从 URL 导入 `.m3u` 或 JSON 歌单 |
| 搜索 | 加权排序（标题 > 艺术家 > 专辑 > 路径）、搜索历史、中英双语界面 |
| 发现 | iTunes 试听搜索（30 秒片段，不是完整歌曲）、Radio Browser 电台（按名称/国家/标签）、自定义 HTTPS JSON 源、直链添加 |
| 下载 | 远程音频可下载为应用私有副本，`.part` 完整接收后才改名，上限 512 MiB；HLS/DASH 分段流不支持离线 |
| 歌词 | 手动附加 `.lrc`、多时间戳解析、二分查找高亮、±0.5 秒偏移、跟随播放滚动；不上网抓歌词 |
| 统计 | 总收听时长、播放次数、去重曲目、连续天数、近 7 天柱状图、24 小时分布、Top 曲目与艺术家 |
| 音效 | 系统 `audiofx` 均衡器（12 条内置曲线 + 自定义预设）、BassBoost、Virtualizer、LoudnessEnhancer；全部按设备能力门控，不支持时明确提示 |
| 外观 | 6 套配色 + Android 12+ 动态取色、AMOLED 纯黑、三档对比度、圆角缩放、三档动效、触感反馈 |
| 小部件 | 桌面 4×2 播放小部件：封面、标题、上一首/播放/下一首 |
| 入口 | `aurapulse://` 深链、`audio/*` 打开方式、系统分享目标 |

### GitHub 登录与备份

登录是**可选的**，只用于备份。两种方式都直接与 `api.github.com` 通信：

1. **个人访问令牌（推荐，零配置）**：在 [github.com/settings/tokens](https://github.com/settings/tokens/new?scopes=gist&description=AuraPulse) 生成一个仅勾选 `gist` 的令牌，粘贴到「设置 → 账号」。令牌用 Android Keystore 的 AES/GCM 加密后落盘，明文不写入磁盘、不进入日志。
2. **OAuth Device Flow**：需要自建一个 GitHub OAuth App，并把 Client ID 在构建时注入：

   ```bash
   ./gradlew :app:assembleDebug -Paurapulse.github.clientId=Iv1.xxxxxxxxxxxx
   # 或
   AURAPULSE_GITHUB_CLIENT_ID=Iv1.xxxxxxxxxxxx ./gradlew :app:assembleDebug
   ```

   未注入时该按钮会直接说明缺少 Client ID，而不是假装登录。

备份写入一个私有 Gist（`aurapulse-library.json`，`schema=1`），内容包括曲目元数据、歌单、收藏与在线链接；恢复时按 `id` 与 `updatedAt` 合并，并通过 Gist 修订号检测冲突。**音频文件、下载副本、文件读取授权、播放历史都不会上传。**「发布歌单」会另外创建一个 `.m3u` Gist 并打开浏览器。

### 明确的边界

- iTunes 搜索结果是试听片段；电台能否播放取决于电台本身与网络。
- 直播流通常不能拖动进度，也不能做 A–B 循环；界面会显示 `Live` 而不是伪造时长。
- 均衡器增益过高会失真；这里没有隐藏的 DSP，也不承诺任何采样率或位深。
- 睡眠定时器随进程存活；强制停止应用后不会恢复。
- 界面语言目前为**简体中文 + English**；旧 README 声称的 8 种语言并不存在，已从文档与代码中移除。
- 本地文件被移动、删除或授权被撤销后需要重新导入；应用永远不会删除你的原文件。

### 构建与测试

需要 JDK 21、Android SDK（compileSdk 36.1 / build-tools 36.0.0）与网络访问 Google Maven / Maven Central。

```bash
./gradlew :app:assembleDebug          # 产出 app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # JVM 单元测试（LRC 解析、格式化、模型、来源校验、定时器）
python3 tools/check_locales.py        # 校验各语言资源键与格式参数一致
```

`debug.keystore` 已随仓库提供（别名 `androiddebugkey`，密码 `android`），仅用于本地调试。release 构建需要自行提供 `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD`。

当前状态：`:app:assembleDebug`、`:app:testDebugUnitTest`（63 个用例）与 `:app:lintDebug` 在本机通过。仓库**没有**在真实设备或模拟器上跑过自动化播放测试，涉及音频输出的行为请以真机为准。

### 代码结构

```
app/src/main/java/com/hotsteel/aurapulse/
├── core/           格式化与 Keystore 加密存储
├── data/
│   ├── library/    Room 实体、资料库仓库、歌词解析、下载、歌单导入
│   ├── discovery/  iTunes / Radio Browser / 自定义 JSON 源
│   ├── github/     REST 客户端、令牌会话、Gist 备份
│   └── SettingsStore.kt
├── playback/       MediaSessionService、音效、变速、睡眠定时
├── ui/             手写路由、ViewModel、组件、12 个屏幕
├── widget/         桌面小部件与媒体键转发
└── ui/theme/       动态配色、排版、形状与动效方案
```

### 许可证

[AGPL-3.0-or-later](LICENSE) © 2026 Hotsteel2901

---

## English

### What it is

AuraPulse 2.0 is a ground-up rewrite: new package (`com.hotsteel.aurapulse`), new data layer, new UI. It plays the audio you pick through Android's system file picker, keeps the library on-device, and optionally backs the playlist structure up to a GitHub Gist.

**What it is not**: no Firebase, no Google Sign-In, no analytics SDK, no ads, and no unverifiable "lossless / bit-perfect / fixed sample rate" claims. The login and cloud design promised by the previous README never worked and has been removed entirely.

### Features

- **Playback** — Media3/ExoPlayer behind a `MediaSessionService`: background playback, media notification, lock screen and Bluetooth/headset buttons, audio focus, pause on headphone unplug.
- **Queue** — jump, move up/down, remove, clear, play next, append; the queue snapshot can be restored after a restart.
- **Transport extras** — 0.5×–2.0× speed, independent 0.5×–2.0× pitch, player volume, skip silence (decoder permitting), A–B loop enforced by the service, sleep timer with a 15-second fade and an end-of-track mode that survives the screen going off.
- **Library** — SAF import for files or a whole folder, persisted grants, tag and embedded-artwork reading, rescan, missing-file detection.
- **Organisation** — albums / artists / tracks, five sort orders, three filters, multi-select delete with undo.
- **Playlists** — create, rename, duplicate, reorder, publish as a `.m3u` Gist, import `.m3u` or JSON playlists from a URL.
- **Search** — weighted ranking (title > artist > album > path), search history, English and Simplified Chinese UI.
- **Discovery** — iTunes preview search (30-second clips, **not** full tracks), Radio Browser stations by name/country/tag, custom HTTPS JSON sources, direct links.
- **Downloads** — remote audio can be copied into private app storage; a `.part` file is only renamed after a complete download; 512 MiB cap; segmented HLS/DASH streams are refused.
- **Lyrics** — attach a local `.lrc`, multi-timestamp parsing, binary-search highlighting, ±0.5 s offset, follow-playback scrolling. Nothing is fetched from the network.
- **Insights** — total listening time, play counts, distinct tracks, streak, seven-day chart, 24-hour distribution, top tracks and artists.
- **Audio effects** — the platform equalizer (12 built-in curves plus custom presets), BassBoost, Virtualizer and LoudnessEnhancer, all capability-gated and never simulated.
- **Appearance** — six palettes plus Android 12+ dynamic colour, AMOLED black, three contrast levels, corner scaling, three motion levels, haptics.
- **Widget, deep links, share target** — a 4×2 home-screen widget, `aurapulse://` links, `audio/*` VIEW intents and `ACTION_SEND`.

### GitHub sign-in and backup

Sign-in is optional and only used for backup. Both paths talk straight to `api.github.com`:

1. **Personal access token** (no setup): create a token with only the `gist` scope, paste it in Settings → Account. It is encrypted with AES/GCM in the Android Keystore; the plaintext is never written to disk or logged.
2. **OAuth Device Flow**: create a GitHub OAuth App and inject its client id at build time with `-Paurapulse.github.clientId=…` or `AURAPULSE_GITHUB_CLIENT_ID`. Without it the button states that the client id is missing instead of pretending to sign in.

Backups go to one private Gist (`aurapulse-library.json`, `schema=1`) holding track metadata, playlists, favourites and online links. Restore merges by `id` and `updatedAt` and detects conflicts through the Gist revision. **Audio, downloaded copies, document grants and listening history are never uploaded.** "Publish playlist" creates a separate `.m3u` Gist and opens it in the browser.

### Honest limits

- iTunes results are previews; radio playback depends on the station and your network.
- Live streams cannot be scrubbed or looped; the UI shows `Live` rather than inventing a duration.
- Equalizer boosts can clip; there is no hidden DSP and no sample-rate or bit-depth promise.
- The sleep timer lives with the process; force-stopping the app drops it.
- UI languages are **English and Simplified Chinese** only. The eight locales claimed by the old README never existed and have been removed from the docs and the code.
- Moved or deleted files require a re-import. The app never deletes your originals.

### Build and test

JDK 21, Android SDK (compileSdk 36.1 / build-tools 36.0.0) and access to Google Maven / Maven Central:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
python3 tools/check_locales.py
```

A debug keystore is included for local builds. Release builds need `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD`.

Status: `:app:assembleDebug`, `:app:testDebugUnitTest` (63 cases) and `:app:lintDebug` pass locally. There is **no** automated on-device playback test in this repository; anything involving audio output should be verified on a real device.

### Licence

[AGPL-3.0-or-later](LICENSE) © 2026 Hotsteel2901
