package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.objectstorage.ObjectStorageClient
import com.kingzcheung.xime.objectstorage.ObjectStorageConfig
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import com.kingzcheung.xime.objectstorage.StorageProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

class ObjectStorageSettingsViewModel(
    initial: ObjectStorageConfig,
    private val saveConfig: suspend (ObjectStorageConfig) -> ObjectStorageConfigStore.SaveResult,
    private val testConfig: suspend (ObjectStorageConfig) -> ObjectStorageClient.ConnectionTestResult,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private var saved = initial
    var draft by mutableStateOf(initial)
        private set
    var saving by mutableStateOf(false)
        private set
    var testing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var messageIsError by mutableStateOf(false)
        private set
    val dirty: Boolean get() = draft != saved
    val busy: Boolean get() = saving || testing

    fun update(config: ObjectStorageConfig) {
        if (busy) return
        draft = config
        message = null
    }

    fun discard() {
        if (saving) return
        draft = saved
        message = null
    }

    fun save(onSaved: () -> Unit = {}) {
        if (busy) return
        val config = normalized(draft)
        validationError(config)?.let { showError(it); return }
        saving = true
        message = null
        viewModelScope.launch {
            try {
                when (withContext(ioDispatcher) { saveConfig(config) }) {
                    ObjectStorageConfigStore.SaveResult.Success -> {
                        saved = config
                        draft = config
                        messageIsError = false
                        message = tr(R.string.config_saved)
                        onSaved()
                    }
                    ObjectStorageConfigStore.SaveResult.EncryptionFailure ->
                        showError(tr(R.string.config_encrypt_failed))
                    ObjectStorageConfigStore.SaveResult.PersistenceFailure ->
                        showError(tr(R.string.config_persist_failed))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError(tr(R.string.config_save_failed))
            } finally {
                saving = false
            }
        }
    }

    fun testConnection() {
        if (busy) return
        val config = normalized(draft)
        validationError(config)?.let { showError(it); return }
        testing = true
        message = null
        viewModelScope.launch {
            try {
                when (val result = testConfig(config)) {
                    ObjectStorageClient.ConnectionTestResult.Success -> {
                        messageIsError = false
                        message = tr(R.string.config_test_ok)
                    }
                    is ObjectStorageClient.ConnectionTestResult.Failure -> {
                        val stage = when (result.stage) {
                            ObjectStorageClient.TestStage.WRITE -> tr(R.string.config_write_failed)
                            ObjectStorageClient.TestStage.READ -> tr(R.string.config_read_failed)
                            ObjectStorageClient.TestStage.VERIFY -> tr(R.string.config_verify_failed)
                            ObjectStorageClient.TestStage.CLEANUP -> tr(R.string.config_cleanup_failed)
                        }
                        val cleanup = if (result.cleanupFailed)
                            tr(R.string.config_cleanup_remaining, result.testKey) else ""
                        showError(tr(R.string.config_stage_detail, stage, result.detail, cleanup))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError(tr(R.string.config_test_failed))
            } finally {
                testing = false
            }
        }
    }

    private fun showError(text: String) {
        messageIsError = true
        message = text
    }

    companion object {
        internal fun normalized(config: ObjectStorageConfig) = config.copy(
            endpoint = config.endpoint.trim().trimEnd('/'),
            bucket = if (config.protocol == StorageProtocol.S3) config.bucket.trim() else "",
            accessKeyId = config.accessKeyId.trim(),
            secretKey = config.secretKey.trim(),
        )

        internal fun validationError(config: ObjectStorageConfig): String? {
            if (config.endpoint.isBlank()) return tr(R.string.config_endpoint_required)
            val endpoint = if ("://" in config.endpoint) config.endpoint else "https://${config.endpoint}"
            val uri = runCatching { URI(endpoint) }.getOrNull()
            if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() ||
                uri.userInfo != null || !uri.rawPath.isNullOrEmpty() || uri.rawQuery != null || uri.rawFragment != null) {
                return tr(R.string.config_endpoint_invalid)
            }
            if (config.protocol == StorageProtocol.S3 && config.bucket.isBlank()) return tr(R.string.config_s3_bucket_required)
            if (config.protocol == StorageProtocol.S3 && config.bucket.any { it.isWhitespace() || it == '/' })
                return tr(R.string.config_bucket_invalid)
            if (config.privateAuth && (config.accessKeyId.isBlank() || config.secretKey.isBlank()))
                return tr(R.string.config_credentials_required)
            return null
        }
    }
}
