package com.kingzcheung.xime.objectstorage

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ObjectStorageConnectionTest {
    private class Fixture(
        val writeCode: Int = 200, val readCode: Int = 200,
        val deleteCode: Int = 204, val mismatch: Boolean = false,
    ) {
        val requests = mutableListOf<Pair<String, String>>()
        private var uploaded = ByteArray(0)
        val client = ObjectStorageClient(ObjectStorageConfig(endpoint = "https://bucket.example.com", privateAuth = false),
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests += request.method to request.url.encodedPath
                val code = when (request.method) {
                    "PUT" -> { val buffer = Buffer(); request.body!!.writeTo(buffer); uploaded = buffer.readByteArray(); writeCode }
                    "GET" -> readCode
                    else -> deleteCode
                }
                val bytes = if (request.method == "GET" && code == 200)
                    (if (mismatch) "wrong".toByteArray() else uploaded) else "service detail".toByteArray()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                    .message("fixture").body(bytes.toResponseBody()).build()
            }.build())
    }

    @Test fun successfulTestUsesOneRandomIndependentObjectAndDeletesIt() = runBlocking {
        val fixture = Fixture()
        repeat(2) { assertEquals(ObjectStorageClient.ConnectionTestResult.Success, fixture.client.testConnection()) }
        assertEquals(listOf("PUT", "GET", "DELETE", "PUT", "GET", "DELETE"), fixture.requests.map { it.first })
        val first = fixture.requests[0].second
        val second = fixture.requests[3].second
        assertTrue(first.startsWith("/XimeGboard/connection-tests/"))
        assertFalse(first.contains("index"))
        assertNotEquals(first, second)
        assertEquals(listOf(first, first, first, second, second, second), fixture.requests.map { it.second })
    }

    @Test fun writeFailureStillCleansUpAndKeepsServiceDetail() = runBlocking {
        val fixture = Fixture(writeCode = 403)
        val result = fixture.client.testConnection() as ObjectStorageClient.ConnectionTestResult.Failure
        assertEquals(ObjectStorageClient.TestStage.WRITE, result.stage)
        assertTrue(result.detail.contains("403"))
        assertTrue(result.detail.contains("service detail"))
        assertFalse(result.cleanupFailed)
        assertEquals(listOf("PUT", "DELETE"), fixture.requests.map { it.first })
    }

    @Test fun missingReadIsFailureRatherThanSuccessfulEmptyBucket() = runBlocking {
        val fixture = Fixture(readCode = 404)
        val result = fixture.client.testConnection() as ObjectStorageClient.ConnectionTestResult.Failure
        assertEquals(ObjectStorageClient.TestStage.READ, result.stage)
        assertTrue(result.detail.contains("404"))
        assertEquals("DELETE", fixture.requests.last().first)
    }

    @Test fun contentMismatchStillCleansUp() = runBlocking {
        val fixture = Fixture(mismatch = true)
        val result = fixture.client.testConnection() as ObjectStorageClient.ConnectionTestResult.Failure
        assertEquals(ObjectStorageClient.TestStage.VERIFY, result.stage)
        assertEquals("DELETE", fixture.requests.last().first)
    }

    @Test fun cleanupFailureDoesNotClaimFullSuccess() = runBlocking {
        val fixture = Fixture(deleteCode = 403)
        val result = fixture.client.testConnection() as ObjectStorageClient.ConnectionTestResult.Failure
        assertEquals(ObjectStorageClient.TestStage.CLEANUP, result.stage)
        assertTrue(result.cleanupFailed)
        assertEquals(fixture.requests.first().second.removePrefix("/"), result.testKey)
    }

    @Test fun writeAndCleanupFailurePreservesOriginalStage() = runBlocking {
        val fixture = Fixture(writeCode = 403, deleteCode = 403)
        val result = fixture.client.testConnection() as ObjectStorageClient.ConnectionTestResult.Failure
        assertEquals(ObjectStorageClient.TestStage.WRITE, result.stage)
        assertTrue(result.cleanupFailed)
        assertNotNull(result.testKey)
    }

    @Test fun getObjectPreservesHttpStatusAndServiceDetail() = runBlocking {
        val fixture = Fixture(readCode = 403)
        val result = fixture.client.getObject("XimeGboard/index.json") as ObjectStorageClient.Result.Failure
        assertEquals(403, result.httpCode)
        assertTrue(result.message.contains("service detail"))
    }
}
