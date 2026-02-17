@file:WithArtifact("mutablemarkdownserver.buildMaven()")
@file:WithArtifact("community.kotlin.markdown:api:0.0.1")
@file:WithArtifact("foundation.url:resolver:0.0.328")
@file:WithArtifact("foundation.url:protocol:0.0.234")
@file:WithArtifact("community.kotlin.rpc:protocol-api:0.0.2")
@file:WithArtifact("community.kotlin.rpc:protocol-impl:0.0.11")
@file:WithArtifact("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.1")
@file:WithArtifact("org.json:json:20250517")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.9.22")
@file:WithArtifact("com.squareup.okio:okio-jvm:3.4.0")
@file:WithArtifact("io.libp2p:jvm-libp2p:1.2.2-RELEASE")
@file:WithArtifact("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
package mutablemarkdownserver.tests

import build.kotlin.withartifact.WithArtifact
import kotlin.test.*

fun testE2ePathBasedLookupReturnsFileViaUrlResolver() {
    val tempDir = java.io.File.createTempFile("markdown-test", "").also {
        it.delete()
        it.mkdirs()
    }
    try {
        val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)
        service.createFile("baby-sleep.md", "# Baby Sleep Tips\n\nSome content here.")

        val rpcHandler = mutablemarkdownserver.MarkdownRpcHandler(
            service,
            ByteArray(0),
            "dummy/ClassName",
            ByteArray(0)
        )

        val serverHandler = object : foundation.url.protocol.ServiceHandler {
            override suspend fun handleRequest(
                path: String,
                params: Map<String, Any?>,
                metadata: Map<String, String>
            ): Any? {
                return rpcHandler.handleP2pRequest(path, params)
            }

            override fun getImplementationJar(): ByteArray = ByteArray(0)
            override fun getImplementationClassName(): String = "dummy/ClassName"
            override fun getStdlibJar(): ByteArray = ByteArray(0)
            override fun supportsSandboxedExecution(): Boolean = false
            override fun onShutdown() {}
        }

        // Create server UrlResolver and register the markdown service
        val serverResolver = foundation.url.resolver.UrlResolver(foundation.url.resolver.UrlProtocol2())
        val registration = serverResolver.registerGlobalService(
            "url://markdown/",
            serverHandler,
            foundation.url.protocol.ServiceRegistrationConfig()
        )

        // Create client UrlResolver and connect it to the server
        val clientResolver = foundation.url.resolver.UrlResolver(foundation.url.resolver.UrlProtocol2())
        clientResolver.addBootstrapPeer(
            foundation.url.protocol.TestPeer(
                registration.peerId,
                registration.multiaddresses,
                listOf("url://markdown/")
            )
        )

        // Client resolves url://markdown/baby-sleep.md via RPC
        val result = clientResolver.sendServiceRpcRequest(
            "url://markdown/",
            "baby-sleep.md",
            emptyMap()
        )

        assertEquals(true, result["found"], "File should be found when requesting 'baby-sleep.md' via UrlResolver, but got: $result")
        assertEquals("baby-sleep.md", result["name"], "Returned file name should be 'baby-sleep.md'")
        assertEquals("# Baby Sleep Tips\n\nSome content here.", result["content"], "Returned content should match the created file's content")
        assertNotNull(result["id"], "Returned file should have an id")
        assertNotNull(result["lastModified"], "Returned file should have a lastModified timestamp")

        registration.unregister()
        clientResolver.close()
        serverResolver.close()
    } finally {
        tempDir.deleteRecursively()
    }
}

fun testE2eNonExistentFileReturnsServiceMetadataViaUrlResolver() {
    val tempDir = java.io.File.createTempFile("markdown-test", "").also {
        it.delete()
        it.mkdirs()
    }
    try {
        val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)

        val rpcHandler = mutablemarkdownserver.MarkdownRpcHandler(
            service,
            ByteArray(0),
            "dummy/ClassName",
            ByteArray(0)
        )

        val serverHandler = object : foundation.url.protocol.ServiceHandler {
            override suspend fun handleRequest(
                path: String,
                params: Map<String, Any?>,
                metadata: Map<String, String>
            ): Any? {
                return rpcHandler.handleP2pRequest(path, params)
            }

            override fun getImplementationJar(): ByteArray = ByteArray(0)
            override fun getImplementationClassName(): String = "dummy/ClassName"
            override fun getStdlibJar(): ByteArray = ByteArray(0)
            override fun supportsSandboxedExecution(): Boolean = false
            override fun onShutdown() {}
        }

        val serverResolver = foundation.url.resolver.UrlResolver(foundation.url.resolver.UrlProtocol2())
        val registration = serverResolver.registerGlobalService(
            "url://markdown/",
            serverHandler,
            foundation.url.protocol.ServiceRegistrationConfig()
        )

        val clientResolver = foundation.url.resolver.UrlResolver(foundation.url.resolver.UrlProtocol2())
        clientResolver.addBootstrapPeer(
            foundation.url.protocol.TestPeer(
                registration.peerId,
                registration.multiaddresses,
                listOf("url://markdown/")
            )
        )

        val result = clientResolver.sendServiceRpcRequest(
            "url://markdown/",
            "nonexistent-file.md",
            emptyMap()
        )

        assertEquals("url://markdown/", result["service"], "Should return service metadata when file is not found, but got: $result")
        assertEquals("rpc", result["type"], "Service metadata should have type 'rpc'")
        assertNotNull(result["availableMethods"], "Service metadata should list available methods")

        registration.unregister()
        clientResolver.close()
        serverResolver.close()
    } finally {
        tempDir.deleteRecursively()
    }
}

fun testE2eRpcMethodsTakePrecedenceOverFileNames() {
    val tempDir = java.io.File.createTempFile("markdown-test", "").also {
        it.delete()
        it.mkdirs()
    }
    try {
        val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)
        service.createFile("health", "This should not shadow the health RPC method")

        val rpcHandler = mutablemarkdownserver.MarkdownRpcHandler(
            service,
            ByteArray(0),
            "dummy/ClassName",
            ByteArray(0)
        )

        val serverHandler = object : foundation.url.protocol.ServiceHandler {
            override suspend fun handleRequest(
                path: String,
                params: Map<String, Any?>,
                metadata: Map<String, String>
            ): Any? {
                return rpcHandler.handleP2pRequest(path, params)
            }

            override fun getImplementationJar(): ByteArray = ByteArray(0)
            override fun getImplementationClassName(): String = "dummy/ClassName"
            override fun getStdlibJar(): ByteArray = ByteArray(0)
            override fun supportsSandboxedExecution(): Boolean = false
            override fun onShutdown() {}
        }

        val serverResolver = foundation.url.resolver.UrlResolver(foundation.url.resolver.UrlProtocol2())
        val registration = serverResolver.registerGlobalService(
            "url://markdown/",
            serverHandler,
            foundation.url.protocol.ServiceRegistrationConfig()
        )

        val clientResolver = foundation.url.resolver.UrlResolver(foundation.url.resolver.UrlProtocol2())
        clientResolver.addBootstrapPeer(
            foundation.url.protocol.TestPeer(
                registration.peerId,
                registration.multiaddresses,
                listOf("url://markdown/")
            )
        )

        val result = clientResolver.sendServiceRpcRequest(
            "url://markdown/",
            "health",
            emptyMap()
        )

        // The health RPC method returns "OK" as a string, which gets wrapped in the RPC response.
        // sendServiceRpcRequest returns a Map, so "OK" would be the result value.
        assertEquals("OK", result["result"], "The 'health' RPC method should return 'OK' even when a file named 'health' exists, but got: $result")

        registration.unregister()
        clientResolver.close()
        serverResolver.close()
    } finally {
        tempDir.deleteRecursively()
    }
}

fun testE2eCreateAndRetrieveMultipleFilesViaUrlResolver() {
    val tempDir = java.io.File.createTempFile("markdown-test", "").also {
        it.delete()
        it.mkdirs()
    }
    try {
        val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)

        val rpcHandler = mutablemarkdownserver.MarkdownRpcHandler(
            service,
            ByteArray(0),
            "dummy/ClassName",
            ByteArray(0)
        )

        val serverHandler = object : foundation.url.protocol.ServiceHandler {
            override suspend fun handleRequest(
                path: String,
                params: Map<String, Any?>,
                metadata: Map<String, String>
            ): Any? {
                return rpcHandler.handleP2pRequest(path, params)
            }

            override fun getImplementationJar(): ByteArray = ByteArray(0)
            override fun getImplementationClassName(): String = "dummy/ClassName"
            override fun getStdlibJar(): ByteArray = ByteArray(0)
            override fun supportsSandboxedExecution(): Boolean = false
            override fun onShutdown() {}
        }

        val serverResolver = foundation.url.resolver.UrlResolver(foundation.url.resolver.UrlProtocol2())
        val registration = serverResolver.registerGlobalService(
            "url://markdown/",
            serverHandler,
            foundation.url.protocol.ServiceRegistrationConfig()
        )

        val clientResolver = foundation.url.resolver.UrlResolver(foundation.url.resolver.UrlProtocol2())
        clientResolver.addBootstrapPeer(
            foundation.url.protocol.TestPeer(
                registration.peerId,
                registration.multiaddresses,
                listOf("url://markdown/")
            )
        )

        // Create files via RPC
        clientResolver.sendServiceRpcRequest(
            "url://markdown/",
            "createFile",
            mapOf("name" to "notes.md", "content" to "# Notes")
        )
        clientResolver.sendServiceRpcRequest(
            "url://markdown/",
            "createFile",
            mapOf("name" to "todo.md", "content" to "# Todo List")
        )

        // Retrieve files by path
        val notesResult = clientResolver.sendServiceRpcRequest(
            "url://markdown/",
            "notes.md",
            emptyMap()
        )
        assertEquals("notes.md", notesResult["name"], "Path lookup for 'notes.md' should return the correct file, but got: $notesResult")
        assertEquals("# Notes", notesResult["content"], "Path lookup for 'notes.md' should return the correct content")
        assertEquals(true, notesResult["found"], "notes.md should be found")

        val todoResult = clientResolver.sendServiceRpcRequest(
            "url://markdown/",
            "todo.md",
            emptyMap()
        )
        assertEquals("todo.md", todoResult["name"], "Path lookup for 'todo.md' should return the correct file, but got: $todoResult")
        assertEquals("# Todo List", todoResult["content"], "Path lookup for 'todo.md' should return the correct content")
        assertEquals(true, todoResult["found"], "todo.md should be found")

        // Verify getAllFiles returns both
        val allResult = clientResolver.sendServiceRpcRequest(
            "url://markdown/",
            "getAllFiles",
            emptyMap()
        )
        val files = allResult["files"]
        assertTrue(files is List<*>, "getAllFiles should return a list of files, but got: ${files?.javaClass?.name}")
        assertEquals(2, (files as List<*>).size, "Should have 2 files, but found ${files.size}")

        registration.unregister()
        clientResolver.close()
        serverResolver.close()
    } finally {
        tempDir.deleteRecursively()
    }
}
