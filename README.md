<p align="center">
  <img src="docs/logo.jpg" alt="Xime Logo" width="600">
</p>

<h1 align="center">Xime - Wubi / Pinyin Input Method for Android</h1>

<p align="center">
  <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">繁體中文</a>
</p>

[<img src="https://f-droid.org/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/com.kingzcheung.xime)

<sub>This F-Droid badge points to the upstream Xime package. This fork is built from source.</sub>


[Windows Version](https://github.com/ximeiorg/XimeYao) | [Linux Version](https://github.com/ximeiorg/XimeChe) | [Predictive Text Model](https://github.com/ximeiorg/predictive-text) | [Handwriting Model](https://github.com/ximeiorg/ochwpro)

An Android input method built on the [Rime](https://rime.im/) engine, designed for efficient Chinese text input with Wubi (五笔) and Pinyin support.

## About this fork

> **This is a personal fork: [Xime-Gboardlike](https://github.com/Murchey/Xime-Gboardlike), based on the original [Xime](https://github.com/ximeiorg/Xime) by Kingz Cheung and contributors. It is not the upstream project and does not speak for the original author.**

**Design goal.** This fork reworks the input experience around the habits of **Gboard** and **fcitx5-android** users: a quieter, roomier candidate bar; Gboard-inspired color themes; straightforward font and personalization controls; practical clipboard retention options; and a build flow that is friendly to self-installation. The goal is to build a modern, customizable, plugin-capable input method together with the community — not to replace the upstream design.

**Changes in this fork (on top of upstream):**

- Font picker: system default / bundled font / imported fonts, applied globally.
- A curated modern theme set — WeChat, QQ, Telegram, X, GitHub, ChatGPT, Gboard Gray, Gboard Black, and Claude — with light/dark palettes and independent light/dark selection, plus a personalization color panel with JSON import/export.
- Gboard-style candidate bar: wider cells, vertical separators, down-chevron for more candidates; candidate code hints are off by default.
- Clipboard retention from 1 hour up to 90 days, manual history-size input, and migration from the old day-based setting.
- A public signing key and `build_apk.bat` for signed, ABI-split personal builds.

**Respect for the original work.** The Rime engine integration, plugin system, architecture, documentation, and the vast majority of the code are the work of **Kingz Cheung and the Xime contributors**. This fork only adds changes on top of their work and is released under the same **GPLv3** license. All credit for the original project remains with its authors. Upstream is the source of truth: if you want the original behavior, official releases, or official support, please use [ximeiorg/Xime](https://github.com/ximeiorg/Xime).

**Why publish the signing key?** A single build cannot satisfy every taste, workflow, or device. Publishing the community keystore (with its password in `app/keystore.properties`) lets anyone fork this project, adjust it, and produce a signed APK that can be installed without waiting for a maintainer. The key is for **personal, sideloaded builds only** — do not use it for app-store distribution or to impersonate the upstream project. Replace it with your own key for public releases.

**Not the right fit?** This repository is an experiment in Gboard/fcitx5-android-style personalization. If its choices do not suit you, using the original Xime project is explicitly encouraged.

---

> This input method supports both Wubi (五笔) and Pinyin input. The author primarily uses Wubi with Pinyin as a fallback, so resources lean toward Wubi.

<table align="center">
  <tr>
    <td><img src="docs/Screenshot/full_keyboard_light.jpg" width="180"><br><p align="center">Full Keyboard (Light)</p></td>
    <td><img src="docs/Screenshot/full_keyboard_dark.jpg" width="180"><br><p align="center">Full Keyboard (Dark)</p></td>
    <td><img src="docs/Screenshot/全键盘_下滑_light.jpg" width="180"><br><p align="center">Radical Swipe</p></td>
    <td><img src="docs/Screenshot/shotcut_light.jpg" width="180"><br><p align="center">Quick Actions</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/floating.jpg" width="180"><br><p align="center">Floating Keyboard</p></td>
    <td><img src="docs/Screenshot/t9_pinyin.jpg" width="180"><br><p align="center">T9 Pinyin</p></td>
    <td><img src="docs/Screenshot/number.jpg" width="180"><br><p align="center">Numpad</p></td>
    <td><img src="docs/Screenshot/symbol.jpg" width="180"><br><p align="center">Symbols</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/hw.png" width="180"><br><p align="center">Handwriting</p></td>
    <td><img src="docs/Screenshot/hw2.png" width="180"><br><p align="center">Handwriting (Candidates)</p></td>
    <td><img src="docs/Screenshot/voice.jpg" width="180"><br><p align="center">Voice Input</p></td>
    <td><img src="docs/Screenshot/emoji.jpg" width="180"><br><p align="center">Emoji Keyboard</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/theme_light.jpg" width="180"><br><p align="center">Theme Settings (Light)</p></td>
    <td><img src="docs/Screenshot/theme_dark.jpg" width="180"><br><p align="center">Theme Settings (Dark)</p></td>
    <td><img src="docs/Screenshot/plugin_light.jpg" width="180"><br><p align="center">Plugin Manager</p></td>
    <td><img src="docs/Screenshot/扩展商店.png" width="180"><br><p align="center">Extension Store</p></td>
  </tr>
</table>

## Features

- **Multiple Input Schemas** - Built-in Wubi 86/98, Pinyin, and mixed schemas; supports custom schemas (Shuangpin, Stroke, etc.) via the schema marketplace or wireless import
- **Rime Engine** - Powered by the mature and reliable Rime input method engine for accurate Chinese input
- **Rich Keyboard Layouts** - QWERTY full keyboard, T9 Pinyin, Stroke 9-key, Handwriting, Numpad (with calculator)
- **Floating Keyboard** - Floating card style with drag support, semi-transparent rounded design
- **Voice-to-Text** - Local offline ASR (built-in streaming zipformer2 engine) plus online ASR plugins (FunAsr, Volc, etc.)
- **AI Enhancement** - Transformer-based predictive text for faster input
- **Clean UI** - Material Design 3, light/dark themes with multiple color schemes
- **Keyboard Adjustment** - Adjustable keyboard height and position
- **Toolbar Customization** - Customizable toolbar button layout and functions
- **Haptic Feedback** - Adjustable sound and vibration intensity
- **Swipe Gestures** - Cursor movement, deletion, symbol input via swipe gestures
- **Clipboard Manager** - Clipboard history with quick send and pinning
- **Clipboard Sync** - Bidirectional clipboard sync with remote devices via plugins (WebDAV, ximed, etc.)
- **Optional Candidate Coding Hints** - Display Wubi codes for candidates to aid learning (off by default; enable in Layout & Display)
- **Radical Display** - Swipe down on keys to show Wubi radicals for memory aid
- **Physical Keyboard Support** - Floating candidate bar when using hardware/bluetooth keyboards
- **WebDAV Sync** - Backup and restore schemas and settings via WebDAV
- **Plugin Marketplace** - Extensible Lua plugins (emoji, clipboard sync, online ASR, etc.) via the built-in marketplace

## Requirements

- Android 9.0 (API 28) or later

## Installation

### Download

Choose the APK matching your device architecture:
- **arm64-v8a**: Modern phones (recommended for most users)
- **armeabi-v7a**: Older 32-bit phones
- **x86_64**: Emulators
- **universal**: All architectures (larger file size)

### From Releases

1. Download the latest APK from [this fork's Releases](https://github.com/Murchey/Xime-Gboardlike/releases) (upstream releases are also available at [ximeiorg/Xime](https://github.com/ximeiorg/Xime/releases))
2. Install the application
3. Enable Xime in system input method settings
4. Set Xime as the current input method

### Plugins (Optional)

Plugins are Lua-script plugins (`.xipk` format), installable and enabled from the app's Settings > Extension Store:
- **kaomoji**: Kaomoji text emoticons
- **meme-bunny**: Funny bunny sticker pack (8 stickers)
- **xime-fluent-emoji**: Fluent UI 3D-style emoji plugin (222 curated 3D emojis, 9 categories)
- **funasr-asr**: Alibaba Bailian FunAsr online speech recognition
- **volc-asr**: Volcano Engine online speech recognition
- **webdav-clipboard-sync**: WebDAV-based clipboard sync
- **ximed-clipboard-sync**: ximed-service-based clipboard sync

For the full plugin list, see the [Plugin Center](https://ime.ximei.me/plugin-list.html), or browse and install directly from the app's Settings > Extension Store.

### Build from Source

1. Clone the project and build the APK
2. Install the application
3. Enable Xime in system input method settings
4. Set Xime as the current input method

## Documentation

For detailed documentation, visit [https://ime.ximei.me](https://ime.ximei.me).

- [FAQ](https://ime.ximei.me/faq.html)
- [Rime Schemas List](https://ime.ximei.me/rime-list.html)
- [Plugin List](https://ime.ximei.me/plugin-list.html)
- [AI Models List](https://ime.ximei.me/model-list.html)

## Building

```bash
# Clone this fork with submodules
git clone --recursive https://github.com/Murchey/Xime-Gboardlike.git

# Or initialize submodules in an existing clone
git submodule update --init --recursive

# Build Release APK with the included community keystore
./gradlew assembleRelease
```

### Signed personal builds

This fork ships a public community keystore (`app/xime-gboard.jks` + `app/keystore.properties`) so that anyone can produce an installable, consistently signed APK without extra setup.

On Windows, run `build_apk.bat` from the repository root. It builds signed release APKs for each ABI and copies them to `dist/`:

```bat
build_apk.bat
```

For public distribution, replace the community keystore with your own key.

> Build intermediates (`app/build/`, `app/.cxx/`) can grow to several GB. They are generated, not source, and can be deleted safely when you are done building.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material Design 3
- Rime (librime)
- JNI (Native C++)

## Contributing

Contributions welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a PR.

Core rules:
- **File an Issue first** — All changes require a prior discussion via an Issue
- **Minimal changes** — PRs must contain only the minimum changes needed
- **GPG signing** — All commits must be GPG-signed

## Acknowledgments

- [Rime](https://rime.im/) - Input method engine
- [Trime](https://github.com/osfans/trime) - Configuration reference
- [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) - Keyboard layout reference
- [onnxruntime](https://github.com/microsoft/onnxruntime) - ONNX inference runtime for predictive text and speech recognition models

## License

GPLv3 License

Copyright © 2026 Kingz Cheung

The Xime name, logo and other brand assets are **not** covered by the GPLv3 license.
See [TRADEMARKS.md](TRADEMARKS.md) for details.
