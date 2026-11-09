package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.objectstorage.ObjectStorageBackupManager
import com.kingzcheung.xime.objectstorage.ObjectStorageBackupManager.BackupEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObjectStorageBackupRecordsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val entry = BackupEntry("XimeGboard/backups/userdict/test.zip", 12, "", false)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class FakeRepository : ObjectStorageBackupRecordsRepository {
        var listResult: Result<List<BackupEntry>> = Result.success(emptyList())
        var restoreResult: ObjectStorageBackupManager.Outcome = ObjectStorageBackupManager.Outcome.Success("restored", "key")
        var deleteResult = Result.success(Unit)
        var thrown: Exception? = null
        var listCalls = 0
        var restoreCalls = 0
        var pending: CompletableDeferred<Unit>? = null
        override suspend fun list(): Result<List<BackupEntry>> { listCalls++; return listResult }
        override suspend fun restore(entry: BackupEntry): ObjectStorageBackupManager.Outcome {
            restoreCalls++
            pending?.await()
            thrown?.let { throw it }
            return restoreResult
        }
        override suspend fun delete(entry: BackupEntry): Result<Unit> = deleteResult
    }

    @Test fun loadErrorAndEmptySuccessAreDistinct() = runTest(dispatcher) {
        val repository = FakeRepository().apply { listResult = Result.failure(IllegalStateException("HTTP 403")) }
        val model = ObjectStorageBackupRecordsViewModel(repository)
        model.refresh()
        assertTrue(model.state.value.loading)
        advanceUntilIdle()
        assertFalse(model.state.value.loaded)
        assertEquals("HTTP 403", model.state.value.loadError)
        assertFalse(model.state.value.loading)
        repository.listResult = Result.success(emptyList())
        model.refresh()
        advanceUntilIdle()
        assertTrue(model.state.value.loaded)
        assertNull(model.state.value.loadError)
        assertTrue(model.state.value.entries.isEmpty())
    }

    @Test fun successfulRestoreAndDeleteRefreshRecords() = runTest(dispatcher) {
        val repository = FakeRepository().apply { listResult = Result.success(listOf(entry)) }
        val model = ObjectStorageBackupRecordsViewModel(repository)
        model.restore(entry)
        advanceUntilIdle()
        assertEquals(1, repository.listCalls)
        assertEquals("restored", model.state.value.message)
        assertEquals(listOf(entry), model.state.value.entries)
        repository.listResult = Result.success(emptyList())
        model.delete(entry)
        advanceUntilIdle()
        assertEquals(2, repository.listCalls)
        assertTrue(model.state.value.entries.isEmpty())
        assertNull(model.state.value.busyKey)
    }

    @Test fun thrownRestoreClearsBusyAndPreservesError() = runTest(dispatcher) {
        val repository = FakeRepository().apply { thrown = IllegalStateException("disk failure") }
        val model = ObjectStorageBackupRecordsViewModel(repository)
        model.restore(entry)
        assertEquals(entry.key, model.state.value.busyKey)
        advanceUntilIdle()
        assertEquals("disk failure", model.state.value.operationError)
        assertNull(model.state.value.busyKey)
        assertFalse(model.state.value.loading)
        assertEquals(0, repository.listCalls)
    }

    @Test fun failedDeleteDoesNotRefreshOrReportSuccess() = runTest(dispatcher) {
        val repository = FakeRepository().apply { deleteResult = Result.failure(IllegalStateException("HTTP 403")) }
        val model = ObjectStorageBackupRecordsViewModel(repository)
        model.delete(entry)
        advanceUntilIdle()
        assertEquals("HTTP 403", model.state.value.operationError)
        assertNull(model.state.value.message)
        assertNull(model.state.value.busyKey)
        assertEquals(0, repository.listCalls)
    }

    @Test fun duplicateOperationIsIgnoredWhileBusy() = runTest(dispatcher) {
        val repository = FakeRepository().apply { pending = CompletableDeferred() }
        val model = ObjectStorageBackupRecordsViewModel(repository)
        model.restore(entry)
        model.restore(entry)
        model.refresh()
        repository.pending!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.restoreCalls)
        assertEquals(1, repository.listCalls)
    }

    @Test fun refreshFailureAfterSuccessKeepsOperationSuccessAndLoadErrorSeparate() = runTest(dispatcher) {
        val repository = FakeRepository().apply { listResult = Result.failure(IllegalStateException("offline")) }
        val model = ObjectStorageBackupRecordsViewModel(repository)
        model.restore(entry)
        advanceUntilIdle()
        assertEquals("restored", model.state.value.message)
        assertEquals("offline", model.state.value.loadError)
        assertNull(model.state.value.operationError)
        assertNull(model.state.value.busyKey)
    }
}
