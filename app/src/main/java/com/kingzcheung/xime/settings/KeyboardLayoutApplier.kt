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
     * 应用一次布局选择，返回最终生效的方案 id；26 键且没有可恢复方案时返回 null。
     */
    suspend fun apply(
        context: Context,
        layout: String,
        order: String,
        /** 生成新方案后是否立刻等部署（测试里关掉，避免为编 prism 卡几分钟）。 */
        deployImmediately: Boolean = true,
    ): String? {
        SettingsPreferences.setKeyboardLayoutPreset(context, layout)
        SettingsPreferences.setKeyboardLetterOrder(context, order)

        val explicit = KeyboardLayoutPreset.schemaIdFor(layout, order)
        val current = SettingsPreferences.getCurrentSchema(context)

        if (explicit == null) {
            val saved = KeyboardLayoutPreset.restoreTarget(SettingsPreferences.getSchemaBeforeGrid(context))
            if (saved == null) return null
            if (RimeEngine.isInitialized()) RimeEngine.getInstance().switchSchema(saved)
            SettingsPreferences.setCurrentSchema(context, saved)
            FileLogger.i(TAG, "back to 26 keys, restored " + saved)
            return saved
        }

        val base = GridSchemaGenerator.baseOf(current)
            .takeIf { it in GridSchemaGenerator.BASE_SCHEMA_IDS } ?: "rime_ice"
        if (current.isNotEmpty() && !KeyboardLayoutPreset.isGridSchema(current)) {
            SettingsPreferences.setSchemaBeforeGrid(context, current)
        }
        val generated = GridSchemaInstaller.installAndEnable(context, base, layout, order)
        val target = generated ?: explicit
        if (generated != null && deployImmediately) {
            // 新生成的方案要等部署编出 prism 才能切过去
            RimeConfigHelper.ensureDeployment(context)
        }
        if (RimeEngine.isInitialized()) RimeEngine.getInstance().switchSchema(target)
        SettingsPreferences.setCurrentSchema(context, target)
        FileLogger.i(TAG, "applied " + layout + "/" + order + " -> " + target + " (base=" + base + ")")
        return target
    }
}
