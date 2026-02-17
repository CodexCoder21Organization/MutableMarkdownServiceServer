@file:WithArtifact("mutablemarkdownserver.buildMaven()")
@file:WithArtifact("community.kotlin.markdown:api:0.0.1")
@file:WithArtifact("foundation.url:protocol:0.0.234")
@file:WithArtifact("community.kotlin.rpc:protocol-api:0.0.2")
@file:WithArtifact("community.kotlin.rpc:protocol-impl:0.0.11")
@file:WithArtifact("org.json:json:20250517")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.9.22")
@file:WithArtifact("com.squareup.okio:okio-jvm:3.4.0")
@file:WithArtifact("io.libp2p:jvm-libp2p:1.2.2-RELEASE")
@file:WithArtifact("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
package mutablemarkdownserver.tests

import build.kotlin.withartifact.WithArtifact
import kotlin.test.*

fun testPathBasedLookupReturnsFileWhenExists() {
    val tempDir = java.io.File.createTempFile("markdown-test", "").also {
        it.delete()
        it.mkdirs()
    }
    try {
        val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)
        service.createFile("baby-sleep.md", "# Baby Sleep Tips\n\nSome content here.")

        val handler = mutablemarkdownserver.MarkdownRpcHandler(
            service,
            ByteArray(0),
            "dummy/ClassName",
            ByteArray(0)
        )

        val result = handler.handleP2pRequest("baby-sleep.md", emptyMap())

        assertTrue(result is Map<*, *>, "Result should be a Map but was ${result?.javaClass?.name}")
        val resultMap = result as Map<*, *>
        assertEquals(true, resultMap["found"], "File should be found when requesting by name 'baby-sleep.md'")
        assertEquals("baby-sleep.md", resultMap["name"], "Returned file name should be 'baby-sleep.md'")
        assertEquals("# Baby Sleep Tips\n\nSome content here.", resultMap["content"], "Returned content should match the created file's content")
        assertNotNull(resultMap["id"], "Returned file should have an id")
        assertNotNull(resultMap["lastModified"], "Returned file should have a lastModified timestamp")
    } finally {
        tempDir.deleteRecursively()
    }
}

fun testPathBasedLookupReturnsServiceMetadataWhenFileNotFound() {
    val tempDir = java.io.File.createTempFile("markdown-test", "").also {
        it.delete()
        it.mkdirs()
    }
    try {
        val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)

        val handler = mutablemarkdownserver.MarkdownRpcHandler(
            service,
            ByteArray(0),
            "dummy/ClassName",
            ByteArray(0)
        )

        val result = handler.handleP2pRequest("nonexistent-file.md", emptyMap())

        assertTrue(result is Map<*, *>, "Result should be a Map but was ${result?.javaClass?.name}")
        val resultMap = result as Map<*, *>
        assertEquals("url://markdown/", resultMap["service"], "Should return service metadata when file is not found, but got: $resultMap")
        assertEquals("rpc", resultMap["type"], "Service metadata should have type 'rpc'")
        assertNotNull(resultMap["availableMethods"], "Service metadata should list available methods")
    } finally {
        tempDir.deleteRecursively()
    }
}

fun testPathBasedLookupDoesNotInterfereWithRpcMethods() {
    val tempDir = java.io.File.createTempFile("markdown-test", "").also {
        it.delete()
        it.mkdirs()
    }
    try {
        val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)
        // Create a file named "health" to verify RPC methods take precedence
        service.createFile("health", "This should not be returned for the 'health' RPC method")

        val handler = mutablemarkdownserver.MarkdownRpcHandler(
            service,
            ByteArray(0),
            "dummy/ClassName",
            ByteArray(0)
        )

        val result = handler.handleP2pRequest("health", emptyMap())

        assertEquals("OK", result, "The 'health' RPC method should return 'OK' even when a file named 'health' exists, but got: $result")
    } finally {
        tempDir.deleteRecursively()
    }
}

fun testPathBasedLookupViaGetAllFilesAndThenByName() {
    val tempDir = java.io.File.createTempFile("markdown-test", "").also {
        it.delete()
        it.mkdirs()
    }
    try {
        val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)
        service.createFile("notes.md", "# Notes")
        service.createFile("todo.md", "# Todo List")

        val handler = mutablemarkdownserver.MarkdownRpcHandler(
            service,
            ByteArray(0),
            "dummy/ClassName",
            ByteArray(0)
        )

        // Verify getAllFiles still works
        val allResult = handler.handleP2pRequest("getAllFiles", emptyMap())
        assertTrue(allResult is Map<*, *>, "getAllFiles result should be a Map")
        val files = (allResult as Map<*, *>)["files"] as List<*>
        assertEquals(2, files.size, "Should have 2 files, but found ${files.size}")

        // Verify path-based lookup works for both files
        val notesResult = handler.handleP2pRequest("notes.md", emptyMap()) as Map<*, *>
        assertEquals("notes.md", notesResult["name"], "Path lookup for 'notes.md' should return the correct file")
        assertEquals("# Notes", notesResult["content"], "Path lookup for 'notes.md' should return the correct content")

        val todoResult = handler.handleP2pRequest("todo.md", emptyMap()) as Map<*, *>
        assertEquals("todo.md", todoResult["name"], "Path lookup for 'todo.md' should return the correct file")
        assertEquals("# Todo List", todoResult["content"], "Path lookup for 'todo.md' should return the correct content")
    } finally {
        tempDir.deleteRecursively()
    }
}
