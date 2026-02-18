@file:WithArtifact("mutablemarkdownserver.buildFatJar()")
@file:WithArtifact("community.kotlin.markdown:api:0.0.1")
@file:WithArtifact("foundation.url:resolver:0.0.349")
@file:WithArtifact("foundation.url:protocol:0.0.251")
@file:WithArtifact("community.kotlin.rpc:protocol-api:0.0.2")
@file:WithArtifact("community.kotlin.rpc:protocol-impl:0.0.11")
@file:WithArtifact("net.javadeploy.sjvm:avianStdlibHelper-jvm:0.0.24")
@file:WithArtifact("org.json:json:20250517")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.9.22")
@file:WithArtifact("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
package mutablemarkdownserver.tests

import build.kotlin.withartifact.WithArtifact
import kotlin.test.*
import foundation.url.protocol.Libp2pPeer
import foundation.url.resolver.UrlResolver
import foundation.url.resolver.UrlProtocol2
import foundation.url.protocol.ServiceHandler
import foundation.url.protocol.ServiceRegistrationConfig

fun withSjvmClient(block: (community.kotlin.markdown.api.MarkdownService) -> Unit) {
    val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "markdown-e2e-${java.util.UUID.randomUUID()}")
    tempDir.mkdirs()
    var serverResolver: UrlResolver? = null
    var clientResolver: UrlResolver? = null
    try {
        serverResolver = UrlResolver(UrlProtocol2(bootstrapPeers = emptyList()))
        val service = mutablemarkdownserver.MarkdownServiceImpl(tempDir)

        val clientJarBytes = object {}.javaClass.getResourceAsStream("/client-impl.jar")?.readBytes() ?: ByteArray(0)
        val stdlibJarBytes = object {}.javaClass.getResourceAsStream("/stdlib.jar")?.readBytes()

        val implClassName = mutablemarkdownserver.BytecodeGenerator.getImplementationClassName()
        val rpcHandler = mutablemarkdownserver.MarkdownRpcHandler(
            service, clientJarBytes, implClassName, stdlibJarBytes ?: ByteArray(0)
        )

        val handler = object : ServiceHandler {
            override suspend fun handleRequest(
                path: String,
                params: Map<String, Any?>,
                metadata: Map<String, String>
            ): Any? {
                return rpcHandler.handleP2pRequest(path, params)
            }
            override fun getImplementationJar(): ByteArray = clientJarBytes
            override fun getImplementationClassName(): String = implClassName
            override fun getStdlibJar(): ByteArray? = stdlibJarBytes
            override fun supportsSandboxedExecution(): Boolean = true
            override fun onShutdown() {}
        }

        val registration = serverResolver.registerGlobalService(
            serviceUrl = "url://markdown/",
            handler = handler,
            config = ServiceRegistrationConfig(
                metadata = mapOf("type" to "rpc"),
                reannounceIntervalMs = 60000
            )
        )

        val serverMultiaddrs = registration.multiaddresses.map { addr ->
            addr.replace(Regex("/ip4/[0-9.]+/"), "/ip4/127.0.0.1/")
        }

        val bootstrapPeer = Libp2pPeer.remote(
            peerId = registration.peerId,
            multiaddresses = serverMultiaddrs,
            advertisedServices = listOf("markdown")
        )

        clientResolver = UrlResolver(UrlProtocol2(bootstrapPeers = listOf(bootstrapPeer)))
        val connection = clientResolver.openSandboxedConnection(
            "url://markdown/",
            community.kotlin.markdown.api.MarkdownService::class
        )
        val proxy = connection.proxy

        block(proxy)

        registration.unregister()
    } finally {
        clientResolver?.close()
        serverResolver?.close()
        tempDir.deleteRecursively()
    }
}

fun testE2eCreateAndRetrieveFileViaSjvm() {
    withSjvmClient { service ->
        val file = service.createFile("baby-sleep.md", "# Baby Sleep Tips\n\nSome content here.")
        assertNotNull(file.id, "Created file should have an id")
        assertEquals("baby-sleep.md", file.name, "Created file name should be 'baby-sleep.md'")

        val retrieved = service.getFileByName("baby-sleep.md")
        assertNotNull(retrieved, "getFileByName('baby-sleep.md') should return the file, but got null")
        assertEquals("baby-sleep.md", retrieved.name, "Retrieved file name should be 'baby-sleep.md'")
        assertEquals("# Baby Sleep Tips\n\nSome content here.", retrieved.content, "Retrieved file content should match")

        println("[E2E] Create and retrieve file via SJVM passed")
    }
}

fun testE2eMultipleFilesViaSjvm() {
    withSjvmClient { service ->
        service.createFile("notes.md", "# Notes")
        service.createFile("todo.md", "# Todo List")

        val allFiles = service.getAllFiles()
        assertEquals(2, allFiles.size, "Should have 2 files, but found ${allFiles.size}")

        val notes = service.getFileByName("notes.md")
        assertNotNull(notes, "notes.md should exist")
        assertEquals("# Notes", notes.content, "notes.md content should match")

        val todo = service.getFileByName("todo.md")
        assertNotNull(todo, "todo.md should exist")
        assertEquals("# Todo List", todo.content, "todo.md content should match")

        println("[E2E] Multiple files via SJVM passed")
    }
}

fun testE2eDeleteFileViaSjvm() {
    withSjvmClient { service ->
        val file = service.createFile("temp.md", "temporary content")
        assertEquals(1, service.getAllFiles().size, "Should have 1 file after creation")

        service.deleteFile(file)
        assertEquals(0, service.getAllFiles().size, "Should have 0 files after delete")

        val lookup = service.getFileByName("temp.md")
        assertNull(lookup, "Deleted file should not be found by name, but getFileByName returned: $lookup")

        println("[E2E] Delete file via SJVM passed")
    }
}

fun testE2eFilePropertyMutationViaSjvm() {
    withSjvmClient { service ->
        val file = service.createFile("readme.md", "# Initial Content")

        file.content = "# Updated Content"

        val retrieved = service.getFileByName("readme.md")
        assertNotNull(retrieved, "readme.md should still exist after content update")
        assertEquals("# Updated Content", retrieved.content, "Content should be updated to '# Updated Content'")

        file.name = "updated-readme.md"

        val renamed = service.getFileByName("updated-readme.md")
        assertNotNull(renamed, "File should be findable by new name 'updated-readme.md'")
        assertEquals("# Updated Content", renamed.content, "Content should remain after rename")

        val oldName = service.getFileByName("readme.md")
        assertNull(oldName, "File should no longer be findable by old name 'readme.md', but got: $oldName")

        println("[E2E] File property mutation via SJVM passed")
    }
}
