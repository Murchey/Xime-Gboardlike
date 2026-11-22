# 发布前自检清单

发布前按这份清单跑一遍，确认「下一个 RELEASE 没有使用问题」。每条都写清楚怎么验、验到什么算过。

## 1. 构建产物

- [ ] `build_apk.bat` 一次出 4 个 ABI 的 release APK：`dist/XimeGboard-<ver>-<abi>.apk`
      （arm64-v8a / armeabi-v7a / x86 / x86_64；universal 已取消）
- [ ] 检查 APK 内容：
  - `assets/gram/XimeGboard.gram` 在（语法模型随包）；
  - `assets/models/ochwpro/`（ochwpro.onnx + char_index.json）在——**手写模型随包**，
    首次安装即落盘到 `files/models/ochwpro/`，用户不需要再去下载；
  - **不应该有** `assets/models/predictive-text-small/`（联想 ONNX 模型不随包）；
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

- [ ] 「智能联想 → 选择模型」里只列上游 `predictive-text-small` / `predictive-text-base`
      （以及自己导入/商店装过的模型）；**没有**预留的自训模型槽位。
- [ ] 全新安装（没有任何模型文件）不崩：智能联想自动关闭并提示去下载/导入；导入后能正常联想。
- [ ] 语法模型（.gram）在设置里可见并处于启用状态。

## 2026-11-16 本次自检记录

### 构建与产物

- §build_apk.bat§ 跑通（当时的 full + lite 双包流程，现已改为单包）。
- APK 内容核对：模型只在 full；§assets/gram/XimeGboard.gram§ 两种包都有；§assets/grid_schemas/§ 已无（合并键改成运行时派生）。
- debug 单测 1913 个用例、5 个既有失败（与本次改动无关）。

### 真机（Redmi 21091116AC，Android 13）

- 全新安装会被 MIUI 拦一次：§INSTALL_FAILED_USER_RESTRICTED§，需要在「USB安装提示」点「继续安装」；
  覆盖安装（同签名）不受影响。
- release（用 debug 签名 + §-PreleaseDebuggable=true§ 出包，只为验证 release 代码本身）：
  - 冷启动无崩溃，设置首页 / 输入方案页渲染正常，分类 chips 可见，方案显示「已编译」；
  - 向导三步能走完（第 2 步要等 §isDeploymentComplete§，首次部署以分钟计）；
  - 26 键中文输入正常：打 §wo§ 出「我|握|窝|卧|喔|沃」，上屏后联想栏出「们|的|是|不|也|就」——
    说明 R8 压缩后 JNI、推理子进程、内置联想模型链路都还活着。

### 仍建议人工过一遍的

- 合并键布局在 release 上的切换（debug 上已充分验证：派生、键面、组码解码、切回 26 键恢复方案）；
- 「无模型」路径（联想 ONNX 已不随包，所有包都是这条路径：导入入口、不崩、提示下载）。
