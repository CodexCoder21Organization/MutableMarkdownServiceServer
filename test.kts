@KotlinBuildScript("https://tools.kotlin.build/")
@file:WithArtifact("kompile:build-kotlin-jvm:0.0.1")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.9.22")
@file:WithArtifact("org.json:json:20250517")
package mutablemarkdownserver

import build.kotlin.withartifact.WithArtifact
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for MutableMarkdownServiceServer.
 *
 * These tests start an actual HTTP server and verify the CLI can
 * communicate with it properly.
 */

// Helper to make HTTP requests
fun httpGet(url: String): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    val response = conn.inputStream.bufferedReader().readText()
    return JSONObject(response)
}

fun httpPost(url: String, body: String): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    conn.outputStream.use { it.write(body.toByteArray()) }
    val response = conn.inputStream.bufferedReader().readText()
    return JSONObject(response)
}

fun httpPut(url: String, body: String): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "PUT"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    conn.outputStream.use { it.write(body.toByteArray()) }
    val response = conn.inputStream.bufferedReader().readText()
    return JSONObject(response)
}

fun httpDelete(url: String): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "DELETE"
    val response = conn.inputStream.bufferedReader().readText()
    return JSONObject(response)
}

// Test with a temporary data directory
fun withTempMarkdownService(block: (port: Int, dataDir: File) -> Unit) {
    val tempDir = File.createTempFile("markdown-test-", "").apply {
        delete()
        mkdirs()
    }

    try {
        // Find a free port
        val socket = java.net.ServerSocket(0)
        val port = socket.localPort
        socket.close()

        // Start server in background
        val serverReady = CountDownLatch(1)
        val serverThread = Thread {
            // Set environment for server
            System.setProperty("MARKDOWN_SERVICE_DATA_DIR", tempDir.absolutePath)

            val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)
            val rpcHandler = mutablemarkdownserver.MarkdownRpcHandler(service)
            val httpServer = mutablemarkdownserver.MarkdownHttpServer(rpcHandler, port)
            httpServer.start()
            serverReady.countDown()

            // Keep running until interrupted
            try {
                Thread.sleep(Long.MAX_VALUE)
            } catch (e: InterruptedException) {
                httpServer.stop()
            }
        }
        serverThread.start()

        // Wait for server to start
        assertTrue(serverReady.await(10, TimeUnit.SECONDS), "Server should start within 10 seconds")

        // Run tests
        block(port, tempDir)

        // Stop server
        serverThread.interrupt()
        serverThread.join(5000)
    } finally {
        tempDir.deleteRecursively()
    }
}

// Test: Health check
fun testHealthCheck() {
    withTempMarkdownService { port, _ ->
        val response = httpGet("http://localhost:$port/health")
        assertTrue(response.toString().contains("OK") || response.has("result"))
    }
}

// Test: Create and retrieve file
fun testCreateAndRetrieveFile() {
    withTempMarkdownService { port, _ ->
        // Create file
        val createResponse = httpPost(
            "http://localhost:$port/file",
            """{"name": "test.md", "content": "# Hello World"}"""
        )
        assertTrue(createResponse.has("id"))
        val id = createResponse.getString("id")
        assertNotNull(id)
        assertEquals("test.md", createResponse.getString("name"))

        // Get file by ID
        val getResponse = httpGet("http://localhost:$port/file?id=$id")
        assertEquals(id, getResponse.getString("id"))
        assertEquals("test.md", getResponse.getString("name"))
        assertEquals("# Hello World", getResponse.getString("content"))
    }
}

// Test: Get file by name
fun testGetFileByName() {
    withTempMarkdownService { port, _ ->
        // Create file
        httpPost("http://localhost:$port/file", """{"name": "readme.md", "content": "# Readme"}""")

        // Get by name
        val response = httpGet("http://localhost:$port/file?name=readme.md")
        assertEquals("readme.md", response.getString("name"))
        assertEquals("# Readme", response.getString("content"))
    }
}

// Test: Update file content
fun testUpdateFileContent() {
    withTempMarkdownService { port, _ ->
        // Create file
        val createResponse = httpPost(
            "http://localhost:$port/file",
            """{"name": "update.md", "content": "Original content"}"""
        )
        val id = createResponse.getString("id")

        // Update content
        val updateResponse = httpPut(
            "http://localhost:$port/file?id=$id",
            """{"content": "Updated content"}"""
        )
        assertTrue(updateResponse.optBoolean("ok", false))

        // Verify update
        val getResponse = httpGet("http://localhost:$port/file?id=$id")
        assertEquals("Updated content", getResponse.getString("content"))
    }
}

// Test: List files
fun testListFiles() {
    withTempMarkdownService { port, _ ->
        // Create multiple files
        httpPost("http://localhost:$port/file", """{"name": "file1.md", "content": "Content 1"}""")
        httpPost("http://localhost:$port/file", """{"name": "file2.md", "content": "Content 2"}""")
        httpPost("http://localhost:$port/file", """{"name": "file3.md", "content": "Content 3"}""")

        // List files
        val response = httpGet("http://localhost:$port/files")
        val files = response.getJSONArray("files")
        assertEquals(3, files.length())
    }
}

// Test: Delete file
fun testDeleteFile() {
    withTempMarkdownService { port, _ ->
        // Create file
        val createResponse = httpPost(
            "http://localhost:$port/file",
            """{"name": "delete-me.md", "content": "To be deleted"}"""
        )
        val id = createResponse.getString("id")

        // Delete file
        val deleteResponse = httpDelete("http://localhost:$port/file?id=$id")
        assertTrue(deleteResponse.optBoolean("deleted", false))

        // Verify deletion
        val listResponse = httpGet("http://localhost:$port/files")
        val files = listResponse.getJSONArray("files")
        assertEquals(0, files.length())
    }
}
