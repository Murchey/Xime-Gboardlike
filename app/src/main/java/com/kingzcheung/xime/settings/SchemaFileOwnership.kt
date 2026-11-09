package com.kingzcheung.xime.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * 方案包「共享文件」冲突的判定与处置。
 *
 * 起因：一批方案包习惯把公共文件（词典、algebra、符号表）随包重复携带。
 * 同一文件被多个包声明、内容又不同时，历史实现会把旧属主的认领整体移交给新包，
 * 卸载新包时那些仍被旧包需要的文件被一并删掉，旧方案随即损坏——
 * 用户侧看到的就是「装了 A 再装 B，A 就坏了 / B 顶替了 A」。
 *
 * 本对象集中处理这类文件的语义：
 * - 共享文件：内容相同 → 允许任意多个包同时声明，卸载时只解除自己的声明；
 * - 覆盖文件：内容不同 → 允许安装，但当前生效内容归属最后安装者，
 *   其余包的版本留档在 .package-presets/<包>/，由 SharedPresetOverrides 负责回滚。
 *
 * 判定与合并都是纯函数（入参出参都是 JSON 结构），便于单测覆盖真实包的文件表。
 */
object SchemaFileOwnership {

    /** 冲突分类结果。 */
    data class Analysis(
        /** 内容与现有完全相同：可共享，不阻断安装。 */
        val identical: List<String> = emptyList(),
        /** 内容不同但属于可覆盖类别：安装后当前生效内容改用新包版本。 */
        val overridable: List<String> = emptyList(),
        /** 内容不同且不属于可覆盖类别：需要用户明确确认「覆盖安装」。 */
        val blocking: List<String> = emptyList(),
    ) {
        val hasBlocking: Boolean get() = blocking.isNotEmpty()
        val canInstallDirectly: Boolean get() = !hasBlocking
        val isEmpty: Boolean get() = identical.isEmpty() && overridable.isEmpty() && blocking.isEmpty()
    }

    /**
     * 判断某个文件是否属于「允许跨包覆盖」的类别。
     *
     * 真正需要用户确认的只有两类：**方案定义**（*.schema.yaml）与**词库**（*.dict.yaml）
     * ——它们直接决定编码与候选，覆盖后旧方案可能打不出字。
     * 其余随包重复携带的文件（default.yaml、algebra、拆文表、符号表、Lua 脚本…）
     * 是各方案族共享的基础设施，覆盖属于正常升级行为，不该拦住安装。
     */
    internal fun isOverridableType(fileName: String): Boolean {
        val base = fileName.substringAfterLast('/')
        return !base.endsWith(".schema.yaml") && !base.endsWith(".dict.yaml")
    }

    /**
     * 分析一批待安装文件与现有归属的关系。
     *
     * @param targetFiles 本包将写入的文件名
     * @param newHashes 本包各文件内容 sha256
     * @param registryFiles 注册表 files 节点
     * @param packageId 本包 id（同包重装不算冲突）
     */
    fun analyze(
        targetFiles: Collection<String>,
        newHashes: Map<String, String>,
        registryFiles: JSONObject,
        packageId: String,
    ): Analysis {
        val identical = mutableListOf<String>()
        val overridable = mutableListOf<String>()
        val blocking = mutableListOf<String>()

        for (fileName in targetFiles) {
            val entry = registryFiles.optJSONObject(fileName) ?: continue
            val claimants = entry.optJSONArray("claimedBy")?.let(::jsonToList).orEmpty()
            if (claimants.isEmpty()) continue
            // 同包重装/升级：直接覆盖自己的文件
            if (claimants.contains(packageId)) continue
            // 共享预设由 SHARED 认领：本就允许各包覆盖
            if (claimants.all { it == BuiltinPackages.SHARED }) continue

            val existingSha = entry.optString("sha256", "")
            val newSha = newHashes[fileName] ?: continue
            when {
                existingSha == newSha -> identical.add(fileName)
                isOverridableType(fileName) -> overridable.add(fileName)
                else -> blocking.add(fileName)
            }
        }
        return Analysis(identical, overridable, blocking)
    }

    /**
     * 计算安装后的新归属表。
     *
     * 关键规则：共享文件保留原有属主、只追加本包（不再整体转交），
     * 这样卸载本包时旧包仍持有该文件；只有真正覆盖的文件才把归属
     * 收敛到本包（旧属主的版本由 SharedPresetOverrides 留档）。
     */
    fun mergeClaims(
        registryFiles: JSONObject,
        packageId: String,
        fileEntries: JSONObject,
        isShared: (String) -> Boolean,
    ): JSONObject {
        val result = JSONObject(registryFiles.toString())
        val keys = fileEntries.keys()
        while (keys.hasNext()) {
            val fileName = keys.next() as String
            val fe = fileEntries.optJSONObject(fileName) ?: continue
            val existing = result.optJSONObject(fileName)
            if (existing == null) {
                result.put(fileName, JSONObject().apply {
                    put("sha256", fe.optString("sha256", ""))
                    put("size", fe.optLong("size"))
                    put("claimedBy", JSONArray(listOf(packageId)))
                })
                continue
            }
            val claimants = existing.optJSONArray("claimedBy")?.let(::jsonToList)
                ?.toMutableList() ?: mutableListOf()
            // 任何情况下都保留旧属主的声明：卸载本包时其它包仍持有该文件，
            // 不会出现"装了 B、A 的文件被删掉"的情况。
            if (!claimants.contains(packageId)) claimants.add(packageId)
            existing.put("claimedBy", JSONArray(claimants))
            // 非共享（本包自有）文件的内容随本次安装更新；共享文件的内容由
            // SharedPresetOverrides 单独留档，注册表里保留基线 sha 便于回滚判定。
            if (!isShared(fileName)) {
                existing.put("sha256", fe.optString("sha256", existing.optString("sha256", "")))
                existing.put("size", fe.optLong("size", existing.optLong("size", 0L)))
            }
            result.put(fileName, existing)
        }
        return result
    }

    private fun jsonToList(arr: JSONArray): List<String> =
        (0 until arr.length()).mapNotNull { runCatching { arr.getString(it) }.getOrNull() }
}
