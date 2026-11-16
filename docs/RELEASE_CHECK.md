# 发布前自检清单

发布前按这份清单跑一遍，确认「下一个 RELEASE 没有使用问题」。每条都写清楚怎么验、验到什么算过。

## 1. 构建产物

- [ ] `build_apk.bat` 两个包都出：`dist/full/XimeGboard-<ver>-<abi>-full.apk`、`dist/lite/...-lite.apk`
- [ ] 两种包各自 5 个 ABI（arm64-v8a / armeabi-v7a / universal / x86 / x86_64）
- [ ] 检查 APK 内容：
  - `assets/models/predictive-text-small/` 只在 **full** 里出现；
  - `assets/gram/XimeGboard.gram` 两种包都有；
  - `assets/xime.yaml` 在；
  - 不应该再有 `assets/grid_schemas/`（合并键方案改为运行时派生）。

## 2. 单元测试

- [ ] `./gradlew :app:testDebugUnitTest`，失败数不高于基线
  - 当前基线：1913 个用例、5 个既有失败（`BackupManagerTest`×2、`KeyboardPanelConfigTest`、
    `KeyboardRowsLayoutConfigTest`、`KeysConfigLayoutReloadTest`）——这三条布局用例是历史遗留，
    与本轮改动无关，修它们要单独排期。

## 3. 装机（真机）

- [ ] 覆盖安装（同名同签名）直接成功；全新安装需要确认 MIUI 的「USB安装提示」弹窗
      （命令行 `adb install` 会返回 `INSTALL_FAILED_USER_RESTRICTED`，需要点「继续安装」）。
- [ ] 冷启动进设置首页，无崩溃。
- [ ] 设置向导三步能走完：启用 → 选方案（要求「已编译」且部署完成）→ 切换。

## 4. 输入链路

- [ ] 26 键：中文能出候选、能上屏；英文直出正常。
- [ ] 数字结尾不触发联想（候选栏清空）。
- [ ] 联想候选成组（词跟随首字），标点不上候选。
- [ ] 合并键布局：设置 → 布局与显示 → 键盘布局设置 → 选「14 键」→ 键盘变 14 键、按组码出词；
      再选「26 键」能恢复原来的方案。
- [ ] 方案列表的分类 chips（全部/全拼/双拼/合并键/其他）能筛。
- [ ] 空格键上显示方案名（不是空白，也不是裸 id）。

## 5. 模型与语法

- [ ] 「智能联想 → 选择模型」里能看到 **XimeGboard 内置联想模型**（独立条目），
      上游 `predictive-text-small` / `predictive-text-base` 仍作为可导入项存在。
- [ ] full 包首次启动即有联想；lite 包不崩、提示去导入模型。
- [ ] 语法模型（.gram）在设置里可见并处于启用状态。

## 2026-11-16 本次自检记录

- 构建：full + lite 各 5 个 ABI 全部产出；APK 内容核对通过（模型只在 full、无 grid_schemas）。
- 单测：debug 1913 / 5（基线内）。
- 装机：release（debug 签名版）在 Redmi 21091116AC 上安装并冷启动成功，设置页、输入方案页渲染正常，
  分类 chips 可见，方案显示「已编译」。
- 未完成：**键盘输入链路没在 release 上走完**——全新安装后卡在向导第 2 步：
  「下一步」要求 `RimeConfigHelper.isDeploymentComplete` 为真，而该机首次部署耗时很长，
  按钮点了没有反应（也没看到部署提示文案）。release 包不可 debug，`run-as` 读不到 rime 目录，
  无法从命令行确认部署进度。这条建议发布前在真机上人工过一遍。
