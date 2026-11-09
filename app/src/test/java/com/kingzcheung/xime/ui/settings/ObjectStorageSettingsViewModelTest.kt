package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.objectstorage.ObjectStorageClient
import com.kingzcheung.xime.objectstorage.ObjectStorageConfig
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import com.kingzcheung.xime.objectstorage.StorageProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObjectStorageSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val initial = ObjectStorageConfig(endpoint = "bucket.cos.example.com", accessKeyId = "id", secretKey = "secret")
    private var saved: ObjectStorageConfig? = null
    private var tested: ObjectStorageConfig? = null
    private var saveResult: ObjectStorageConfigStore.SaveResult = ObjectStorageConfigStore.SaveResult.Success
    private fun model() = ObjectStorageSettingsViewModel(initial,
        { saved = it; saveResult },
        { tested = it; ObjectStorageClient.ConnectionTestResult.Success }, dispatcher)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun editsAndDiscardNeverSave() {
        val vm = model()
        vm.update(initial.copy(protocol = StorageProtocol.OSS, privateAuth = false, endpoint = "other.example.com"))
        assertTrue(vm.dirty)
        assertNull(saved)
        vm.discard()
        assertEquals(initial, vm.draft)
        assertFalse(vm.dirty)
        assertNull(saved)
    }

    @Test fun connectionUsesDraftWithoutSaving() = runTest(dispatcher) {
        val vm = model()
        val draft = initial.copy(endpoint = "draft.example.com", secretKey = "draft-secret")
        vm.update(draft)
        vm.testConnection()
        advanceUntilIdle()
        assertEquals(draft, tested)
        assertNull(saved)
        assertTrue(vm.dirty)
        assertFalse(vm.testing)
        assertFalse(vm.messageIsError)
    }

    @Test fun explicitSaveNormalizesAndClearsDirtyOnlyAfterSuccess() = runTest(dispatcher) {
        val vm = model()
        vm.update(initial.copy(bucket = " bucket "))
        var returned = false
        vm.save { returned = true }
        assertTrue(vm.saving)
        assertTrue(vm.dirty)
        assertNull(saved)
        advanceUntilIdle()
        assertEquals("bucket", saved?.bucket)
        assertFalse(vm.dirty)
        assertTrue(returned)
    }

    @Test fun encryptionFailureRetainsDraftAndDoesNotNavigate() = runTest(dispatcher) {
        saveResult = ObjectStorageConfigStore.SaveResult.EncryptionFailure
        val vm = model()
        val draft = initial.copy(secretKey = "new-secret")
        vm.update(draft)
        var returned = false
        vm.save { returned = true }
        advanceUntilIdle()
        assertEquals(draft, vm.draft)
        assertTrue(vm.dirty)
        assertTrue(vm.messageIsError)
        assertFalse(returned)
        assertFalse(vm.saving)
    }

    @Test fun invalidPrivateDraftCannotSaveOrTest() = runTest(dispatcher) {
        val vm = model()
        vm.update(initial.copy(secretKey = ""))
        vm.save()
        vm.testConnection()
        advanceUntilIdle()
        assertNull(saved)
        assertNull(tested)
        assertTrue(vm.messageIsError)
    }

    @Test fun publicDraftAllowsAnonymousTestAndS3RequiresBucket() {
        assertNull(ObjectStorageSettingsViewModel.validationError(initial.copy(privateAuth = false, accessKeyId = "", secretKey = "")))
        assertNotNull(ObjectStorageSettingsViewModel.validationError(initial.copy(protocol = StorageProtocol.S3)))
        assertNull(ObjectStorageSettingsViewModel.validationError(initial.copy(protocol = StorageProtocol.S3, bucket = "bucket")))
    }

    @Test fun rejectsIgnoredEndpointPathsAndUnsupportedSchemes() {
        listOf("https://example.com/path", "https://example.com?query", "ftp://example.com", "https://id:secret@example.com").forEach {
            assertNotNull(ObjectStorageSettingsViewModel.validationError(initial.copy(endpoint = it)))
        }
    }

    @Test fun cleanupFailureIsDistinctAndIncludesOnlyTestObject() = runTest(dispatcher) {
        val vm = ObjectStorageSettingsViewModel(initial, { ObjectStorageConfigStore.SaveResult.Success }, {
            ObjectStorageClient.ConnectionTestResult.Failure(ObjectStorageClient.TestStage.CLEANUP,
                "HTTP 403", true, "XimeGboard/connection-tests/random.bin")
        }, dispatcher)
        vm.testConnection()
        advanceUntilIdle()
        assertTrue(vm.messageIsError)
        assertTrue(vm.message!!.contains("清理失败"))
        assertTrue(vm.message!!.contains("connection-tests/random.bin"))
        assertFalse(vm.testing)
    }
}
