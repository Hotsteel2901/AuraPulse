<div align="center">

# 🎛️ AuraPulse

### **Material Design 3 Expressive 24-Bit / 192kHz Lossless Music Player & Universal Custom Music API Client**
**24-Bit / 192kHz 无损高保真灵动播放器 · 全球全区域通用自定义音源客户端**
**24-Bit / 192kHz 無損高傳真靈動播放器 · 全球全區域通用自訂音源用戶端**

[![CI Status](https://img.shields.io/badge/GitHub_Actions_CI-Passing-00F5D4?style=for-the-badge&logo=githubactions&logoColor=080A0F)](./.github/workflows/android-ci.yml)
[![License: AGPL-3.0-or-later](https://img.shields.io/badge/License-AGPL--3.0--or--later-10B981?style=for-the-badge&logo=gnu&logoColor=white)](./LICENSE)
[![Developer](https://img.shields.io/badge/Developer-github%40Hotsteel2901-9B5DE5?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Hotsteel2901)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-FF9F1C?style=for-the-badge&logo=kotlin&logoColor=white)](./gradle/libs.versions.toml)
[![Android SDK](https://img.shields.io/badge/Android_API-36_(Min_24)-3B82F6?style=for-the-badge&logo=android&logoColor=white)](./app/build.gradle.kts)

[**English**](#-overview) · [**简体中文**](#-项目概览-简体中文) · [**繁體中文**](#-專案概覽-繁體中文) · [**Architecture**](#-system-architecture--dsp-pipeline) · [**CI/CD**](#-automated-ci--component-matrix) · [**Developer**](#-about-the-developer-githubhotsteel2901)

</div>

---

## 🌌 Overview

**AuraPulse** is a free, libre, and borderless audiophile music player for Android engineered with **Jetpack Compose** and **Material Design 3 Expressive** principles. Designed and built by **[Hotsteel2901 (`github@Hotsteel2901`)](https://github.com/Hotsteel2901)** under the **GNU Affero General Public License v3.0 or later (`AGPL-3.0-or-later`)**, AuraPulse rejects proprietary platform lock-in in favor of an open, user-extensible **Universal Custom Music Source API** engine that supports listeners across every country and region worldwide.

### ✨ Core Highlights

- **🎧 24-Bit / 192kHz Studio Master Playback Engine**:
  - Hardware-accelerated stereo PCM synthesis and `AudioTrack` output pipeline with selectable target sample rates (`44.1 kHz Redbook`, `96.0 kHz Studio`, `192.0 kHz Master`, `384.0 kHz DXD`) and configurable low-latency buffers (`64ms`, `128ms`, `256ms`).
  - **USB / Internal Hi-Res DAC Bit-Perfect Mode** bypassing software DSP stages for direct 1:1 PCM delivery.
  - **Real-Time 28-Band FFT Spectrum & RMS Telemetry** paired with a 60fps rotating vinyl master platter.
- **🎚️ 10-Band Parametric Equalizer & Spatial DSP**:
  - Precision 64-bit floating-point cascaded biquad octave filters (`31Hz` to `16kHz`, `-12.0 dB` to `+12.0 dB`) with real-time Catmull-Rom spline frequency response visualization.
  - Pre-amplifier headroom gain, Sub-Bass Harmonic Exciter, Holographic Crossfeed Spatializer, **ReplayGain (-14 LUFS)** loudness normalization, and built-in audiophile curves (*Harman Reference*, *Flat Studio Monitor*, *Warm Tube Triode*, *Sub-Bass Surge*, *Vocal Presence*).
- **🌍 Universal Custom Music Source APIs (All Countries & Regions)**:
  - Self-add your own or known music APIs with custom base URLs, search paths, optional HTTP authentication headers (`Authorization`, `X-Api-Token`), and preferred lossless codecs.
  - **5 Built-In Protocol Adapters**:
    1. `OPEN_REST_JSON` — Universal REST JSON track/stream array parser
    2. `SUBSONIC_NAVIDROME` — OpenSubsonic / Navidrome / Airsonic self-hosted servers
    3. `RADIO_BROWSER_GLOBAL` — Global community lossless & high-bitrate radio directory
    4. `ARCHIVE_LOSSLESS` — Internet Archive 24-bit/16-bit FLAC concert & audio vault
    5. `JELLYFIN_AUDIO` — Jellyfin & Emby media server audio endpoints
  - **Worldwide ISO Country/Area Router**: Instant switching across 24+ preset regions (`GLOBAL`, `US`, `JP`, `KR`, `TW`, `HK`, `SG`, `DE`, `GB`, `FR`, `CA`, `AU`, `BR`, `IN`, etc.) plus arbitrary custom ISO 3166-1 country codes (`CN`, `NZ`, `AR`, `CH`, `AE`, `ZA`, etc.).
  - **Live Endpoint Ping & Diagnostics**: One-tap HTTP status, latency (`ms`), and payload verification.
- **💾 Offline Hi-Res FLAC Vault & Cloud Sync**:
  - Pin individual tracks or entire custom playlists to the device-local **Room SQLite + Stereo PCM Vault** for zero-buffering offline playback.
  - Real-time multi-device synchronization of custom playlists, custom API sources, 10-band EQ sound profiles, and cross-platform playback handoff via **Firebase Firestore** and **Google Sign-In (`CredentialManager`)**.
- **🌐 Instant Multi-Language Localization**:
  - Zero-restart reactive localization supporting **Simplified Chinese (`简体中文`)**, **Traditional Chinese (`繁體中文 - 台灣 / 香港`)**, **English**, **Japanese (`日本語`)**, **Korean (`한국어`)**, **Spanish (`Español`)**, and **German (`Deutsch`)**.

---

## 🇨🇳 项目概览 (简体中文)

**AuraPulse** 是一款基于 **Jetpack Compose** 与 **Material Design 3 Expressive（灵动设计）** 打造的发烧级开源无损音乐播放器，由 **[Hotsteel2901 (`github@Hotsteel2901`)](https://github.com/Hotsteel2901)** 独立设计与研发，遵循 **AGPL-3.0-or-later** 自由软件许可协议发布。

### 核心特性
1. **全球无国界通用自定义音源 API 引擎**：
   - 彻底摆脱商业流媒体平台限制，支持用户自由添加个人或公开的音乐 API（支持 **通用 REST JSON**、**OpenSubsonic / Navidrome**、**全球 Radio-Browser**、**Internet Archive 无损音乐库** 以及 **Jellyfin / Emby**）。
   - 内置覆盖全球所有国家与地区的 **ISO 国际区域路由器**（预设 `GLOBAL`、`CN`、`TW`、`HK`、`JP`、`KR`、`SG`、`US`、`DE`、`GB` 等，并支持任意自定义 ISO 国家代码）与一键 **Ping 测速诊断**。
2. **24-Bit / 192kHz 母带唱机与 10 段参量均衡器**：
   - 支持 USB / 内置高清 DAC **Bit-Perfect 源码直通模式**、前级动态余量增益、超低频偶次谐波激励器、全息声场互馈（Crossfeed）及 **ReplayGain -14 LUFS** 响度标准化。
   - 配备实时 **28 频段 FFT 频谱仪**、**64 位浮点双二阶滤波器频响样条曲线**以及云端音效配置同步。
3. **离线无损 FLAC 金库与跨平台无缝接力**：
   - 支持将自定义歌单与无损曲目一键缓存至本地离线金库，并通过 Google 账号与 Firestore 实时同步歌单、自定义音源、EQ 曲线与多设备播放进度。
4. **多语言零延迟切换与系统配置中心**：
   - 内置完整的 **简体中文**、**繁體中文**、**English**、**日本語**、**한국어** 等多国语言包，支持在顶栏或设置页即时无感切换。

---

## 🇹🇼 專案概覽 (繁體中文)

**AuraPulse** 是一款採用 **Jetpack Compose** 與 **Material Design 3 Expressive（靈動設計）** 打造的發燒級開源無損音樂播放器，由 **[Hotsteel2901 (`github@Hotsteel2901`)](https://github.com/Hotsteel2901)** 獨立設計與研發，依據 **AGPL-3.0-or-later** 自由軟體授權條款釋出。

### 核心亮點
1. **全球無國界通用自訂音源 API 引擎**：
   - 擺脫商業串流平台綁定，支援使用者自行新增個人或已知的開放音樂 API（涵蓋 **通用 REST JSON**、**OpenSubsonic / Navidrome**、**全球 Radio-Browser**、**Internet Archive 無損音樂庫** 與 **Jellyfin / Emby**）。
   - 內建支援全球所有國家與地區的 **ISO 區域智慧路由**（支援 `GLOBAL`、`TW`、`HK`、`JP`、`KR`、`SG`、`US`、`DE`、`GB` 等及任意自訂 ISO 代碼）與一鍵 **連線延遲診斷**。
2. **24-Bit / 192kHz 母帶唱機與 10 段參數等化器**：
   - 支援 USB / 內建高解析 DAC **Bit-Perfect 位元直通模式**、前級動態餘裕增益、極低頻諧波激勵器、全息音場互饋（Crossfeed）與 **ReplayGain -14 LUFS** 響度標準化。
3. **離線無損 FLAC 金庫與跨裝置無縫接力**：
   - 支援將自訂播放清單與曲目一鍵釘選至本機離線金庫，並透過 Google 登入與 Firestore 即時同步播放清單、自訂音源、EQ 設定與跨裝置播放狀態。

---

## 🏗️ System Architecture & DSP Pipeline

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                        AuraPulse M3 Expressive UI                          │
│  HiFiDeckScreen │ PlaylistsScreen │ EqualizerScreen │ CloudOffline │ Config│
│               Reactive Multi-Language Engine (LocalAuraStrings)            │
└───────────────┬───────────────────────────┬────────────────────────┬───────┘
                │                           │                        │
                ▼                           ▼                        ▼
┌──────────────────────────────┐ ┌─────────────────────┐ ┌───────────────────┐
│      HiFiAudioEngine         │ │ CustomSourceBridge  │ │ AuraFirestoreRepo │
│ • 24-Bit/192kHz PCM Synth    │ │ • OPEN_REST_JSON    │ │ • /playlists      │
│ • 10-Band Biquad Filter DSP  │ │ • SUBSONIC_NAVIDROME│ │ • /customSources  │
│ • Hardware Equalizer AudioFX │ │ • RADIO_BROWSER     │ │ • /soundProfiles  │
│ • 28-Bin Real-Time FFT       │ │ • ARCHIVE_LOSSLESS  │ │ • /syncState      │
└───────────────┬──────────────┘ └──────────┬──────────┘ └─────────┬─────────┘
                │                           │                      │
                ▼                           ▼                      ▼
┌──────────────────────────────────────────────────────┐ ┌───────────────────┐
│         OfflineVaultDatabase (Room SQLite)           │ │ Firebase Firestore│
│       + Local Stereo Lossless WAV/FLAC Storage       │ │ + Zero-Trust Rules│
└──────────────────────────────────────────────────────┘ └───────────────────┘
```

---

## ⚙️ Automated CI & Component Matrix

AuraPulse enforces strict build reproducibility, security rule auditing, and automated dependency freshness via **GitHub Actions** (`.github/workflows/android-ci.yml`) and **Dependabot v2** (`.github/dependabot.yml`).

| Component / Toolchain | Version | Role |
| :--- | :--- | :--- |
| **Android Gradle Plugin (AGP)** | `9.1.1` | Official Android build system with built-in Kotlin support |
| **Gradle Wrapper Distribution** | `9.3.1` | Build orchestration & configuration cache |
| **Kotlin & Compose Compiler** | `2.2.10` | Language runtime & Jetpack Compose compiler plugin |
| **Google DevTools KSP** | `2.3.5` | Kotlin Symbol Processing for Room SQLite codegen |
| **Compile / Target / Min SDK** | `36` / `36` / `24` | Android 16 (API 36) target with wide device compatibility |
| **Firebase BoM** | `34.17.0` | Firestore & Firebase Auth version alignment |
| **AndroidX Room** | `2.7.0` | Local offline track catalog & vault persistence |
| **Jetpack Credential Manager** | `1.5.0` | Google Sign-In (`GetSignInWithGoogleOption`) authentication |
| **Robolectric** | `4.16.1` | Local JVM & emulator-backed Firestore rules verification |
| **GitHub Actions Runner Stack** | `v4` (`JDK 21` / `Node 22`) | Automated license check, rules tests, APK build & test reports |

### Running Local Verification Commands

```bash
# 1. Run Tier 1 Firestore Security Rules Unit Tests (Node.js + Firestore Emulator)
firebase emulators:exec --only firestore --project demo-no-project "node --test firestore.test.js"

# 2. Run Tier 2 Kotlin Robolectric & Repository Rule Tests
gradle :app:testDebugUnitTest

# 3. Assemble Debug APK
gradle :app:assembleDebug
```

---

## 👨‍💻 About the Developer (`github@Hotsteel2901`)

<div align="center">

| **Developer** | **GitHub Handle** | **Focus Areas** | **License Philosophy** |
| :---: | :---: | :---: | :---: |
| **Hotsteel2901** | [**`github@Hotsteel2901`**](https://github.com/Hotsteel2901) | High-Fidelity Audio DSP · Material Design 3 Expressive · Decentralized Open Music APIs | **AGPL-3.0-or-later** Free & Libre Copyleft |

</div>

- **GitHub Profile**: [https://github.com/Hotsteel2901](https://github.com/Hotsteel2901) (`github@Hotsteel2901`)
- **In-App Interactive Showcase**: Open the **Config & About (设置与关于 / 設定與關於)** tab inside AuraPulse to interact with the real-time **Holo-Resonance Orbital Matrix Canvas**, inspect the engineering telemetry radar, and send harmonic star pulses.

---

## 📜 License (`AGPL-3.0-or-later`)

```text
Copyright (C) 2026 Hotsteel2901 (github@Hotsteel2901 / https://github.com/Hotsteel2901)
SPDX-License-Identifier: AGPL-3.0-or-later
```

This program is free software: you can redistribute it and/or modify it under the terms of the **GNU Affero General Public License** as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version (**`AGPL-3.0-or-later`**). See the [LICENSE](./LICENSE) file for the full text.
