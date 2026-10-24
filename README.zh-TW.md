<p align="center">
  <img src="docs/logo.jpg" alt="Xime Logo" width="600">
</p>

<h1 align="center">Xime（曦碼） - 五筆/拼音輸入法</h1>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a>
</p>

[<img src="https://f-droid.org/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/com.kingzcheung.xime)

<sub>此 F-Droid 徽章指向上游專案 Xime；本分支需自行建置。</sub>


[Xime 輸入法 (Windows 版)](https://github.com/ximeiorg/XimeYao) | [Xime 輸入法 (Linux 版)](https://github.com/ximeiorg/XimeChe) | [聯想詞預測模型](https://github.com/ximeiorg/predictive-text) | [手寫輸入法模型](https://github.com/ximeiorg/ochwpro)


一款基於 <a href="https://rime.im/">Rime</a> 引擎構建的 Android 五筆/拼音輸入法，專注於簡潔高效的中文輸入體驗。

## 關於本分支

> **這是個人分支：[Xime-Gboardlike](https://github.com/Murchey/Xime-Gboardlike)，基於 Kingz Cheung 與貢獻者維護的上游專案 [Xime](https://github.com/ximeiorg/Xime)。本分支不是上游專案，也不代表原作者。**

**設計理念。** 本分支圍繞 **Gboard** 與 **fcitx5-android** 的使用習慣進行改造：候選欄更安靜、更寬鬆；採用 Gboard 風格主題色；字型與個人化入口更直接；剪貼簿保留策略更實用；同時保持對個人安裝友善的建置方式。目標是與社群一起打造一款現代化、可自訂、支援外掛的優秀輸入法，而不是取代上游的設計。

**本分支的修改（在上游基礎上）：**

- 字型選擇：系統預設 / 內建字型 / 已匯入字型，一次選擇全域生效。
- 精選現代主題：微信、QQ、Telegram、X、GitHub、ChatGPT、Gboard 灰、Gboard 黑、Claude；每套皆適配淺色/深色，並支援淺色與深色分別選擇；另有支援 JSON 匯入匯出的個人化調色面板。
- Gboard 風格候選欄：更寬的候選格、縱向分隔線、更多候選下箭頭；編碼註解預設關閉。
- 剪貼簿自動清理支援 1 小時到 90 天，保留筆數支援手動輸入，並相容舊版以天為單位的設定。
- 提供公開打包金鑰與 `build_apk.bat`，便於產生依 ABI 分開的已簽章個人安裝包。

**尊重原作者的程式碼與勞動。** Rime 引擎整合、外掛體系、整體架構、文件以及絕大部分程式碼，均來自 **Kingz Cheung 與 Xime 貢獻者**。本分支只在其成果之上做增量修改，並沿用 **GPLv3** 授權條款；原專案的全部榮譽歸原作者與貢獻者所有。上游才是專案的源頭：如果你希望使用原始行為、官方發布或官方支援，請使用 [ximeiorg/Xime](https://github.com/ximeiorg/Xime)。

**為什麼公開打包金鑰？** 單一建置產物不可能完全符合所有人的審美、習慣與裝置環境。公開社群 keystore（密碼同樣放在 `app/keystore.properties`），是為了讓任何人都能 fork 本專案、依自己的喜好修改，並直接產出可安裝、簽章一致的 APK，而不必等待維護者。此金鑰**僅供個人側載建置**，請勿用於應用程式商店發布，也不要用來冒充上游專案；正式對外發布請替換為你自己的金鑰。

**如果不適合你？** 本儲存庫只是對 Gboard / fcitx5-android 風格個人化的一次嘗試。如果你的習慣與本分支不同，**推薦直接使用原儲存庫專案**。

---

> 本輸入法支援五筆/拼音輸入，只是本人以五筆為主，拼音為輔，因此資源會傾向於五筆為主。

<table align="center">
  <tr>
    <td><img src="docs/Screenshot/full_keyboard_light.jpg" width="180"><br><p align="center">全鍵盤（亮色）</p></td>
    <td><img src="docs/Screenshot/full_keyboard_dark.jpg" width="180"><br><p align="center">全鍵盤（暗色）</p></td>
    <td><img src="docs/Screenshot/全键盘_下滑_light.jpg" width="180"><br><p align="center">字根下滑</p></td>
    <td><img src="docs/Screenshot/shotcut_light.jpg" width="180"><br><p align="center">快捷操作</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/floating.jpg" width="180"><br><p align="center">懸浮鍵盤</p></td>
    <td><img src="docs/Screenshot/t9_pinyin.jpg" width="180"><br><p align="center">T9 九宮格拼音</p></td>
    <td><img src="docs/Screenshot/number.jpg" width="180"><br><p align="center">數字鍵盤</p></td>
    <td><img src="docs/Screenshot/symbol.jpg" width="180"><br><p align="center">符號鍵盤</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/hw.png" width="180"><br><p align="center">手寫輸入</p></td>
    <td><img src="docs/Screenshot/hw2.png" width="180"><br><p align="center">手寫找字（候選）</p></td>
    <td><img src="docs/Screenshot/voice.jpg" width="180"><br><p align="center">語音輸入</p></td>
    <td><img src="docs/Screenshot/emoji.jpg" width="180"><br><p align="center">Emoji 鍵盤</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/theme_light.jpg" width="180"><br><p align="center">主題設定（亮色）</p></td>
    <td><img src="docs/Screenshot/theme_dark.jpg" width="180"><br><p align="center">主題設定（暗色）</p></td>
    <td><img src="docs/Screenshot/plugin_light.jpg" width="180"><br><p align="center">外掛管理</p></td>
    <td><img src="docs/Screenshot/扩展商店.png" width="180"><br><p align="center">擴充商店</p></td>
  </tr>
</table>

## 功能特點

- **多種輸入方案** - 內建五筆86/98、拼音、混輸方案，支援自訂（雙拼、筆畫等），可透過方案市場下載或無線匯入
- **Rime 引擎** - 使用成熟穩定的 Rime 輸入法引擎，精準可靠的中文輸入體驗
- **豐富鍵盤佈局** - QWERTY 全鍵盤、T9 九宮格拼音、九宮格筆畫、手寫、數字（含計算機）
- **懸浮鍵盤** - 懸浮卡片樣式，支援拖拽移動、半透明圓角設計
- **語音轉文字** - 本地離線語音辨識（內建串流 zipformer2 引擎），也支援線上 ASR 外掛（FunAsr、Volc 等）
- **AI 智能增強** - 基於 Transformer 的聯想詞預測，輸入更高效
- **簡潔介面** - Material Design 3 風格，支援淺色/深色主題及多種配色方案
- **鍵盤調節** - 支援鍵盤高度調整和位置移動
- **工具列定製** - 可自訂工具列按鈕佈局和功能
- **按鍵反饋** - 可調節音效和振動強度
- **滑動手勢** - 游標移動、刪除、符號輸入等滑動手勢操作
- **剪貼簿管理** - 剪貼簿歷史記錄，支援快捷傳送和置頂
- **剪貼簿同步** - 透過外掛與遠端裝置雙向同步剪貼簿（WebDAV、ximed 等）
- **候選詞編碼提示** - 候選詞可顯示五筆編碼，輔助學習（預設關閉，可在「佈局與顯示」開啟）
- **字根顯示** - 下滑按鈕顯示五筆字根，方便健忘用戶
- **實體鍵盤支援** - 連接實體/藍牙鍵盤時顯示浮動候選欄
- **WebDAV 同步** - 透過 WebDAV 備份和還原方案與設定
- **外掛市場** - 透過內建擴充商店安裝可擴充 Lua 外掛（表情、剪貼簿同步、線上 ASR 等）

## 系統需求

- Android 9.0 (API 28) 及以上

## 安裝

### 主程式下載

選擇對應架構的 APK：
- **arm64-v8a**: 適用於大多數現代手機（**絕大部分人的手機都是這個**）
- **armeabi-v7a**: 適用於舊款32位元手機
- **x86_64**: 適用於模擬器
- **universal**: 包含所有架構，體積較大

### 外掛下載（選用）

外掛為 Lua 指令碼外掛（.xipk 格式），可在主應用程式「設定 > 擴充商店」中安裝和啟用：
- **kaomoji**: 顏文字外掛（提供精選顏文字）
- **meme-bunny**: 惡搞兔表情包外掛（提供8個表情）
- **xime-fluent-emoji**: Fluent UI 3D 風格表情外掛（222 個精選 3D 表情，9 大分類）
- **funasr-asr**: 阿里百煉 FunAsr 線上語音辨識
- **volc-asr**: 火山引擎線上語音辨識
- **webdav-clipboard-sync**: 基於 WebDAV 的剪貼簿同步
- **ximed-clipboard-sync**: 基於 ximed 服務的剪貼簿同步

更多外掛請查看 [外掛中心列表](https://ime.ximei.me/plugin-list.html)，或直接到手機應用程式「設定 > 擴充商店」中瀏覽安裝。

### 從 Release 下載

1. 在 [本分支 Releases](https://github.com/Murchey/Xime-Gboardlike/releases) 頁面下載最新版本的 APK（也可使用 [上游 Releases](https://github.com/ximeiorg/Xime/releases)）
2. 安裝應用程式
3. 在系統設定中啟用 Xime 輸入法
4. 將 Xime 設為目前輸入法

### 國內下載

由於 APK 包是透過 GitHub Actions 自動構建的，國內的倉庫沒有免費的功能使用，因此如果你覺得 GitHub Release 下載不穩定，請自行構建安裝，或者透過 [https://github.akams.cn](https://github.akams.cn) 來下載。

### 手動構建安裝

1. 克隆專案並構建 APK
2. 安裝應用程式
3. 在系統設定中啟用 Xime 輸入法
4. 將 Xime 設為目前輸入法

## 使用文件

詳細使用說明請檢視 [使用文件](https://ime.ximei.me)。

- [常見問題 FAQ](https://ime.ximei.me/faq.html)
- [Rime 方案列表](https://ime.ximei.me/rime-list.html)
- [外掛列表](https://ime.ximei.me/plugin-list.html)
- [AI 模型列表](https://ime.ximei.me/model-list.html)

## 構建

```bash
# 克隆本分支（包含子模組）
git clone --recursive https://github.com/Murchey/Xime-Gboardlike.git

# 或者在已克隆的專案中初始化子模組
git submodule update --init --recursive

# 使用儲存庫內公開憑證構建 Release APK
./gradlew assembleRelease
```

### 個人簽章打包

本分支隨儲存庫提供公開社群憑證（`app/xime-gboard.jks` + `app/keystore.properties`），無需額外設定即可產生可安裝、簽章一致的 APK。

Windows 下在儲存庫根目錄執行 `build_apk.bat`，會產生依 ABI 分開的已簽章 release APK，並複製到 `dist/`：

```bat
build_apk.bat
```

如需對外正式發布，請替換為你自己的金鑰。

> 建置中間產物（`app/build/`、`app/.cxx/`）可能達到數 GB；它們是生成檔案而非原始碼，打包完成後可以安全刪除。

## 技術棧

- Kotlin
- Jetpack Compose
- Material Design 3
- Rime (librime)
- JNI (Native C++)

## 貢獻

歡迎貢獻！在提交 PR 之前，請先閱讀 [CONTRIBUTING.md](CONTRIBUTING.md) 瞭解貢獻流程。

核心規則：
- **先提 Issue** — 所有改動必須先建立 Issue 討論
- **最小修改** — PR 只包含所需的最小改動
- **GPG 簽章** — 所有 commit 必須 GPG 簽章

## 致謝

- [Rime](https://rime.im/) - 中州韻輸入法引擎
- [Trime](https://github.com/osfans/trime) - 同文輸入法，設定參考
- [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) - 鍵盤佈局參考
- [onnxruntime](https://github.com/microsoft/onnxruntime) - 聯想詞預測與語音辨識的 ONNX 推論引擎

## 授權條款

GPLv3 License

Copyright © 2026 Kingz Cheung

"Xime" 名稱、Logo 及其他品牌資產**不屬於** GPLv3 開源授權範圍，詳見 [TRADEMARKS.md](TRADEMARKS.md)。
