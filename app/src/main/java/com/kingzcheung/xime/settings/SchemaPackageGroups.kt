package com.kingzcheung.xime.settings

/**
 * 方案列表按「来源包」分组。
 *
 * 背景：装上雾凇、万象等方案包后，同一个 id 或同一个显示名会出现在多个包里
 * （内置的 微软双拼 vs 雾凇的 微软双拼 vs 万象的 微软双拼），平铺成一张长列表
 * 时用户只看到一堆同名项，分不清哪个来自哪个包，也判断不出「谁顶替了谁」。
 *
 * 这里把「schemaId → 所属包」的映射 + 分组做成纯函数，便于单测。
 */
object SchemaPackageGroups {

    /** 未归类（文件存在但没有任何清单认领，如手动拷进 rime/ 的方案）。 */
    const val UNGROUPED = "ungrouped"

    /** 一个来源包及其包含的方案。 */
    data class Group(
        val packageId: String,
        val displayName: String,
        val schemaIds: List<String>,
        /** 该包当前有多少个方案处于启用状态。 */
        val enabledCount: Int,
    ) {
        val isEmpty: Boolean get() = schemaIds.isEmpty()
    }

    /**
     * 按包分组。
     *
     * @param allSchemaIds 当前扫描到的全部方案 id（按名称排序）
     * @param schemaToPackage schemaId → 包 id
     * @param packageNames 包 id → 显示名
     * @param enabledSchemaIds 已启用的方案 id
     */
    fun group(
        allSchemaIds: List<String>,
        schemaToPackage: Map<String, String>,
        packageNames: Map<String, String>,
        enabledSchemaIds: Collection<String>,
        ungroupedName: String,
    ): List<Group> {
        val enabled = enabledSchemaIds.toSet()
        val byPackage = allSchemaIds.groupBy { schemaToPackage[it] ?: UNGROUPED }

        val groups = byPackage.map { (packageId, ids) ->
            Group(
                packageId = packageId,
                displayName = if (packageId == UNGROUPED) {
                    ungroupedName
                } else {
                    packageNames[packageId] ?: packageId
                },
                schemaIds = ids,
                enabledCount = ids.count { it in enabled },
            )
        }
        // 有内容的包按名称排；未归类固定放最后，避免它挤在字母序中间
        return groups.sortedWith(
            compareBy<Group> { it.packageId == UNGROUPED }
                .thenBy { it.displayName.lowercase() }
        )
    }

    /**
     * 过滤出某个包下的方案；[packageId] 为空表示「全部」。
     *
     * 顺序保持入参顺序（调用方已按启用状态 + 名称排好），不在这里重排，
     * 避免出现「切换分组后同一个方案跳位置」。
     */
    fun filter(
        schemaIds: List<String>,
        schemaToPackage: Map<String, String>,
        packageId: String?,
    ): List<String> {
        if (packageId.isNullOrBlank()) return schemaIds
        return schemaIds.filter { (schemaToPackage[it] ?: UNGROUPED) == packageId }
    }

    /**
     * 同名方案检测：返回显示名重复的 id 列表。
     *
     * 这些项在界面上必须带来源包标签，否则用户无法区分——这正是
     * 「有些同名方案会被顶替」的观感来源。
     */
    fun duplicateNames(nameById: Map<String, String>): Set<String> {
        val byName = nameById.entries.groupBy { it.value.trim() }
        return byName.values
            .filter { it.size > 1 }
            .flatMap { it.map { e -> e.key } }
            .toSet()
    }
}
