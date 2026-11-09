<p align="center">
  <img src="docs/logo.jpg" alt="XimeGboard Logo" width="600">
</p>

<h1 align="center">XimeGboard - 五筆/拼音輸入法</h1>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a>
</p>

[<img src="https://f-droid.org/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/com.kingzcheung.xime)

<sub>此 F-Droid 徽章指向上游專案 Xime。本分支未在 F-Droid 發布，安裝包請從 [GitHub Releases](https://github.com/Murchey/Xime-Gboardlike/releases) 或 [Gitee Releases](https://gitee.com/Murchey/Xime-Gboardlike/releases) 下載。</sub>


[Xime 輸入法 (Windows 版)](https://github.com/ximeiorg/XimeYao) | [Xime 輸入法 (Linux 版)](https://github.com/ximeiorg/XimeChe) | [聯想詞預測模型](https://github.com/ximeiorg/predictive-text) | [手寫輸入法模型](https://github.com/ximeiorg/ochwpro)


一款基於 <a href="https://rime.im/">Rime</a> 引擎構建的 Android 五筆/拼音輸入法，專注於簡潔高效的中文輸入體驗。

## UI 風格不一定適合所有人

> **這是本分支最重要的一條說明，請先讀它再決定要不要用。**

本分支對介面做了較大幅度的改造：候選欄更寬、候選之間有豎分隔線、鍵帽圓角更大、主題配色向 Gboard 靠攏、工具列與選單都重新排過。**這些改動是審美選擇，不是普適改進**——它們可以讓一部分人覺得更清爽，也同樣會讓另一部分人覺得佔地方、不習慣、不如上游順手。

幾個具體例子，說明「同一種設計有人喜歡有人不喜歡」：

- **候選欄的豎分隔線**：有人覺得它讓每個候選邊界清楚、點選不容易錯；也有人覺得它純佔橫向空間，候選字多的時候可用候選反而變少。
- **更寬的候選格**：有人喜歡寬鬆不擠手；也有人一螢幕能看到的候選數變少了。
- **Gboard 風格配色**：有人覺得克制耐看；也有人更喜歡上游原本的觀感。

這些都沒有對錯。**如果你覺得哪裡不對勁，最直接的做法是換成適合你的版本**：

| 你的需求 | 建議 |
|---|---|
| 想要簡潔、接近原版的行為 | 直接用上游 [ximeiorg/Xime](https://github.com/ximeiorg/Xime) |
| 想用本分支但不喜歡某處配色/鍵帽 | 設定 → 主題與定製 → 個人化調色，自己調；或換主題 |
| 有程式能力，想徹底按自己喜好改 | fork 本倉庫自行修改（已公開社群 keystore，見下文「為什麼公開打包密鑰」） |

**我們不認為本分支的 UI 比上游更好，只認為它是另一種選擇。** 上游才是專案的源頭，它的設計經過更長時間打磨；本分支只是把 Gboard / fcitx5-android 的一些習慣搬了過來，供口味相投的人使用。

---

## 關於本分支

> **這是個人分支：[Xime-Gboardlike](https://github.com/Murchey/Xime-Gboardlike)，基於 Kingz Cheung 與貢獻者維護的上游專案 [Xime](https://github.com/ximeiorg/Xime)。本分支不是上游專案，也不代表原作者。**

**設計理念。** 本分支圍繞 **Gboard** 與 **fcitx5-android** 的使用習慣進行改造：候選欄更安靜、更寬鬆；採用 Gboard 風格主題色；字型與個人化入口更直接；剪貼簿保留策略更實用；同時保持對個人安裝友善的建置方式。目標是與社群一起打造一款現代化、可自訂、支援外掛的優秀輸入法，而不是取代上游的設計。

**本分支的修改（在上游基礎上）：**

- 候選排序更聰明：內建一個「搭配模型」，會看著前文把更搭的詞往前提——打了「就」，「緒」自動提前拼成「就緒」。裝上即用，無需任何設定。
- 同音字選得更準：拼音/雙拼總在一堆同碼字裡挑花眼？實測「第 1 個就是對的」從約 6 成提到 9 成，按數字翻候選的次數明顯變少；五筆這類編碼唯一的輸入不受影響。
- 模型你說了算：模型就是個本地檔案，可匯入/匯出/刪除/停用，不連網、不上傳，資料放哪由你掌控；聯想模型還可以用你自己累積的輸入訓練專屬版本。技術細節見 [語法模型（gram）](#語法模型gram)。
- 字型選擇：系統預設 / 內建字型 / 已匯入字型，一次選擇全域生效。
- 精選現代主題：微信、QQ、Telegram、X、GitHub、ChatGPT、Gboard 灰、Gboard 黑、Claude；每套皆適配淺色/深色，並支援淺色與深色分別選擇；另有支援 JSON 匯入匯出的個人化調色面板。
- Gboard 風格候選欄：更寬的候選格、縱向分隔線、更多候選下箭頭；編碼註解預設關閉。
- 剪貼簿自動清理支援 1 小時到 90 天，保留筆數支援手動輸入，並相容舊版以天為單位的設定；過期條目依計時器主動刪除。
- 輸入方案頁提供「部署方案 / 重新編譯」智慧按鈕，可一鍵清除已編譯快取釋放儲存空間。
- 提供公開打包金鑰與 `build_apk.bat`，便於產生依 ABI 分開的已簽章個人安裝包。


**近期更新（V1.1.3，距 V1.1.2 共 14 個提交）：**

- **萬象拼音可用性修復**：三個疊加的缺陷一併解決——方案引用的語法模型檔案不存在，導致載入卡死/無回應；方案自帶的 `custom/` 補丁未被初始化，導致簡拼等規則失效、長編碼不出候選；`gram` 匯入上限從 128MB 放寬到 2GB（萬象 LTS 的模型有 418MB）。實測全拼 `nihaoshijie` 每步都有候選、編碼氣泡正常分段，選詞後不殘留；九鍵同樣正常。
- **鍵盤真正鋪滿螢幕寬度**：豎屏時部分 ROM 會報出非 0 的左右 inset，此前照單全收，導致鍵盤兩側各留一條邊。現在只在橫屏避讓（橫屏的挖孔/瀏海/三鍵導覽確實需要讓開）。
- **符號面板符號半角化**：全形 `＠` → `@`（半角才能觸發微信等聊天工具的 @ 提醒）、全形 `＿` → `_`（全形底線在程式碼、郵件裡不可用）。
- **匯入方案更可靠**：部署前自動清理 YAML 檔案開頭的 UTF-8 BOM（記事本另存常帶，會讓 librime 解析整份設定失敗），並檢查每個啟用方案是否真的編譯出產物——以前「部署成功」可能是假的，方案實際不可用。
- **鍵盤高度與寬度支援橫豎屏分別設定**：此前是單一儲存鍵，豎屏調窄後橫屏也跟著變窄。
- **主題預覽與實機對齊**：`?123` 與 Enter 鍵在預覽裡也使用實機的 24dp 大圓角；「強調第一個候選」開關現在會即時反映到預覽。
- **Gboard 主題更還原**：Gboard 灰/黑不再強調第一個候選詞，候選文字改為純黑（淺色底）/純白（深色底），不再是灰色。
- **新增自訂配色項目**：Enter 鍵背景、Enter 鍵圖示色（可「跟隨強調色」）、強調候選開關。
- **跟進上游功能**：語音辨識長文字可捲動檢視（不再截斷 4 行）、新增 `cycle_schema` 方案輪換動作、補齊 OpenCC 簡繁轉換資料。
- **長按氣泡補齊消失動畫**：放開時氣泡會縮回並淡出（此前是瞬間消失）。
- **外掛**：備份擴充點逾時從 180s 放寬到 300s，且逾時不再把外掛標記為停用（慢速上傳不再被誤判成無限迴圈）。

**尊重原作者的程式碼與勞動。** Rime 引擎整合、外掛體系、整體架構、文件以及絕大部分程式碼，均來自 **Kingz Cheung 與 Xime 貢獻者**。本分支只在其成果之上做增量修改，並沿用 **GPLv3** 授權條款；原專案的全部榮譽歸原作者與貢獻者所有。上游才是專案的源頭：如果你希望使用原始行為、官方發布或官方支援，請使用 [ximeiorg/Xime](https://github.com/ximeiorg/Xime)。

**為什麼公開打包金鑰？** 單一建置產物不可能完全符合所有人的審美、習慣與裝置環境。公開社群 keystore（密碼同樣放在 `app/keystore.properties`），是為了讓任何人都能 fork 本專案、依自己的喜好修改，並直接產出可安裝、簽章一致的 APK，而不必等待維護者。此金鑰**僅供個人側載建置**，請勿用於應用程式商店發布，也不要用來冒充上游專案；正式對外發布請替換為你自己的金鑰。

**如果不適合你？** 本儲存庫只是對 Gboard / fcitx5-android 風格個人化的一次嘗試。如果你的習慣與本分支不同，**推薦直接使用原儲存庫專案**。

---

> 本地輸入法內建霧凇拼音的所有方案，本人以小鶴雙拼為主，全拼為輔，因此資源注重全拼和雙拼。如果你是五筆使用者，建議使用上游專案，或者匯入基於 RIME 打造的更好五筆方案使用。

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

- **多種輸入方案** - 內建霧凇拼音全系方案（含小鶴/自然碼/微軟等各雙拼），另保留五筆86/98；支援自訂方案（筆畫等），可透過方案市場下載或無線匯入
- **Rime 引擎** - 使用成熟穩定的 Rime 輸入法引擎，精準可靠的中文輸入體驗
- **豐富鍵盤佈局** - QWERTY 全鍵盤、T9 九宮格拼音、九宮格筆畫、手寫、數字（含計算機）
- **懸浮鍵盤** - 懸浮卡片樣式，支援拖拽移動、半透明圓角設計
- **語音轉文字** - 本地離線語音辨識（內建串流 zipformer2 引擎），以及**免外掛的內建線上識別**（阿里百煉 / MiMo / MiniMax / OpenAI，填 Key 即用，位址可自訂）；也可繼續使用線上 ASR 外掛（FunAsr、Volc 等）
- **AI 智能增強** - 基於 Transformer 的聯想詞預測，輸入更高效
- **上下文語法模型（gram）** - 內建八元語法模型，候選隨上文整句重排；模型為使用者可控的本地檔案
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

## 語法模型（gram）

**上游 Xime 沒有這套設計。** 上游的智慧預測運作在**應用層**：ONNX 聯想模型 + 使用者輸入自學習的二元/三元組快取（JSON 持久化），融合後對聯想詞排序——`.gram` 在上游只是檔案管理器裡的一般二進位檔案。本分支在其之上增加了一條完整的 **Rime 引擎層**語法模型（librime-octagram `*.gram`）鏈路：

| | 上游 Xime | 本分支 |
| --- | --- | --- |
| 預測層次 | 應用層（ONNX 聯想 + 使用者 n-gram 快取） | 同上游，另加 Rime 引擎層八元語法模型 |
| 語法模型 | — | 內建 `XimeGboard.gram`（字元 n-gram，約 26MB），隨 APK 分發 |
| 生效範圍 | 聯想候選 | 全部已啟用方案的候選排序（一鍵全域啟用） |
| 模型管理 | — | App 內匯入 / 匯出 / 刪除 / 停用，帶檔案格式校驗 |
| 自訂模型 | — | 可匯入任意相容的 `.gram`；聯想模型的完整訓練流水線在 [`ModelTrainning/`](ModelTrainning/) |

**運作原理。** 啟用時向每個方案的自訂設定寫入 `grammar/language` 與 `translator/contextual_suggestions`，由 Rime 引擎在取詞階段依上文整句對候選重排。它直接作用於候選列表，與應用層聯想互補。

**打分機制（給想深挖的讀者）。** 排序由 `Octagram::Query(context, word)` 完成：取上文末尾 ≤3 字 + 候選開頭 ≤3 字拼成 2~6 字鍵，在 Darts 雙陣列 trie 上做最長前綴命中；每個命中得 `ln P(gram|prefix) + 10`，再依搭配長度施加懲罰（`collocation_penalty = -12`、`weak = -24`，未命中恆為 `-12`），取最大值。訓練側的值域與此嚴格對齊（`value = P·e¹⁰`，低強度搭配截斷為 0 分退化為預設）。

**實測判別力。** 在 26 組同音易混候選集上，對比「gram 重排」與「語料字頻序（模擬純詞典預設序）」：

| 排序方式 | 正確候選排第 1 | MRR |
| --- | --- | --- |
| gram 重排 | 24/26（92%） | 0.96 |
| 字頻序（純詞典近似） | 15/26（58%） | 0.77 |
| 隨機序 | ≈19% | — |

（離線抽測，衡量的是排序判別力；端到端上屏還受詞典召回與個人詞頻影響——常打的詞本來就排第 1，gram 的增益集中在低頻詞與首次輸入的搭配上。）

**開箱即用。** 模型隨 APK 內建並預設啟用，且是純本地檔案：可隨時匯出、備份、刪除或停用（停用後排序恢復引擎預設）——資料存放位置始終由使用者掌控。

**自訓模型。** 匯入任意八元語法相容的 `.gram` 即可替換內建模型。聯想模型方面，[`ModelTrainning/`](ModelTrainning/) 提供完整流水線（語料 → 訓練 → int8 量化 → 可匯入模型包），逐步指南見 [`ModelTrainning/modelGuide.md`](ModelTrainning/modelGuide.md)。

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
3. 在系統設定中啟用 XimeGboard 輸入法
4. 將 XimeGboard 設為目前輸入法

### 國內下載

本分支在 Gitee 同步分發安裝包，國內存取更快、更穩定：

- 儲存庫：[https://gitee.com/Murchey/Xime-Gboardlike](https://gitee.com/Murchey/Xime-Gboardlike)
- 安裝包：到該儲存庫的 **發行版（Releases）** 頁面下載與裝置架構相符的最新 APK，選擇方式同上（arm64-v8a / armeabi-v7a / x86_64 / universal）。

GitHub 與 Gitee 的 Release 內容一致，任選其一即可；若兩邊下載都不穩定，也可以[從原始碼構建](#手動構建安裝)後自行安裝。

### 手動構建安裝

1. 克隆專案並構建 APK
2. 安裝應用程式
3. 在系統設定中啟用 XimeGboard 輸入法
4. 將 XimeGboard 設為目前輸入法

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

## 內建物件儲存同步與備份

在**設定 → 同步與備份 → 物件儲存**填寫騰訊雲 COS / 阿里雲 OSS / S3 相容儲存桶資訊，即可在
XimeGboard 的 **Android 版本之間**同步剪貼簿歷史（文字與圖片），並備份使用者詞典；進階選項可
備份目前所有方案的完整形態。目前暫無電腦版，因此不能與電腦同步。上游 WebDAV 等外掛式
同步/備份保留在「更多同步方式（WebDAV 等）」二級頁面。

## 授權條款

GPLv3 License

Copyright © 2026 Kingz Cheung

"Xime" 名稱、Logo 及其他品牌資產**不屬於** GPLv3 開源授權範圍，詳見 [TRADEMARKS.md](TRADEMARKS.md)。
