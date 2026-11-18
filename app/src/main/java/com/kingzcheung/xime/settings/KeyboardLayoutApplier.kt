package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.util.FileLogger

/**
 * 键盘布局选择的落地动作（设置页点击后走这里）。
 *
 * 从 Composable 里抽出来，一是能单测/设备测（点一下到底改了什么），二是页面只负责显示。
 * 规则：
 * - 26 键：恢复进入合并键布局前的方案（[KeyboardLayoutPreset.restoreTarget]）；
 * - 14/17/18 键：以**当前基础方案**派生对应方案（全拼 → rime_ice_14jian，小鹤 → double_pinyin_flypy_14jian …），
 *   生成后等部署编出 prism 再切换。
 */
object KeyboardLayoutApplier {
    private const val TAG = "KeyboardLayoutApplier"

    /**
     * 应用一次布局选择并部署，返回最终生效的方案 id。
     *
     * - 26 键：基础方案就是它自己，直接启用 + 编译 + 切换（替换当前方案）；
     * - 14/17/18 键：按 [baseSchemaId] 派生合并键方案，部署后**替换当前方案**，
     *   并把上一套方案的编译产物删掉（同一时间只留一个方案真正在用）。
     */
    suspend fun apply(
        context: Context,
        layout: String,
        order: String,
        baseSchemaId: String,
        /** 测试里关掉，避免为编 prism 卡几分钟。 */
        deployImmediately: Boolean = true,
    ): String? {
        SettingsPreferences.setKeyboardLayoutPreset(context, layout)
        SettingsPreferences.setKeyboardLetterOrder(context, order)
        if (baseSchemaId.isNotEmpty()) {
            SettingsPreferences.setKeyboardLayoutBaseSchema(context, baseSchemaId)
        }

        val isGrid = KeyboardLayoutPreset.normalizeLayout(layout) != KeyboardLayoutPreset.LAYOUT_26
        // 26 键直接用当前/所选方案（可以是导入的）；异体键盘只能用内置基础方案派生，
        // 显式传入导入方案时拒绝，而不是悄悄换成 rime_ice 让用户以为生效了。
        if (isGrid && baseSchemaId.isNotEmpty() && baseSchemaId !in GridSchemaGenerator.BASE_SCHEMA_IDS) {
            FileLogger.w(TAG, "apply(" + layout + "/" + order + "): 基础方案 " + baseSchemaId + " 不是内置方案，拒绝派生异体键盘")
            return null
        }
        val base = baseSchemaId.ifEmpty { GridSchemaGenerator.baseOf(SettingsPreferences.getCurrentSchema(context)) }
            .takeIf { it in GridSchemaGenerator.BASE_SCHEMA_IDS } ?: "rime_ice"

        val current = SettingsPreferences.getCurrentSchema(context)

        val target = if (!isGrid) {
            // 26 键：直接用基础方案本身
            if (base !in SchemaManager.getEnabledSchemas(context)) {
                SchemaManager.setEnabledSchemas(context, SchemaManager.getEnabledSchemas(context) + base)
            }
            base
        } else {
            if (current.isNotEmpty() && !KeyboardLayoutPreset.isGridSchema(current)) {
                SettingsPreferences.setSchemaBeforeGrid(context, current)
            }
            GridSchemaInstaller.installAndEnable(context, base, layout, order)
                ?: KeyboardLayoutPreset.schemaIdFor(layout, order)
                ?: return null
        }

        // 换方案：上一套不是目标就退休（移出启用列表 + 删编译产物）
        if (current.isNotEmpty() && current != target) {
            GridSchemaInstaller.retireScheme(context, current)
        }

        if (deployImmediately) {
            RimeConfigHelper.ensureDeployment(context)
        }
        if (RimeEngine.isInitialized()) RimeEngine.getInstance().switchSchema(target)
        SettingsPreferences.setCurrentSchema(context, target)
        FileLogger.i(TAG, "applied " + layout + "/" + order + " base=" + base + " -> " + target)
        return target
    }
}