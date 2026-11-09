package com.kingzcheung.xime.settings

/**
 * 部署阶段：给用户看的"现在在做什么"，不追求精确进度。
 *
 * librime 的部署是一个整体调用（nativeDeploy），内部按 schema/dict 逐个编译，
 * 但不会回调进度。因此这里的做法是：把部署前后的准备工作拆成明确的阶段，
 * 部署本身按已用时间在一个区间内推进（渐近但不到 100%），
 * 真正完成时才跳到终点——宁可慢一点，也不要在结束时突然从 30% 跳到 100%。
 */
enum class DeployStage {
    IDLE,

    /** 补齐内置基础文件（symbols/default 等）。 */
    PREPARE_BASE,

    /** 写入/更新方案补丁（packs、T9 等）。 */
    PREPARE_PATCHES,

    /** 加载键位与主题配置。 */
    PREPARE_CONFIG,

    /** librime 编译方案与词库（耗时最长，无法细分）。 */
    COMPILING,

    /** 清理未启用方案的编译产物。 */
    PRUNE,

    /** 完成。 */
    DONE,
}
