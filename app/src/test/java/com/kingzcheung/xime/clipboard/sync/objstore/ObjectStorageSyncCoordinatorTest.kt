package com.kingzcheung.xime.clipboard.sync.objstore

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObjectStorageSyncCoordinatorTest {
    private val prefs = SyncTestPreferences()
    private val context: Context = mock {
        on { getSharedPreferences(any(), any()) } doReturn prefs
    }

    @Test fun manualSyncSerializesAndPublishesStateWithoutStartingSubscriptions() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        var running = 0
        var maxRunning = 0
        val coordinator = ObjectStorageSyncCoordinator(context) {
            calls++
            running++
            maxRunning = maxOf(maxRunning, running)
            entered.complete(Unit)
            release.await()
            running--
            ClipboardObjectStoreSync.SyncOutcome.Success(1, 0, 0)
        }
        val first = async { coordinator.syncNow() }
        entered.await()
        assertEquals(SyncPhase.Running, coordinator.status.value.phase)
        val second = async { coordinator.syncNow() }
        runCurrent()
        assertEquals(1, calls)
        release.complete(Unit)
        first.await()
        second.await()
        assertEquals(2, calls)
        assertEquals(1, maxRunning)
        assertEquals(SyncPhase.Success, coordinator.status.value.phase)
        assertNotNull(coordinator.status.value.lastSuccessAt)
        verify(context, never()).getSystemService(any<String>())
    }

    @Test fun cancelledSyncReturnsIdleWithoutSuccessTimestamp() = runTest {
        val entered = CompletableDeferred<Unit>()
        val coordinator = ObjectStorageSyncCoordinator(context) {
            entered.complete(Unit)
            CompletableDeferred<Unit>().await()
            ClipboardObjectStoreSync.SyncOutcome.UpToDate
        }
        val job = launch { coordinator.syncNow() }
        entered.await()
        job.cancelAndJoin()
        assertEquals(SyncPhase.Idle, coordinator.status.value.phase)
        assertNull(coordinator.status.value.lastSuccessAt)
    }

    @Test fun failurePreservesLastSuccessAndExposesMessage() = runTest {
        var fail = false
        val coordinator = ObjectStorageSyncCoordinator(context) {
            if (fail) ClipboardObjectStoreSync.SyncOutcome.Failure("HTTP 403")
            else ClipboardObjectStoreSync.SyncOutcome.UpToDate
        }
        coordinator.syncNow()
        val successAt = coordinator.status.value.lastSuccessAt
        fail = true
        coordinator.syncNow()
        assertEquals(SyncPhase.Error, coordinator.status.value.phase)
        assertEquals("HTTP 403", coordinator.status.value.message)
        assertEquals(successAt, coordinator.status.value.lastSuccessAt)
    }

    @Test fun disabledAutomaticRequestDoesNotRunOrStartManager() = runTest {
        var calls = 0
        val coordinator = ObjectStorageSyncCoordinator(context) {
            calls++
            ClipboardObjectStoreSync.SyncOutcome.UpToDate
        }
        coordinator.startAutomatic()
        coordinator.requestAutomatic()
        runCurrent()
        assertEquals(0, calls)
        assertEquals(SyncPhase.Idle, coordinator.status.value.phase)
        verify(context, never()).getSystemService(any<String>())
    }
    @Test fun savedBucketChangeClearsStatusAndPersistedSuccess() = runTest {
        val coordinator = ObjectStorageSyncCoordinator(context) { ClipboardObjectStoreSync.SyncOutcome.UpToDate }
        coordinator.syncNow()
        prefs.edit().putLong(ClipboardObjectStoreSync.PREF_LAST_UPDATED, 123)
            .putString(ClipboardObjectStoreSync.PREF_LAST_SHA, "old-sha").apply()
        prefs.edit().putString("os_bucket", "another-bucket").apply()
        assertEquals(SyncStatus(), coordinator.status.value)
        assertFalse(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED))
        assertFalse(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_SHA))
    }

    @Test fun modeChangeKeepsSuccessHistory() = runTest {
        val coordinator = ObjectStorageSyncCoordinator(context) { ClipboardObjectStoreSync.SyncOutcome.UpToDate }
        coordinator.syncNow()
        val previous = coordinator.status.value
        prefs.edit().putString(ClipboardObjectStoreSync.PREF_MODE, ClipboardObjectStoreSync.MODE_PUSH).apply()
        assertEquals(previous, coordinator.status.value)
    }

    @Test fun previousBucketRunCannotRestoreSuccessAfterConfigurationReset() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = ObjectStorageSyncCoordinator(context) {
            entered.complete(Unit)
            release.await()
            ClipboardObjectStoreSync.SyncOutcome.UpToDate
        }
        val run = async { coordinator.syncNow() }
        entered.await()
        prefs.edit().putString("os_bucket", "another-bucket").apply()
        assertEquals(SyncStatus(), coordinator.status.value)
        release.complete(Unit)
        assertTrue(run.await() is ClipboardObjectStoreSync.SyncOutcome.Failure)
        assertEquals(SyncStatus(), coordinator.status.value)
    }

    @Test fun restoredSuccessRequiresMatchingSavedTarget() = runTest {
        prefs.edit().putLong(ClipboardObjectStoreSync.PREF_LAST_UPDATED, 123)
            .putString(SavedSyncTarget.PREF_KEY, SavedSyncTarget.identity(prefs)).apply()
        assertEquals(123L, ObjectStorageSyncCoordinator(context).status.value.lastSuccessAt)
        prefs.edit().putString("os_endpoint", "new.example.com").apply()
        assertNull(ObjectStorageSyncCoordinator(context).status.value.lastSuccessAt)
    }
}