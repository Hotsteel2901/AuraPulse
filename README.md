# AuraPulse

An Android music player for **your own audio files, playlists and honest online sources**. Local playback does not require an account. The app is licensed under [AGPL-3.0-or-later](LICENSE).

[简体中文](#简体中文) · [English](#english)

---

## 简体中文

### 现在可以做什么

- **播放本地音频**：在“资料库”选择文件或文件夹。应用使用 Android 系统文件选择器，并保存可持续读取的授权；不会移动或删除原文件。支持格式取决于 Android / 设备的解码能力。文件被移动、删除或授权撤销后，需要重新选择。
- **真实播放与后台控制**：使用 Android Media3 / ExoPlayer 读取实际音频或流，支持播放、暂停、进度拖动、上一首/下一首、随机和循环，并提供系统媒体通知与音频焦点处理。直播流通常不能拖动进度。
- **真实在线来源**：iTunes Search 返回的是**短试听片段，不是完整歌曲**；Radio Browser 提供直播电台，电台能否播放取决于该电台和网络。也可添加直接音频链接，或配置一个返回音频 URL 的通用 JSON 搜索 API。应用不再内置虚构歌曲，也不宣称支持尚未实现的 Subsonic、Jellyfin 或 Internet Archive 协议。
- **离线与歌单**：本地文件本身不需要重新下载；用户添加的直接链接或自定义 API 音频可手动下载到应用私有目录，完整接收后才标记为已下载（上限 512 MiB；直播、试听和 HLS/DASH 等分段播放清单不提供离线下载）。歌单和资料库保存在设备上，重启后仍在。
- **真实播放控制**：播放页提供队列查看、插队、追加、移动和删除；收藏、最近播放与续播；A–B 循环；前进/后退；速度、音调、播放器音量；跳过静音；定时停止或曲目结束后停止；曲目信息与仅分享标题/艺术家。播放中的定时器和循环在后台服务继续运行，进程结束后不会保留定时器。直播或其他不可拖动的音源不支持跳转和 A–B 循环，跳过静音取决于解码器。
- **设备均衡器**：12 种预设按当前设备实际提供的频率和音量范围计算，并可手动调整。预设是近似曲线而非校准值，增益过高可能失真。设备不支持时会明确提示；不宣称固定采样率、无损位深、Bit-Perfect 或虚构的 DSP 效果。
- **媒体库与外观**：收藏、已下载和最近播放筛选，以及标题、艺术家、时长排序。使用 Material 3 Expressive 实验版主题、动效与波形进度；动画遵循系统动画缩放设置。收藏、历史、续播位置只保存在本机，不包含在云备份中。
- **云端备份**：Google 登录后可手动备份/恢复歌单和在线音频链接。恢复会合并本地数据；同一歌单按设备记录的修改时间保留较新版本。**音频文件、下载副本与 Android 文件读取授权不会上传**；其他设备上的本地文件需重新导入并重新加入歌单。备份是手动快照，不是实时同步。上传会覆盖该账号上一次快照，并在操作前确认。
- **语言**：简体中文、繁體中文、English、日本語、한국어、Español、Deutsch，以及跟随系统。香港系统语言使用繁体中文资源。

应用允许播放 `http://` 电台和用户自行添加的直链，因为一些电台没有 HTTPS；这些连接未加密。通用搜索 API 必须使用 HTTPS，且不支持在 URL 内嵌用户名和密码。不要把令牌放在 URL 查询参数中：**云备份会包含在线链接**。搜索 API 配置仅保存在本机，当前不支持自定义认证头。网络服务可能限制地区、下线或改变接口，失败会显示错误，而不会伪造播放结果。

### Google 登录与云端配置

Google 登录无法通过代码绕过 Firebase 配置或 APK 签名验证。仓库包含原项目的 `app/google-services.base64`；构建时若没有本地 `app/google-services.json` 会自动解码它，但**这不保证你自己的签名、Google 登录方式或数据库已配置好**。如果使用自己的 Firebase 项目：

1. 在 Firebase 控制台添加 Android 应用，包名必须与 `app/build.gradle.kts` 中的 `applicationId` 一致（当前为 `com.aistudio.aurapulse.kxmpzq`）。下载该项目生成的 `google-services.json`，放到 `app/` 下；本地文件优先于仓库中的 base64 配置，不会被 Git 跟踪。
2. 在 Firebase Authentication 中启用 **Google** 登录。确认生成的配置含有类型为 3 的 Web OAuth client；如 OAuth 同意屏幕处于测试状态，添加测试账号。设备需要可用的 Google Play 服务及网络。
3. 将**实际用于安装 APK 的签名证书 SHA-1 / SHA-256** 加入 Firebase Android 应用设置。Debug、自己签名的 release、Google Play 应用签名可能各不相同，都应登记。修改后重新下载 `google-services.json`。当前项目的 debug 签名文件为仓库根目录的 `debug.keystore`：

   ```bash
   # 若尚无该文件，先创建一个本机 debug 签名（不要用于正式发布）
   keytool -genkeypair -keystore debug.keystore -storepass android \
     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
     -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android
   ```

4. 创建 Firestore 数据库并部署本仓库的 `firestore.rules`。应用从 `app/src/main/res/values/firebase_applet_config.xml` 的 `firestore_database_id` 读取数据库 ID；仓库原配置为一个**命名数据库**。若使用自己的 `(default)` 数据库，请同时修改该资源和 `firebase.json` 中的 Firestore 数据库配置，再部署：

   ```bash
   firebase deploy --only firestore:rules --project YOUR_PROJECT_ID
   ```

5. 在应用“设置 → 账号与云端”登录。若按钮流程失败，可尝试“使用账号选择器”；两种方式都必须通过 Firebase 身份验证。打开“登录诊断”可查看当前安装包的 SHA-1、SHA-256、包名、Firebase 项目、Google Play 服务状态及最近错误类型/代码，并复制证书指纹。该页面不会显示凭据令牌或密码。只有 Firebase 真正接受 Google 凭据时才显示登录成功；备份在服务器确认写入后才报告成功；超时的排队写入可能稍后完成，应先检查再重试。

**CI APK 特别注意**：若 Actions 没有稳定的调试签名，每次运行都会生成一枚临时密钥，因此每次 APK 的证书指纹都不同，通常不在 Firebase 登记列表中，Google 登录会失败。每次 Actions 运行摘要都会显示公开的 SHA 指纹。要长期测试登录，可由项目维护者通过 GitHub Actions Secret `AURAPULSE_DEBUG_KEYSTORE_BASE64` 提供受保护的测试用 debug keystore（别名 `androiddebugkey`，密码 `android`，符合现有构建签名配置），将对应指纹登记到 Firebase，并更新配置文件。不要把 keystore 或其 base64 内容提交到仓库、发到聊天或用于正式发布。来自 fork 的 PR 通常无法访问仓库 secrets，会退回临时密钥。即使签名匹配，也仍需正确配置 Google provider、OAuth 同意屏幕、Play 服务与网络。

新版本使用私有路径 `users/{uid}/backups/library`；规则限制只有该用户可读写。单个备份最多 700,000 字节，支持最多 2,000 个在线条目、200 个歌单、每歌单 1,000 个引用。旧版本的数据库和 Firestore 旧集合不会被删除，但其虚构曲目和旧云端条目**不会自动迁移**；请重新添加真实文件及音源后创建新备份。

### 构建和测试

需要 JDK 21、Android SDK（API 36.1）和网络访问 Google Maven / Maven Central。首次构建需准备上述 `debug.keystore`，并按需配置 Firebase。仓库提供 Gradle 9.3.1 wrapper：

```bash
./gradlew :app:assembleDebug
python3 tools/check_locales.py
# Android JVM / Robolectric 测试；完整套件还需要 Firebase Auth + Firestore 模拟器
./gradlew :app:testDebugUnitTest
# 在连接的 Android 设备或模拟器上测试实际音频服务
./gradlew :app:connectedDebugAndroidTest
```

Firestore 规则测试（Node.js 24.12+、Firebase CLI 15.34.2）：

```bash
npm install --no-save --no-package-lock @firebase/rules-unit-testing@6.0.0 firebase@13.1.0
firebase emulators:exec --only firestore --project demo-no-project "node --test firestore.test.js"
```

---

## English

### What works

- Import files or folders through Android’s Storage Access Framework with persisted read permissions. Original files are never moved or deleted. Format support depends on the device.
- Media3 / ExoPlayer plays the actual file or stream, with background media controls, seeking where supported, audio focus, shuffle and repeat.
- iTunes Search provides **previews only**; Radio Browser provides live stations. Add a direct audio URL or an HTTPS JSON API returning `title` and `streamUrl` in an array or a `results`, `tracks`, `items` or `data` array. There are no fabricated tracks or unimplemented protocol claims.
- Playlists persist locally. Explicit downloads are available for direct/custom audio URLs, not previews, live radio or segmented HLS/DASH playlists; a file is marked downloaded only after it has been fully received (512 MiB limit).
- The playback screen provides an editable queue, favorites, local history and resume, A–B loops, skip back/forward, speed, pitch, player volume, silence skipping, sleep timers, track details and metadata-only sharing. Timers and loops work in the background service but do not survive process death; seeking/loops require a seekable item and silence skipping depends on the decoder.
- Twelve approximate equalizer presets adapt to the actual device bands and limits, and can be adjusted manually. Boosts can distort loud audio. Unsupported effects are reported rather than simulated.
- Filter favorites, downloads and recently played tracks, and sort the library. Material 3 Expressive experimental theme, motion and a wavy progress indicator respect the system animation scale. Favorites, history and resume position remain local and are not included in cloud backups.
- Google/Firebase provides an **explicit, manual** backup of playlists and online links. Audio files, downloaded copies and Android document permissions are not uploaded. Restore merges rather than deleting local items; local files must be imported and added to playlists again on another device.
- Complete interface resources are supplied for English, Simplified and Traditional Chinese, Japanese, Korean, Spanish and German. Other system languages fall back to English.

HTTP streams are allowed for compatibility with radio stations and are not encrypted. Custom JSON search endpoints must use HTTPS. Configuration stays on the device, but backed-up online URLs may contain sensitive query parameters; do not place credentials in them.

### Firebase setup

A working Google sign-in requires an actual Firebase project, enabled Google provider, web OAuth client ID, Google Play services, and SHA-1/SHA-256 fingerprints for **every APK signing key**, including Play App Signing where applicable. Settings offers both Google credential flows and a diagnostics page with the installed certificate fingerprints and error codes; neither flow reports success without Firebase authentication. CI generates a different temporary debug key on each run unless a maintainer supplies a stable test-only key through the `AURAPULSE_DEBUG_KEYSTORE_BASE64` Actions secret. The public fingerprints are shown in the run summary. Never commit or share the keystore; fork PRs do not receive repository secrets. Put your project’s `google-services.json` in `app/`; it takes precedence over the existing base64 configuration. The application ID must match the Firebase Android app. See the detailed setup steps and debug keystore commands in the Chinese section above.

Create the Firestore database, set `firestore_database_id` in `app/src/main/res/values/firebase_applet_config.xml`, update `firebase.json` to target the same database, and deploy `firestore.rules`. Backups live at `users/{uid}/backups/library` and are owner-only. Backup is limited to 700,000 bytes, 2,000 online tracks, 200 playlists and 1,000 references per playlist. A server acknowledgement is required for a success message. A timed-out queued write might still complete later.

The old app’s database and Firestore collections are left untouched, but synthetic tracks and old cloud collections are not automatically imported. Re-add real files and sources before making a new backup.

### Build and test

Use JDK 21, Android SDK API 36.1, the included Gradle wrapper and access to Google Maven / Maven Central. Material 3 Expressive is an experimental 1.5.0-alpha18 dependency; newer releases require a newer compile SDK. Create `debug.keystore` as shown above, then run `./gradlew :app:assembleDebug`, `python3 tools/check_locales.py`, and `./gradlew :app:testDebugUnitTest`. The full JVM suite requires the Firebase Auth and Firestore emulators. Run `./gradlew :app:connectedDebugAndroidTest` on a device or emulator for the real media-service playback test.
