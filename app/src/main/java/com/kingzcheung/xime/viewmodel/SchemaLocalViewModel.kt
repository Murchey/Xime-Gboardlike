package com.kingzcheung.xime.viewmodel

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.settings.BuiltinPackages
import com.kingzcheung.xime.settings.BuiltinSeedState
import com.kingzcheung.xime.settings.MarketUpdateChecker
import com.kingzcheung.xime.settings.FileConflictInfo
import com.kingzcheung.xime.settings.SchemaManifestManager
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SchemaManager.InstallFromDirResult
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.UpgradeTransaction
import com.kingzcheung.xime.rime.RimeConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalPackageItem(
    val packageId: String,
    val displayName: String,
    val version: String,
    val downloaded: Boolean,
    val installed: Boolean,
    val schemaCount: Int = 0,
    val fileCount: Int = 0,
) {
    val isImport: Boolean get() = packageId.startsWith("import_")
    val statusLabel: String
        get() = when {
            installed && downloaded -> tr(R.string.s_yianzhuang)
            installed -> tr(R.string.s_yianzhuang)
            downloaded -> tr(R.string.s_yixiazai)
            else -> ""
        }
}

/** 本地包操作阶段（安装与部署分开显示）。 */
enum class LocalPackagePhase { INSTALLING, DEPLOYING }

data class SchemaLocalUiState(
    val packages: List<LocalPackageItem> = emptyList(),
    val isLoading: Boolean = false,
    val installingId: String? = null,
    val phase: LocalPackagePhase? = null,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val conflictPackageId: String? = null,
    val conflictingSchemeIds: List<String> = emptyList(),
    val builtinSeedDisabled: Boolean = false,
)

class SchemaLocalViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(SchemaLocalUiState())
    val uiState: StateFlow<SchemaLocalUiState> = _uiState.asStateFlow()

    init {
        loadLocalPackages()
    }

    fun loadLocalPackages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val installedPkgs = withContext(Dispatchers.IO) {
                SchemaManifestManager.migrateLegacySchemas(context)
                SchemaManifestManager.getInstalledPackages(context)
                    .filter { it.packageId != BuiltinPackages.SHARED }
            }
            val downloadedIds = withContext(Dispatchers.IO) {
                val marketDir = SchemaManager.getMarketDir(context)
                if (!marketDir.exists()) emptySet()
                else marketDir.listFiles()?.mapNotNull { sub ->
                    if (sub.isDirectory && sub.listFiles()?.any { it.isFile } == true) sub.name
                    else null
                }?.toSet() ?: emptySet()
            }
            val installedMap = installedPkgs.associateBy { it.packageId }
            val allIds = (downloadedIds + installedMap.keys)
                .filter { it != "builtin" && it != BuiltinPackages.SHARED }
                .sorted()
            val packages = allIds.map { id ->
                val info = installedMap[id]
                LocalPackageItem(
                    packageId = id,
                    displayName = info?.displayName ?: id,
                    version = info?.version ?: "",
                    downloaded = id in downloadedIds,
                    installed = info != null,
                    schemaCount = info?.schemaCount ?: 0,
                    fileCount = info?.fileCount ?: 0,
                )
            }
            val seedDisabled = withContext(Dispatchers.IO) {
                BuiltinSeedState.isDisabled(context)
            }
            _uiState.update { it.copy(packages = packages, isLoading = false, builtinSeedDisabled = seedDisabled) }
        }
    }

    fun installPackage(item: LocalPackageItem) {
        if (_uiState.value.installingId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(installingId = item.packageId, phase = LocalPackagePhase.INSTALLING) }

            // 检查 rime/ 下是否已有其他方案（无论是否被清单追踪）
            val hasSchemas = withContext(Dispatchers.IO) {
                SchemaManager.discoverSchemas(context).isNotEmpty()
            }
            // 同 ID 已安装视为升级：直接覆盖，保留当前启用列表；不走冲突卸载。
            val isUpgrade = item.installed
            if (!isUpgrade && hasSchemas) {
                val otherInstalled = withContext(Dispatchers.IO) {
                    SchemaManifestManager.getInstalledPackages(context)
                        .map { it.packageId }
                        .filter { it != item.packageId && it != BuiltinPackages.SHARED }
                }
                // 内置包已卸载且没有其他包：没有可冲突的旧包，直接安装。
                if (otherInstalled.isNotEmpty() || !BuiltinSeedState.isDisabled(context)) {
                    _uiState.update { it.copy(installingId = null, phase = null) }
                    val targetIds = if (otherInstalled.isNotEmpty()) otherInstalled else BuiltinPackages.USER_VISIBLE
                    _uiState.update {
                        it.copy(
                            conflictPackageId = item.packageId,
                            conflictingSchemeIds = targetIds,
                        )
                    }
                    return@launch
                }
            }

            // 直接安装，成功后自动部署
            val result = try {
                withContext(Dispatchers.IO) {
                    SchemaManager.installPackageFromMarketDir(
                        context = context,
                        packageId = item.packageId,
                        displayName = item.displayName,
                        version = item.version,
                        fromMarket = !item.isImport,
                        switchEnabled = !isUpgrade,
                        withRollback = isUpgrade,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SchemaLocalViewModel", "installPackage exception", e)
                InstallFromDirResult(success = false, failureReason = tr(R.string.s_anzhuangyichang, e.message))
            }
            if (result.success) {
                // 装完即部署，用户常忘记点「部署」
                _uiState.update { st -> st.copy(phase = LocalPackagePhase.DEPLOYING) }
                val deployed = deployAfterInstall()
                val transaction = result.transaction
                var rolledBack = false
                if (deployed) {
                    transaction?.commit()
                } else if (transaction != null) {
                    rolledBack = withContext(Dispatchers.IO) {
                        val ok = transaction.rollback()
                        if (ok) RimeConfigHelper.deployInstalledSchema(context)
                        ok
                    }
                }
                _uiState.update { st -> st.copy(installingId = null, phase = null) }
                val msg = buildString {
                    append(if (deployed) tr(R.string.s_yianzhuang_bingwanchengbushu, item.displayName)
                           else tr(R.string.s_yianzhuang_qingdian_bushu_shen_cf341, item.displayName))
                    if (rolledBack) append("\n" + tr(R.string.s_yihuigun))
                    if (result.parseFailures.isNotEmpty()) {
                        append(tr(R.string.s_nyixiafanganjiexishibai))
                        result.parseFailures.forEach { append("\n  · $it") }
                    }
                }
                showToast(msg)
            } else if (result.conflicts.isNotEmpty()) {
                _uiState.update { st -> st.copy(installingId = null, phase = null) }
                // 注册表冲突 → 进入冲突解决流程
                val otherInstalled = withContext(Dispatchers.IO) {
                    SchemaManifestManager.getInstalledPackages(context)
                        .map { it.packageId }
                        .filter { it != item.packageId && it != BuiltinPackages.SHARED }
                }
                val targetIds = result.conflicts
                    .flatMap { it.claimedBy }
                    .distinct()
                    .filter { it != item.packageId }
                    .ifEmpty { otherInstalled }
                    .ifEmpty { BuiltinPackages.USER_VISIBLE }
                _uiState.update {
                    it.copy(
                        conflictPackageId = item.packageId,
                        conflictingSchemeIds = targetIds,
                    )
                }
            } else {
                _uiState.update { st -> st.copy(installingId = null, phase = null) }
                showToast(result.failureReason ?: tr(R.string.s_anzhuangshibai))
            }
            loadLocalPackages()
        }
    }

    fun confirmInstallWithUninstall() {
        if (_uiState.value.installingId != null) return
        val pkgId = _uiState.value.conflictPackageId ?: return
        val item = _uiState.value.packages.firstOrNull { it.packageId == pkgId } ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(conflictPackageId = null, installingId = pkgId, phase = LocalPackagePhase.INSTALLING)
            }
            // 卸载前备份旧包；安装/部署失败时整体回滚
            val backups = mutableListOf<UpgradeTransaction>()
            val backupIds = mutableListOf<String>()
            for (sid in _uiState.value.conflictingSchemeIds) {
                val backup = withContext(Dispatchers.IO) {
                    try {
                        // 先刷新 builtin 清单：APP 更新后可能新增了未被清单追踪的文件
                        SchemaManifestManager.refreshBuiltinManifest(context)
                        SchemaManifestManager.prepareUpgradeTransaction(context, sid)
                    } catch (e: Exception) {
                        android.util.Log.e("SchemaLocalViewModel", "backup $sid failed", e)
                        null
                    }
                }
                if (backup != null) {
                    backups += backup
                    backupIds += sid
                }
                if (BuiltinPackages.isBuiltin(sid)) {
                    BuiltinSeedState.setDisabled(context, sid, true)
                }
                val uninstallOk = withContext(Dispatchers.IO) {
                    try {
                        SchemaManifestManager.uninstallWithManifest(context, sid).success
                    } catch (e: Exception) {
                        android.util.Log.e("SchemaLocalViewModel", "uninstall $sid failed", e)
                        false
                    }
                }
                if (!uninstallOk) {
                    withContext(Dispatchers.IO) {
                        backups.asReversed().forEach { it.rollback() }
                        backupIds.forEach { BuiltinSeedState.setDisabled(context, it, false) }
                        RimeConfigHelper.deployInstalledSchema(context)
                    }
                    _uiState.update {
                        it.copy(installingId = null, phase = null, conflictPackageId = null, conflictingSchemeIds = emptyList())
                    }
                    showToast(tr(R.string.s_xiezaichongtufangan_shibai_anz_ab29f, sid))
                    loadLocalPackages()
                    return@launch
                }
            }
            val result = try {
                withContext(Dispatchers.IO) {
                    SchemaManager.installPackageFromMarketDir(
                        context = context,
                        packageId = pkgId,
                        displayName = item.displayName,
                        version = item.version,
                        fromMarket = !item.isImport,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SchemaLocalViewModel", "confirmInstallWithUninstall exception", e)
                InstallFromDirResult(success = false, failureReason = tr(R.string.s_anzhuangyichang, e.message))
            }
            if (result.success) {
                _uiState.update { st -> st.copy(phase = LocalPackagePhase.DEPLOYING) }
                val deployed = deployAfterInstall()
                var restored = false
                if (deployed) {
                    withContext(Dispatchers.IO) { backups.forEach { it.commit() } }
                } else if (backups.isNotEmpty()) {
                    restored = withContext(Dispatchers.IO) {
                        runCatching { SchemaManifestManager.uninstallWithManifest(context, pkgId) }
                        backups.asReversed().forEach { it.rollback() }
                        backupIds.forEach { BuiltinSeedState.setDisabled(context, it, false) }
                        RimeConfigHelper.deployInstalledSchema(context)
                        true
                    }
                }
                _uiState.update { st ->
                    st.copy(installingId = null, phase = null, conflictingSchemeIds = emptyList())
                }
                val msg = buildString {
                    append(if (deployed) tr(R.string.s_yianzhuang_bingwanchengbushu, item.displayName)
                           else tr(R.string.s_yianzhuang_qingdian_bushu_shen_cf341, item.displayName))
                    if (restored) append("\n" + tr(R.string.s_yihuigun))
                    if (result.parseFailures.isNotEmpty()) {
                        append(tr(R.string.s_nyixiafanganjiexishibai))
                        result.parseFailures.forEach { append("\n  · $it") }
                    }
                }
                showToast(msg)
            } else {
                val restored = withContext(Dispatchers.IO) {
                    if (backups.isEmpty()) false else {
                        runCatching { SchemaManifestManager.uninstallWithManifest(context, pkgId) }
                        backups.asReversed().forEach { it.rollback() }
                        backupIds.forEach { BuiltinSeedState.setDisabled(context, it, false) }
                        RimeConfigHelper.deployInstalledSchema(context)
                        true
                    }
                }
                _uiState.update { st -> st.copy(installingId = null, phase = null, conflictingSchemeIds = emptyList()) }
                val reason = if (result.failureReason != null) {
                    result.failureReason
                } else if (result.conflicts.isNotEmpty()) {
                    result.conflicts.joinToString("、") { conflict ->
                        tr(R.string.s_wenjian_chongtu_shiyong, conflict.fileName, conflict.claimedBy.joinToString("、"))
                    }
                } else {
                    tr(R.string.s_anzhuangshibai)
                }
                showToast(if (restored) reason + "\n" + tr(R.string.s_yihuigun) else reason)
            }
            loadLocalPackages()
        }
    }

    /** 安装后自动部署；失败不抛异常，调用方回退为提示手动部署。 */
    private suspend fun deployAfterInstall(): Boolean = withContext(Dispatchers.IO) {
        runCatching { RimeConfigHelper.deployInstalledSchema(context) }.getOrDefault(false)
    }

    fun cancelConflictInstall() {
        _uiState.update { it.copy(conflictPackageId = null, conflictingSchemeIds = emptyList()) }
    }

    fun deleteDownloaded(item: LocalPackageItem) {
        if (BuiltinPackages.isBuiltin(item.packageId)) { showToast(tr(R.string.s_neizhifanganbukeshanchu)); return }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val dir = SchemaManager.getMarketDir(context, item.packageId)
                if (dir.exists()) { dir.deleteRecursively(); true } else false
            }
            showToast(if (ok) tr(R.string.s_yishanchu_2) else tr(R.string.s_shanchushibai))
            if (ok) MarketUpdateChecker.refreshNow(context)
            loadLocalPackages()
        }
    }

    fun uninstall(item: LocalPackageItem) {
        viewModelScope.launch {
            val isBuiltin = BuiltinPackages.isBuiltin(item.packageId)
            if (isBuiltin && item.packageId != BuiltinPackages.SHARED) {
                BuiltinSeedState.setDisabled(context, item.packageId, true)
            }
            withContext(Dispatchers.IO) {
                SchemaManager.deleteSchemaFiles(context, item.packageId)
                SchemaManager.deleteSchemeArchive(context, item.packageId)
                // 当前方案随包被删时，切到仍启用的第一个方案，避免引擎停在已删除方案。
                val enabled = SchemaManager.getEnabledSchemas(context)
                val current = SettingsPreferences.getCurrentSchema(context)
                if (current !in enabled && enabled.isNotEmpty()) {
                    SettingsPreferences.setCurrentSchema(context, enabled.first())
                    if (RimeEngine.isInitialized()) {
                        runCatching { RimeEngine.getInstance().switchSchema(enabled.first()) }
                    }
                }
            }
            // 若卸载后没有已安装的方案了，全量清理 rime/ 残留。
            // 卸载内置包时跳过：保留用户词库/自定义短语，且避免下次启动被 assets 灌回。
            val remaining = withContext(Dispatchers.IO) {
                SchemaManifestManager.getInstalledPackages(context)
                    .filter { it.packageId != BuiltinPackages.SHARED }
            }
            if (remaining.isEmpty() && !BuiltinSeedState.isDisabled(context)) {
                withContext(Dispatchers.IO) {
                    SchemaManager.cleanRimeDir(context)
                }
            }
            showToast(tr(R.string.s_yixiezai))
            MarketUpdateChecker.refreshNow(context)
            loadLocalPackages()
        }
    }

    /** 从 assets 恢复内置方案包（用户卸载后可手动恢复）。 */
    fun restoreBuiltinSeed() {
        if (_uiState.value.installingId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    installingId = BuiltinPackages.RIME_ICE,
                    phase = LocalPackagePhase.INSTALLING,
                )
            }
            val ok = withContext(Dispatchers.IO) {
                RimeConfigHelper.restoreBuiltinSeed(context)
            }
            _uiState.update { it.copy(installingId = null, phase = null, builtinSeedDisabled = !ok) }
            showToast(
                if (ok) tr(R.string.s_yianzhuang_bingwanchengbushu, tr(R.string.s_huifuneizhifangan))
                else tr(R.string.s_anzhuangshibai)
            )
            MarketUpdateChecker.refreshNow(context)
            loadLocalPackages()
        }
    }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }

    private fun showToast(message: String) = _uiState.update { it.copy(toastMessage = message) }
}
