package com.kingzcheung.xime.ui.theme

/** 玻璃效果的作用范围。 */
enum class GlassScope(val id: String, val title: String) {
    /** 整个键盘盘面：键帽、候选栏、工具栏、面板都走玻璃，背板透出。 */
    ENTIRE("entire", "整个键盘盘面"),

    /** 只有候选栏：候选栏透出模糊底，键盘背板与键帽保持不透明（默认更稳、更省电）。 */
    CANDIDATE_BAR("candidate_bar", "只有候选栏");

    companion object {
        fun of(id: String?): GlassScope = entries.firstOrNull { it.id == id } ?: ENTIRE
    }
}
