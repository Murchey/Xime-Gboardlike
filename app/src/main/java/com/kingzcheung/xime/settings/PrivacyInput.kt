package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.rime.RimeEngine

/**
 * 隐私输入：一次开关同时关掉两条"学习"链路。
 *
 * - 用户词库：librime 的 Memory::OnCommit 会跳过提交写库（新加的 JNI 开关）；
 * - 联想模型：输入不再记进本地 n-gram 缓存（PredictionManager / AssociationManager 侧拦截）。
 *
 * 已有词库与模型文件不受影响，读库、删词条、导入导出都照常。
 * librime 的开关是进程内全局量，引擎重建（进程重启/切换方案后重建）后要重新落一次，
 * 见 [apply] 在各初始化点的调用。
 */
object PrivacyInput {

    fun isEnabled(context: Context): Boolean = SettingsPreferences.isPrivacyInputEnabled(context)

    /** 切换开关：先落盘，再同步到 librime。 */
    fun setEnabled(context: Context, enabled: Boolean) {
        SettingsPreferences.setPrivacyInputEnabled(context, enabled)
        apply(context)
    }

    /** 把当前开关同步到 librime（引擎初始化/重建后调用）。 */
    fun apply(context: Context) {
        runCatching {
            RimeEngine.getInstance().setUserDictLearningEnabled(!isEnabled(context))
        }
    }
}
