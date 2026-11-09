package com.kingzcheung.xime.clipboard.sync.objstore

import android.content.Context
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.clipboard.db.ClipboardDao
import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import com.kingzcheung.xime.objectstorage.ObjectStorageClient
import com.kingzcheung.xime.objectstorage.ObjectStorageConfig
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ClipboardObjectStoreSyncTest {
    private val prefs = SyncTestPreferences()
    private val context: Context = mock()
    private val manager: ClipboardManager = mock()
    private val dao: ClipboardDao = mock()
    private val store: ObjectStorageConfigStore = mock()
    private val client: ObjectStorageClient = mock()
    private lateinit var engine: ClipboardObjectStoreSync
    private val indexKey = "XimeGboard/index.json"

    @Before fun prepare() = runBlocking {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(store.load()).thenReturn(ObjectStorageConfig(endpoint = "example.com", accessKeyId = "id", secretKey = "key"))
        whenever(dao.allClipboardOnce()).thenReturn(emptyList())
        whenever(client.getObject(any())).thenReturn(ObjectStorageClient.Result.Failure("HTTP 404 <Code>NoSuchKey</Code>", 404))
        whenever(client.putObject(any(), any(), any())).thenReturn(ObjectStorageClient.Result.Success(Unit))
        whenever(client.deleteObject(any())).thenReturn(ObjectStorageClient.Result.Success(Unit))
        engine = ClipboardObjectStoreSync(context, manager, dao, store) { client }
    }

    @Test fun readFailureDoesNotOverwriteRemoteOrSuccess() = runBlocking {
        whenever(client.getObject(indexKey)).thenReturn(ObjectStorageClient.Result.Failure("HTTP 403", 403))
        assertTrue(engine.performSync() is ClipboardObjectStoreSync.SyncOutcome.Failure)
        verify(client, never()).putObject(any(), any(), any())
        assertFalse(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED))
    }

    @Test fun uploadFailureDoesNotPublishIndexOrSuccess() = runBlocking {
        whenever(dao.allClipboardOnce()).thenReturn(listOf(ClipboardEntry(text = "local")))
        whenever(client.putObject(any(), any(), any())).thenReturn(ObjectStorageClient.Result.Failure("HTTP 500", 500))
        assertTrue(engine.performSync() is ClipboardObjectStoreSync.SyncOutcome.Failure)
        verify(client, never()).putObject(eq(indexKey), any(), any())
        assertFalse(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED))
    }

    @Test fun indexWriteFailureDoesNotRecordSuccess() = runBlocking {
        whenever(client.putObject(eq(indexKey), any(), any())).thenReturn(ObjectStorageClient.Result.Failure("HTTP 500", 500))
        assertTrue(engine.performSync() is ClipboardObjectStoreSync.SyncOutcome.Failure)
        assertFalse(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED))
    }

    @Test fun cancellationPropagates() = runBlocking {
        whenever(client.getObject(indexKey)).thenThrow(CancellationException("cancelled"))
        try {
            engine.performSync()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED))
    }

    @Test fun remoteTombstoneDeletesLocalEvenWithoutRemoteObject() = runBlocking {
        val entry = ClipboardEntry(id = 42, text = "removed", timestamp = 1)
        whenever(dao.allClipboardOnce()).thenReturn(listOf(entry))
        val uuid = engine.uuidOf(entry)!!
        val remote = ClipboardIndexCalculator.build(emptyList(), listOf(ClipboardTombstone(uuid, 2)), 2)
        whenever(client.getObject(indexKey)).thenReturn(ObjectStorageClient.Result.Success(remote.toJson().toString().toByteArray()))
        assertTrue(engine.performSync() is ClipboardObjectStoreSync.SyncOutcome.Success)
        verify(dao).deleteClipboardById(42)
        assertEquals(listOf(uuid), TombstoneStore.decode(prefs.getString(TombstoneStore.PREF_KEY, null)).map { it.uuid })
    }

    @Test fun invalidDownloadedContentDoesNotRecordSuccess() = runBlocking {
        val sha = ObjectStorageHashes.sha256Hex("expected")
        val uuid = "txt_" + sha
        val remote = ClipboardIndexCalculator.build(listOf(ClipboardIndexItem(uuid, sha, 1)), now = 2)
        whenever(client.getObject(indexKey)).thenReturn(ObjectStorageClient.Result.Success(remote.toJson().toString().toByteArray()))
        val body = org.json.JSONObject().put("uuid", uuid).put("type", "text").put("text", "corrupt")
        whenever(client.getObject("XimeGboard/content/" + uuid + ".json"))
            .thenReturn(ObjectStorageClient.Result.Success(body.toString().toByteArray()))
        assertTrue(engine.performSync() is ClipboardObjectStoreSync.SyncOutcome.Failure)
        verify(dao, never()).insert(any())
        assertFalse(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED))
    }

    @Test fun rejectedImageImportDoesNotRecordSuccess() = runBlocking {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0)
        val sha = ObjectStorageHashes.sha256Hex(bytes)
        val uuid = "img_" + sha
        val remote = ClipboardIndexCalculator.build(listOf(ClipboardIndexItem(uuid, sha, 1, "image")), now = 2)
        whenever(client.getObject(indexKey)).thenReturn(ObjectStorageClient.Result.Success(remote.toJson().toString().toByteArray()))
        val path = "files/img/" + sha + ".png"
        val body = org.json.JSONObject().put("uuid", uuid).put("type", "image").put("imageHash", sha).put("imagePath", path)
        whenever(client.getObject("XimeGboard/content/" + uuid + ".json"))
            .thenReturn(ObjectStorageClient.Result.Success(body.toString().toByteArray()))
        whenever(client.getObject("XimeGboard/content/" + path)).thenReturn(ObjectStorageClient.Result.Success(bytes))
        whenever(manager.importImageFromSync(any(), anyOrNull())).thenReturn(null)
        assertTrue(engine.performSync() is ClipboardObjectStoreSync.SyncOutcome.Failure)
        assertFalse(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED))
    }
    @Test fun missingBucketAndUnknown404DoNotCreateIndex() = runBlocking {
        for (message in listOf("HTTP 404 <Code>NoSuchBucket</Code>", "HTTP 404")) {
            whenever(client.getObject(indexKey)).thenReturn(ObjectStorageClient.Result.Failure(message, 404))
            assertTrue(engine.performSync() is ClipboardObjectStoreSync.SyncOutcome.Failure)
        }
        verify(client, never()).putObject(any(), any(), any())
        assertFalse(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED))
    }

    @Test fun missingObjectCanInitializeIndex() = runBlocking {
        assertTrue(engine.performSync() is ClipboardObjectStoreSync.SyncOutcome.Success)
        verify(client).putObject(eq(indexKey), any(), any())
        assertTrue(prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED))
    }

    @Test fun syncMergeDoesNotOverwriteDeletionRecordedDuringTransfer() = runBlocking {
        TombstoneStore.mergeInto(prefs, listOf(ClipboardTombstone("new", 10)))
        TombstoneStore.mergeInto(prefs, listOf(ClipboardTombstone("old", 1)))
        assertEquals(setOf("new", "old"), TombstoneStore.decode(prefs.getString(TombstoneStore.PREF_KEY, null)).map { it.uuid }.toSet())
    }
}